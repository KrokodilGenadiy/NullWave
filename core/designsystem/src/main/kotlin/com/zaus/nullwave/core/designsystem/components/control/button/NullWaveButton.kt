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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

enum class NullWaveButtonVariant { Primary, Secondary, Neutral, Danger }

/**
 * Labelled action with a decorative leading icon. Preserves label casing and allows wrapping.
 * [loading] blocks clicks and keeps the original content measured, so its size stays unchanged
 * provided [label], [leadingIcon] and parent constraints stay the same.
 * The caller owns the operation and must set loading when it starts; this is not a debounce.
 * A supplied [interactionSource] remains owned by the caller; this button only observes it.
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

    Button(
        // Foundation 1.12.1's direct semantics OnClick action does not check enabled.
        onClick = { if (interactive) onClick() },
        modifier = modifier
            .defaultMinSize(
                minWidth = NullWaveTheme.dimens.touchTarget,
                minHeight = NullWaveTheme.dimens.touchTarget,
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
        border = BorderStroke(
            width = if (focusedHighlighted) NullWaveTheme.dimens.focusBorder else NullWaveTheme.dimens.hairline,
            color = borderColor,
        ),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
        interactionSource = interactions,
    ) {
        Box(contentAlignment = Alignment.Center) {
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
                    style = NullWaveTheme.typography.button,
                )
            }
            if (loading) {
                // Three centred square blocks replace the label: intentionally static, not text
                // punctuation or a separate progress control. The button retains its role/name.
                // matchParentSize prevents this overlay from changing the button's measurement.
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
}

// Button-specific geometry; shared theme dimensions stay independent of this busy mark.
private val LoadingDotSize = 4.dp
private const val LoadingDotCount = 3
