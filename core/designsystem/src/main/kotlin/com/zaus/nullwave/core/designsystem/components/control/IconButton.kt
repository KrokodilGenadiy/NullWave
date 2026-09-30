package com.zaus.nullwave.core.designsystem.components.control

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * A 24dp icon centred in a 48dp hit area - the touch-target floor the design sets.
 *
 * The clip is what makes the ripple an octagon: a ripple is a circle clipped to the node's bounds,
 * so it takes the shape you clip to. `clip` must stay BEFORE `clickable`, or the indication draws
 * outside the clip and you get a square.
 */
@Composable
fun NullWaveIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    tint: Color = NullWaveTheme.colors.textPrimary,
) {
    Box(
        modifier = modifier
            .size(NullWaveTheme.dimens.touchTarget)
            .clip(NullWaveTheme.shapes.node)
            // enabled = false drops both the click and the ripple, which is what you want for a
            // disabled action - otherwise it still lights up under a finger.
            //
            // Ripple colour follows the icon rather than the theme's global primary: a primary
            // ripple under a danger-tinted icon would contradict what the button does.
            .clickable(
                enabled = isEnabled,
                indication = ripple(color = tint),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (isEnabled) tint else NullWaveTheme.colors.textTertiary,
            modifier = Modifier.size(NullWaveTheme.dimens.icon),
        )
    }
}

@Preview(name = "Icon button", showBackground = true)
@Composable
private fun IconButtonPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Column(
            modifier = Modifier
                .background(colors.bg)
                .padding(NullWaveTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NullWaveIconButton(NullWaveIcons.Menu, "Open navigation", {})
                NullWaveIconButton(NullWaveIcons.Search, "Search", {})
                NullWaveIconButton(NullWaveIcons.Overflow, "More", {})
                NullWaveMicroLabel("default")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NullWaveIconButton(NullWaveIcons.Play, "Play", {}, tint = colors.primary)
                NullWaveIconButton(NullWaveIcons.FavouriteFilled, "Unfavourite", {}, tint = colors.primary)
                NullWaveIconButton(NullWaveIcons.Delete, "Delete", {}, tint = colors.danger)
                NullWaveMicroLabel("tinted")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NullWaveIconButton(NullWaveIcons.Previous, "Previous", {}, isEnabled = false)
                NullWaveIconButton(NullWaveIcons.Next, "Next", {}, isEnabled = false)
                NullWaveMicroLabel("disabled — no click, no ripple")
            }
        }
    }
}
