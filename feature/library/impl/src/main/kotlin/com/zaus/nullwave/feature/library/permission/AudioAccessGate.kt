package com.zaus.nullwave.feature.library.permission

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaus.nullwave.core.data.AudioPermission
import com.zaus.nullwave.core.data.AudioPermissionState
import com.zaus.nullwave.core.data.AudioPermissionStatus
import com.zaus.nullwave.core.data.audioPermissionStatus
import kotlinx.coroutines.launch

/**
 * Decides which of the permission states the library shows, and renders [granted] once it may.
 *
 * Wraps the library's content rather than being a screen itself: artboard 41 replaces everything, while
 * artboard 45 sits inside the library shell with the tabs still visible, so the caller decides how much of
 * the chrome each one keeps.
 *
 * ## No ViewModel, deliberately
 *
 * This is a few flags and one launcher. MetroX has no ViewModel support and navigation3 1.1.7 has no
 * ViewModel-store decorator, so a ViewModel here would mean choosing and hand-rolling both before anything
 * exercises them - see `LIBRARY.md` §1b. The state that must outlive composition is already in DataStore;
 * the rest is genuinely per-composition.
 *
 * ## Three inputs, and why each is read where it is
 *
 * - **`isGranted`** from [AudioPermissionState], re-read on every resume. The permission can change while
 *   the app is backgrounded and nothing notifies it.
 * - **the stored flag**, collected as state. The only thing separating "never asked" from "permanently
 *   denied", which the platform reports identically.
 * - **`shouldShowRequestPermissionRationale`**, which needs an `Activity` and therefore cannot live in
 *   `:core:data`. Read here and passed down.
 *
 * [audioPermissionStatus] combines them. Nothing in this file re-implements that logic.
 *
 * ## Two known limits
 *
 * [modifier] applies to the two permission screens, **not** to [granted] - a content slot brings its own.
 *
 * `markRequested()` is written on the composition's scope, so a configuration change in the instant
 * between tapping Grant and the write landing could lose it. The consequence is one extra prompt rather
 * than anything broken, and fixing it properly needs an application-scoped coroutine in the graph, which
 * does not exist yet. See NOTES.md.
 */
@Composable
fun AudioAccessGate(
    permissionState: AudioPermissionState,
    modifier: Modifier = Modifier,
    onChooseFolders: (() -> Unit)? = null,
    granted: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // `LocalActivity` from activity-compose, rather than unwrapping LocalContext's ContextWrapper chain
    // by hand. It is null in a @Preview, where there is no Activity - which doubles as the preview guard,
    // so previewing anything containing this gate shows the granted path instead of crashing.
    val activity = LocalActivity.current ?: run {
        granted()
        return
    }

    val scope = rememberCoroutineScope()

    // Not derived state: the permission is a system fact that changes behind the app's back, so it is
    // sampled at the moments it can have changed - first composition, resume, and a request result.
    var isGranted by remember { mutableStateOf(permissionState.isGranted) }
    var shouldShowRationale by remember { mutableStateOf(activity.shouldShowRationaleForAudio()) }

    // `initialValue = null` rather than false. Reading DataStore is asynchronous, so on the first frame
    // we genuinely do not know whether the permission has been asked for - and false would mean rendering
    // artboard 41 for a moment to someone who has permanently denied it. Null renders nothing.
    val hasBeenRequested by permissionState.hasBeenRequestedWithoutGrant
        .collectAsStateWithLifecycle(initialValue = null)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { wasGranted ->
        isGranted = wasGranted
        // Re-read after the result: a denial flips this, and it is what separates "declined once, ask
        // again" from "the dialog will never appear again".
        shouldShowRationale = activity.shouldShowRationaleForAudio()
        if (wasGranted) scope.launch { permissionState.syncWithSystem() }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isGranted = permissionState.isGranted
        shouldShowRationale = activity.shouldShowRationaleForAudio()
        // Clears the stored flag while the permission is held, so a later revoke in system Settings
        // starts from the prompt rather than claiming a permanent denial. Without this the app shows
        // artboard 45 for a permission a dialog could still obtain.
        scope.launch { permissionState.syncWithSystem() }
    }

    val status = hasBeenRequested?.let { asked ->
        audioPermissionStatus(
            isGranted = isGranted,
            hasBeenRequestedWithoutGrant = asked,
            shouldShowRationale = shouldShowRationale,
        )
    }

    when (status) {
        // Still reading the flag. Deliberately blank: a spinner for something that resolves in a frame or
        // two is more visible than nothing, and this is the one state the design has no artboard for.
        null -> Unit

        AudioPermissionStatus.Granted -> granted()

        // Both "never asked" and "declined once" land on artboard 41. The platform will still show the
        // dialog in either case, so the screen that offers it is the right one - the difference is only
        // whether the system decorates the dialog with a rationale.
        AudioPermissionStatus.NeverAsked,
        AudioPermissionStatus.Declined,
        -> AudioAccessScreen(
            onGrantAccess = {
                // Marked when the launcher fires, not when the result arrives: a user who swipes the
                // dialog away without choosing has still been asked.
                scope.launch { permissionState.markRequested() }
                launcher.launch(AudioPermission.name)
            },
            onChooseFolders = onChooseFolders,
            modifier = modifier,
        )

        // `onOpenSettings` adds a button the artboard does not have - it renders the path as caption text
        // only. Passed deliberately: this is the one state the app cannot leave on its own, and the
        // alternative is telling the user to go and find Settings themselves. Drop this argument to get
        // the screen exactly as drawn.
        AudioPermissionStatus.PermanentlyDenied -> AccessDeniedScreen(
            onOpenSettings = { context.startActivity(appSettingsIntent(context.packageName)) },
            modifier = modifier,
        )
    }
}

/**
 * `shouldShowRequestPermissionRationale` for the audio permission.
 *
 * False both before the first ask and after a permanent denial, which is exactly why it cannot be used
 * alone - see [audioPermissionStatus].
 */
private fun Activity.shouldShowRationaleForAudio(): Boolean =
    shouldShowRequestPermissionRationale(AudioPermission.name)

private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        // Settings is a different task; without this the back stack behaves oddly on return.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
