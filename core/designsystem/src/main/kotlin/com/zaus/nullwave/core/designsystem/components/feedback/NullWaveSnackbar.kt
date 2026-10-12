package com.zaus.nullwave.core.designsystem.components.feedback

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle

/**
 * Snackbar visuals only. Use [NullWaveSnackbarHost] for queueing, timeout, announcements and dismiss
 * semantics. The caller handles SnackbarResult.ActionPerformed from showSnackbar; no business
 * callback is stored here. In particular, an Undo label does not implement an undo operation.
 * Indefinite messages always expose a close button, even when withDismissAction is false.
 */
@Composable
fun NullWaveSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    variant: NullWaveMessageVariant = NullWaveMessageVariant.Neutral,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
) {
    val visuals = snackbarData.visuals
    NullWaveInlineMessage(
        message = visuals.message,
        modifier = modifier.widthIn(max = SnackbarMaxWidth),
        variant = variant,
        action = visuals.actionLabel?.let { NullWaveMessageAction(it, snackbarData::performAction) },
        onDismiss = if (visuals.withDismissAction || visuals.duration == SnackbarDuration.Indefinite) {
            snackbarData::dismiss
        } else null,
        borderStyle = borderStyle,
    )
}

private val SnackbarMaxWidth = 560.dp
