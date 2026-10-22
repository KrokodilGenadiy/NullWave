package com.zaus.nullwave.feature.library.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.overlay.NullWaveDialog
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.platform.AudioPermission
import com.zaus.nullwave.feature.library.platform.libraryAccess
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessUi

private enum class ExternalRequest { Permission, Settings }

/** Entry-owned UI protocol. Save only dialog/launch history; always ask Android for the grant. */
@Composable
internal fun LibraryPermissionHost(
    permission: AudioPermission,
    onAccessChecked: (LibraryAccess) -> Unit,
    content: @Composable (LibraryAccessUi) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var pending by rememberSaveable { mutableStateOf<ExternalRequest?>(null) }
    var deniedResult by rememberSaveable { mutableStateOf(false) }
    var rationaleVisible by rememberSaveable { mutableStateOf(false) }
    var launchFailed by rememberSaveable { mutableStateOf(false) }
    // Only suppress duplicate signals in this live host; never restore an OS grant from a Bundle.
    var lastReported by remember { mutableStateOf<LibraryAccess?>(null) }
    val readAccess by rememberUpdatedState {
        val granted = permission.isGranted()
        if (granted) {
            deniedResult = false
            rationaleVisible = false
            launchFailed = false
        }
        libraryAccess(granted, !granted && permission.shouldExplain(), deniedResult)
    }
    val publishAccess by rememberUpdatedState { access: LibraryAccess, force: Boolean ->
        // A result can arrive while stopped, or synchronously during registry registration.
        // ON_RESUME reads Android again before the screen becomes interactive.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && (force || lastReported != access)) {
            lastReported = access
            onAccessChecked(access)
        }
    }

    // Register this observer before launchers: if already resumed, a buffered registry result
    // must reconcile against this fresh snapshot instead of causing two initial refreshes.
    DisposableEffect(lifecycle, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) publishAccess(readAccess(), true)
        }
        // Lifecycle brings a new observer up to its current state, including ON_RESUME.
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Unconditional registration keeps ActivityResult registry keys stable across recreation.
    val permissionLauncher = rememberLauncherForActivityResult(remember { ActivityResultContracts.RequestPermission() }) {
        pending = null
        deniedResult = !permission.isGranted()
        publishAccess(readAccess(), false)
    }
    val settingsLauncher = rememberLauncherForActivityResult(remember { ActivityResultContracts.StartActivityForResult() }) {
        pending = null
        publishAccess(readAccess(), false) // Settings resultCode does not describe permission status.
    }

    fun launch(request: ExternalRequest) {
        if (pending != null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        launchFailed = false
        pending = request // Set before launch: a registry may deliver a synchronous result.
        try {
            when (request) {
                ExternalRequest.Permission -> permissionLauncher.launch(permission.name)
                ExternalRequest.Settings -> settingsLauncher.launch(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ))
            }
        } catch (_: ActivityNotFoundException) {
            pending = null
            launchFailed = true
        } catch (_: SecurityException) {
            pending = null
            launchFailed = true
        }
    }

    fun accessAction(askAgain: Boolean) {
        if (pending != null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val access = readAccess()
        // An explicit check must also recover a repository denial after an earlier OS grant.
        publishAccess(access, access == LibraryAccess.Granted)
        when (access) {
            LibraryAccess.Granted -> Unit
            is LibraryAccess.Required -> when {
                access.action == AccessAction.ExplainThenRequest -> rationaleVisible = true
                access.action == AccessAction.SettingsSuggested && !askAgain -> launch(ExternalRequest.Settings)
                else -> launch(ExternalRequest.Permission)
            }
        }
    }

    content(LibraryAccessUi(
        access = lastReported,
        busy = pending != null,
        launchFailed = launchFailed,
        onAccess = { accessAction(askAgain = false) },
        onAskAgain = { accessAction(askAgain = true) },
    ))
    if (rationaleVisible) {
        NullWaveDialog(
            title = stringResource(R.string.library_access_title),
            onDismissRequest = { rationaleVisible = false },
            actions = {
                NullWaveButton(stringResource(R.string.library_continue), onClick = {
                    rationaleVisible = false
                    // Permission may have changed while this explanation was open.
                    val access = readAccess()
                    publishAccess(access, access == LibraryAccess.Granted)
                    if (access != LibraryAccess.Granted) launch(ExternalRequest.Permission)
                })
                NullWaveButton(stringResource(R.string.library_cancel), onClick = { rationaleVisible = false },
                    variant = NullWaveButtonVariant.Neutral)
            },
        ) {
            Text(stringResource(R.string.library_access_rationale), style = NullWaveTheme.typography.body)
        }
    }
}
