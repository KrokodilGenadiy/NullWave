package com.zaus.nullwave.feature.search.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed

/**
 * Restore an editing session once per composition, never on an ordinary resume from Settings.
 * On touch devices, dismissing IME ends automatic focus restoration. Requesting focus on the
 * String-based BasicTextField would otherwise reopen it. Hardware-keyboard focus needs no IME.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun rememberSearchInputFocus(): SearchInputFocus {
    val input = rememberSaveable(saver = SearchInputFocus.Saver) { SearchInputFocus(restoreEditing = true) }
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    val view = LocalView.current
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    val configuration = LocalConfiguration.current
    val hardwareKeyboard by rememberUpdatedState(configuration.keyboard != Configuration.KEYBOARD_NOKEYS &&
        configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO)
    val inspection = LocalInspectionMode.current
    // Insets can reach the Android window before Compose's next frame. Snapshot the real window
    // on pause so an immediate recreation after system Back cannot save the old "IME open" value.
    DisposableEffect(input, lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && !inspection) {
                val visible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) ?: imeVisible
                input.restoreEditing = input.focused && (visible || hardwareKeyboard)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Capture the saved intention before fresh, initially unfocused nodes report their state.
    val restoreEditing = remember(input) { input.restoreEditing }
    LaunchedEffect(input, lifecycle) {
        if (restoreEditing && !inspection) lifecycle.withResumed {
            input.requester.requestFocus()
            if (!hardwareKeyboard) keyboard?.show()
        }
    }
    LaunchedEffect(input, lifecycle, window) {
        if (!inspection) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { if (window.isWindowFocused) input.focused && (imeVisible || hardwareKeyboard) else null }
                .collect { editing ->
                    if (editing != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        input.restoreEditing = editing
                    }
                }
        }
    }
    return input
}

internal class SearchInputFocus(var restoreEditing: Boolean) {
    val requester = FocusRequester()
    var focused by mutableStateOf(false)

    companion object {
        val Saver = Saver<SearchInputFocus, Boolean>(
            save = { it.restoreEditing },
            restore = { SearchInputFocus(restoreEditing = it) },
        )
    }
}
