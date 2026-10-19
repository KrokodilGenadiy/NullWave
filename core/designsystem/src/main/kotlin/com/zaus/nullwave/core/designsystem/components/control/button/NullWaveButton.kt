package com.zaus.nullwave.core.designsystem.components.control.button

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.modifier.nullWaveBorder
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

enum class NullWaveButtonVariant { Primary, Secondary, Neutral, Danger }

/** Compact has a 28 dp minimum body; Material still reserves a minimum 48 dp interactive target. */
enum class NullWaveButtonSize { Regular, Compact }

/**
 * Labelled action with a decorative leading icon. Preserves label casing and allows wrapping.
 * [loading] blocks clicks and keeps the original content measured, so its size stays unchanged
 * provided [label], [leadingIcon] and parent constraints stay the same.
 * The caller owns the operation and must set loading when it starts; this is not a debounce.
 * A supplied [interactionSource] remains owned by the caller; this button only observes it.
 * [borderStyle] controls the decorative outline; keyboard focus always uses a closed border.
 */
@Composable
fun NullWaveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NullWaveButtonVariant = NullWaveButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes leadingIcon: Int? = null,
    interactionSource: MutableInteractionSource? = null,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
    size: NullWaveButtonSize = NullWaveButtonSize.Regular,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val colors = NullWaveTheme.colors
    val spacing = NullWaveTheme.spacing
    val interactive = enabled && !loading
    val pressedHighlighted = interactive && pressed
    val focusedHighlighted = interactive && focused
    val accent = when (variant) {
        NullWaveButtonVariant.Primary -> colors.primary
        NullWaveButtonVariant.Secondary -> colors.secondary
        NullWaveButtonVariant.Neutral -> colors.textPrimary
        NullWaveButtonVariant.Danger -> colors.danger
    }
    val filled = when (variant) {
        NullWaveButtonVariant.Primary -> !pressedHighlighted
        else -> pressedHighlighted
    }
    val containerColor = when {
        !enabled -> colors.surfaceRaised
        filled -> accent
        else -> colors.surface
    }
    val contentColor = when {
        !enabled -> colors.textSecondary
        filled -> colors.bg
        else -> accent
    }
    val borderColor = when {
        !enabled -> colors.outline
        // Contrasts with the fill as well as with outlined variants; focus never changes fill.
        focusedHighlighted -> contentColor
        variant == NullWaveButtonVariant.Neutral && !pressedHighlighted -> colors.outline
        else -> accent
    }
    val loadingDescription = if (loading) stringResource(R.string.nw_button_loading) else null
    val border = BorderStroke(
        width = if (focusedHighlighted) NullWaveTheme.dimens.focusBorder else NullWaveTheme.dimens.hairline,
        color = borderColor,
    )
    val clippedBorder = borderStyle == NullWaveBorderStyle.ClippedCorners && !focusedHighlighted

    if (size == NullWaveButtonSize.Compact) {
        // Clickable Surface reserves the touch target outside the compact visual body.
        // Draw the clipped border on the content so it follows the body, not that reservation.
        Surface(
            onClick = { if (interactive) onClick() },
            modifier = modifier.defaultMinSize(minWidth = NullWaveTheme.dimens.touchTarget).semantics {
                role = Role.Button
                if (loadingDescription != null) {
                    contentDescription = label
                    stateDescription = loadingDescription
                }
            },
            enabled = interactive,
            shape = NullWaveTheme.shapes.small,
            color = if (enabled && !filled) Color.Transparent else containerColor,
            contentColor = contentColor,
            border = if (clippedBorder) null else border,
            interactionSource = interactions,
        ) {
            ButtonContent(
                label, leadingIcon, loading,
                style = NullWaveTheme.typography.mono.copy(letterSpacing = 0.12.em),
                modifier = Modifier.defaultMinSize(minHeight = CompactButtonBodyHeight)
                    .then(if (clippedBorder) Modifier.nullWaveBorder(border, NullWaveTheme.shapes.small, borderStyle) else Modifier)
                    .padding(horizontal = spacing.xs, vertical = spacing.xxs),
            )
        }
        return
    }

    Button(
        // Foundation 1.12.1's direct semantics OnClick action does not check enabled.
        onClick = { if (interactive) onClick() },
        modifier = modifier
            .defaultMinSize(
                minWidth = NullWaveTheme.dimens.touchTarget,
                minHeight = NullWaveTheme.dimens.touchTarget,
            )
            .then(
                if (clippedBorder) Modifier.nullWaveBorder(border, NullWaveTheme.shapes.small, borderStyle)
                else Modifier,
            )
            .semantics {
                if (loadingDescription != null) {
                    contentDescription = label
                    stateDescription = loadingDescription
                }
            },
        enabled = interactive,
        shape = NullWaveTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            // Loading is non-interactive but retains its variant colours.
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        elevation = null,
        border = if (clippedBorder) null else border,
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
        interactionSource = interactions,
    ) {
        ButtonContent(label, leadingIcon, loading, style = NullWaveTheme.typography.button)
    }
}

@Composable
private fun ButtonContent(
    label: String,
    @DrawableRes leadingIcon: Int?,
    loading: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val spacing = NullWaveTheme.spacing
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(
            modifier = if (loading) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                NullWaveIcon(icon = leadingIcon, contentDescription = null)
            }
            Text(
                text = label,
                // Wrap within the space left by the icon without stretching short labels.
                modifier = Modifier.weight(1f, fill = false),
                style = style,
            )
        }
        if (loading) {
            // Static square blocks replace the label, not punctuation or another progress role.
            // matchParentSize keeps the original label's measurement during loading.
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(LoadingDotCount) {
                    Box(Modifier.size(LoadingDotSize).background(LocalContentColor.current))
                }
            }
        }
    }
}

// Button-specific geometry; shared theme dimensions stay independent of this busy mark.
private val LoadingDotSize = 4.dp
private val CompactButtonBodyHeight = 28.dp
private const val LoadingDotCount = 3
