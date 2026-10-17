package com.zaus.nullwave.core.designsystem.preview

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset

/** Static visual comparison only; use [rememberPreviewFocus] to check actual keyboard focus. */
@Composable
internal fun PreviewInteractionSample(
    pressed: Boolean = false,
    focused: Boolean = false,
    content: @Composable (MutableInteractionSource) -> Unit,
) {
    // Reset collectors with the source when the sample changes; otherwise a previously collected
    // focus/press can remain true after the matching flag becomes false.
    key(pressed, focused) {
        val source = remember { MutableInteractionSource() }
        content(source)
        if (pressed || focused) {
            // Compose the consumers first, so their collectors exist before emitting the sample.
            LaunchedEffect(source) {
                if (focused) source.emit(FocusInteraction.Focus())
                if (pressed) source.emit(PressInteraction.Press(Offset.Zero))
            }
        }
    }
}
