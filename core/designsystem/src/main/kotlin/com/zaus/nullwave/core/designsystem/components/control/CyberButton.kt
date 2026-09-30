package com.zaus.nullwave.core.designsystem.components.control

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.chamferBorder
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

/**
 * Visual treatments for [NullWaveCyberButton].
 *
 * One enum rather than three composables: the geometry, the states and the label handling are
 * identical, only the colours differ.
 */
enum class CyberButtonType {
    /** Filled `primary`. The screen's one main action - PLAY ALL, CONFIRM. */
    Primary,

    /** Outlined `secondary`. The accented secondary action - SHUFFLE. */
    Secondary,

    /**
     * Outlined in `outline` with a `textSecondary` label. The neutral action - CANCEL.
     *
     * Not a ghost. The design's component sheet has no borderless button; its third treatment is
     * this grey-outlined one, which sits back without disappearing.
     */
    Tertiary,

    /**
     * Outlined `danger`. Destructive - DELETE from the selection bar.
     *
     * Outlined rather than filled because the design reserves `danger` for framing and destructive
     * actions, and a full red field is very loud on a near-black canvas. Switch to filled if a final
     * confirm ever needs more weight. `colors.danger` shifts to yellow in the Arasaka variant, where
     * red is the brand colour, so this stays distinguishable there.
     */
    Danger,
}

/**
 * The system's button. Chamfered, flat, uppercase.
 *
 * Not Material's `Button`: that brings elevation, a rounded shape and its own content padding - all
 * three of which this design forbids or overrides.
 *
 * Uppercases [text] itself. Every button label in this system is uppercase, so leaving it to the
 * call site is a way to eventually get it wrong.
 */
@Composable
fun NullWaveCyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: CyberButtonType = CyberButtonType.Primary,
    isEnabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    val colors = NullWaveTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val isFilled = type == CyberButtonType.Primary

    // The press "snap": a held filled button inverts to a dark field with a primary border and a
    // primary label, then flips straight back. No animation - the hard cut is the point, and it
    // reads like a key registering rather than a surface being tinted. Swapping primary for
    // primaryDim, which is what this did before, is two yellows and barely visible.
    //
    // Nothing about it changes layout: padding is the same for every variant, and Compose's border
    // draws inside the node's bounds, so the invert cannot make the button move.
    val isInverted = isFilled && isEnabled && isPressed

    // Border colour and label colour are the same value for every outlined treatment except the
    // neutral one, which is deliberately mismatched: a grey border with a lighter grey label.
    val borderColor = when {
        !isEnabled -> colors.outline
        isInverted -> colors.primary
        type == CyberButtonType.Secondary -> colors.secondary
        type == CyberButtonType.Tertiary -> colors.outline
        type == CyberButtonType.Danger -> colors.danger
        else -> colors.outline
    }

    val background = when {
        isInverted -> colors.bg
        !isFilled || !isEnabled -> Color.Transparent
        else -> colors.primary
    }
    val contentColor = when {
        !isEnabled -> colors.textTertiary
        isInverted -> colors.primary
        isFilled -> colors.onPrimary
        type == CyberButtonType.Secondary -> colors.secondary
        type == CyberButtonType.Tertiary -> colors.textSecondary
        type == CyberButtonType.Danger -> colors.danger
        else -> colors.textSecondary
    }

    // Bordered whenever there is no fill to define the edge - and while inverted, where the border
    // is what keeps the button's footprint visible against the canvas.
    val showBorder = !isFilled || !isEnabled || isInverted

    // The ripple is chamfer-shaped for free, because the node is clipped to ButtonShape above.
    // Its colour has to come from the CONTENT, not the accent: the theme's global ripple is
    // primary, which on a yellow fill is invisible - which is why this felt dead with it enabled.
    val rippleColor = if (isFilled && !isInverted) colors.onPrimary else contentColor

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = NullWaveTheme.dimens.touchTarget)
            .clip(ButtonShape)
            .background(background)
            .then(if (showBorder) Modifier.chamferBorder(borderColor, ButtonShape) else Modifier)
            .clickable(
                enabled = isEnabled,
                interactionSource = interactionSource,
                // Two signals doing different jobs: the ripple tracks WHERE the finger is, the
                // invert says the press REGISTERED.
                indication = ripple(color = rippleColor),
                onClick = onClick,
            )
            // 12/22 for every variant. The design's token sheet shows 11/21 for the outlined ones,
            // but that compensates for CSS adding a border outside the padding box. Compose's
            // border draws INSIDE the node's bounds and adds no size, so the same padding already
            // gives filled and outlined buttons identical heights.
            .padding(ButtonPadding),
        horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(NullWaveTheme.dimens.icon),
            )
        }
        Text(
            text = text.uppercase(),
            // 700 filled, 600 outlined - straight from the component sheet. The filled button is
            // the only one carrying full display weight.
            style = NullWaveTheme.typography.button.copy(
                fontWeight = if (isFilled) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = contentColor,
        )
    }
}

private val ButtonShape = chamfer(10.dp)
private val ButtonPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)

@Preview(name = "Cyber button", showBackground = true)
@Composable
private fun CyberButtonPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Column(
            modifier = Modifier
                .background(colors.bg)
                .padding(NullWaveTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
        ) {
            NullWaveMicroLabel("enabled")
            Row(horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm)) {
                NullWaveCyberButton("Play all", {}, leadingIcon = NullWaveIcons.Play)
                NullWaveCyberButton("Shuffle", {}, type = CyberButtonType.Secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm)) {
                NullWaveCyberButton("Cancel", {}, type = CyberButtonType.Tertiary)
                NullWaveCyberButton("Delete", {}, type = CyberButtonType.Danger)
            }
            NullWaveMicroLabel("disabled")
            Row(horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm)) {
                NullWaveCyberButton("Play all", {}, isEnabled = false)
                NullWaveCyberButton("Shuffle", {}, type = CyberButtonType.Secondary, isEnabled = false)
            }
        }
    }
}
