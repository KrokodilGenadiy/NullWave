package com.zaus.nullwave.core.designsystem.components.feedback

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Material owns the queue, accessible timeout, polite announcement, dismiss action and transitions.
 * Remember one [hostState] per host at screen/app level; call showSnackbar from a lifecycle-owned
 * coroutine. Cancelling it removes that request from the display/queue. Do not enqueue in composition.
 * The caller positions the host (e.g. Scaffold.snackbarHost) and handles system/IME/bottom-bar insets.
 * This wrapper adds only the theme gutter; it neither owns a Scaffold nor duplicates inset padding.
 * [snackbar] can map caller-defined SnackbarVisuals to a message variant without a second queue type.
 */
@Composable
fun NullWaveSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    snackbar: @Composable (SnackbarData) -> Unit = { NullWaveSnackbar(it) },
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(NullWaveTheme.dimens.gutter),
        snackbar = snackbar,
    )
}
