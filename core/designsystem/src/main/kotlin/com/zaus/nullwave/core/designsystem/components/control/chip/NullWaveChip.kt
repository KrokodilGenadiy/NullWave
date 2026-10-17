package com.zaus.nullwave.core.designsystem.components.control.chip

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.modifier.nullWaveBorder
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * A selectable filter/preset, not an action button. The caller owns [selected] and decides
 * whether [onClick] selects one option, toggles several, or rejects a change.
 * Material supplies the selected/disabled semantics and Checkbox role; [label] names the chip.
 * Labels preserve casing and wrap. The optional leading icon is decorative and stays in place
 * across selection changes, so the component does not jump when selected.
 *
 * The compact body grows with its label. Material reserves a 48 dp minimum interactive layout
 * under the default LocalMinimumInteractiveComponentSize; parents must allow that space.
 * Do not constrain the row height to the visual body's 32 dp minimum or disable that reservation.
 * A supplied [interactionSource] remains owned by the caller.
 * [borderStyle] controls the decorative outline; keyboard focus always uses a closed border.
 */
@Composable
fun NullWaveChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
    interactionSource: MutableInteractionSource? = null,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
) {
    require(label.isNotBlank()) { "A chip needs a non-blank label." }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val spacing = NullWaveTheme.spacing
    val shape = NullWaveTheme.shapes.small
    val focusedHighlighted = enabled && focused
    val borderColor = when {
        !enabled && selected -> colors.textSecondary
        !enabled -> colors.outline
        focusedHighlighted && selected -> colors.bg
        focusedHighlighted -> colors.textPrimary
        selected -> colors.primary
        else -> colors.outline
    }

    val border = BorderStroke(
        width = if (focusedHighlighted) dimens.focusBorder else dimens.hairline,
        color = borderColor,
    )
    val clippedBorder = borderStyle == NullWaveBorderStyle.ClippedCorners && !focusedHighlighted
    val containerColor = when {
        selected && enabled -> colors.primary
        selected -> colors.surfaceRaised
        else -> Color.Transparent
    }
    val contentColor = if (selected && enabled) colors.bg else colors.textSecondary

    // The same selectable Surface/Checkbox role as Material FilterChip. Owning the content row
    // lets the clipped border follow the actual 32 dp body, not the outer 48 dp touch reservation.
    Surface(
        selected = selected,
        // As with the buttons, guard direct semantics actions as well as normal input.
        onClick = { if (enabled) onClick() },
        modifier = modifier.defaultMinSize(minWidth = dimens.touchTarget).semantics { role = Role.Checkbox },
        enabled = enabled,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = if (clippedBorder) null else border,
        interactionSource = interactions,
    ) {
        Row(
            modifier = Modifier.defaultMinSize(minHeight = FilterChipDefaults.Height)
                .then(if (clippedBorder) Modifier.nullWaveBorder(border, shape, borderStyle) else Modifier)
                .padding(horizontal = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                NullWaveIcon(
                    icon = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
            Text(
                text = label,
                // Last in the Row: use the remaining bounded width, or natural width in a
                // horizontal scroller. Weight would restrict an unbounded row to its minimum.
                modifier = Modifier.padding(spacing.xs),
                style = NullWaveTheme.typography.mono,
            )
        }
    }
}
