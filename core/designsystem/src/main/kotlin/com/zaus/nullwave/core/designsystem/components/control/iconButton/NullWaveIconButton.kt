package com.zaus.nullwave.core.designsystem.components.control.iconButton

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Single action: a 24 dp icon in an octagonal target of at least 48 x 48 dp.
 * [contentDescription] must be a non-blank, localized action name supplied by the caller.
 * Parent constraints must allow the minimum target; use [modifier] to enlarge, not shrink it.
 * [tint] should contrast with the surrounding surface; theme text/accent colours are suitable.
 * A supplied [interactionSource] remains owned by the caller. This is not a toggle control.
 */
@Composable
fun NullWaveIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = NullWaveTheme.colors.textPrimary,
    interactionSource: MutableInteractionSource? = null,
) {
    require(contentDescription.isNotBlank()) { "An icon button needs a non-blank action description." }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val shape = NullWaveTheme.shapes.node
    val pressedHighlighted = enabled && pressed
    val focusedHighlighted = enabled && focused
    val contentColor = if (pressedHighlighted) colors.bg else tint

    IconButton(
        // Foundation 1.12.1 registers semantics OnClick even when disabled; a direct invocation
        // calls performClick() without checking enabled. Normal pointer/key input is gated.
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .defaultMinSize(minWidth = dimens.touchTarget, minHeight = dimens.touchTarget)
            .then(
                // Border draws inside the shape: on press, bg contrasts with the tint fill.
                if (focusedHighlighted) Modifier.border(dimens.focusBorder, contentColor, shape)
                else Modifier,
            )
            // Augment semantics; clearing here would also hide the internal clickable's semantics.
            .semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (pressedHighlighted) tint else Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            // Disabled intentionally removes accent/danger tint, independent of the caller's tint.
            disabledContentColor = colors.textSecondary,
        ),
        interactionSource = interactions,
        shape = shape,
    ) {
        NullWaveIcon(icon = icon, contentDescription = null)
    }
}
