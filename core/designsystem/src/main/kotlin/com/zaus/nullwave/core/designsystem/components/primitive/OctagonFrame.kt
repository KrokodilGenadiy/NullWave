package com.zaus.nullwave.core.designsystem.components.primitive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.modifier.activeGlow
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.OctagonShape

/**
 * The perk-tree node as a container: a flattened octagon with a hairline border and an optional
 * activation glow.
 *
 * This system has no circles - wherever another design would use one, this uses the octagon. So the
 * same component serves the drawer avatar, artist grid cards, every slider thumb and every EQ band
 * handle. It scales, so a 28dp handle and a 96dp avatar are recognisably the same shape.
 *
 * ## Modifier order, which is load-bearing here
 *
 * `activeGlow` comes **before** `clip`, so the halo is free to draw outside the node while the
 * content is still clipped to the octagon. Swap them and the glow disappears - a clip cuts off
 * everything drawn after it, and the glow lives outside `size` by definition.
 *
 * @param isActive draws the activation glow. **One element at a time** - the touched EQ band, the
 *   selected node. Not every node in a row.
 * @param borderColor the design's convention: `outline` when idle, `primary` when active or set.
 *   An EQ handle at exactly 0.0 dB is outline-only so a flat EQ visibly reads as flat.
 */
@Composable
fun NullWaveOctagonFrame(
    modifier: Modifier = Modifier,
    size: Dp = NullWaveTheme.dimens.sliderNode,
    backgroundColor: Color = NullWaveTheme.colors.surface,
    borderColor: Color = NullWaveTheme.colors.outline,
    borderWidth: Dp = 1.5.dp,
    isActive: Boolean = false,
    glowColor: Color = borderColor,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(size)
            // Before clip: the glow has to escape the node's bounds.
            .then(
                if (isActive) {
                    Modifier.activeGlow(
                        color = glowColor,
                        shape = OctagonShape,
                        radius = NullWaveTheme.dimens.glowRadius,
                    )
                } else {
                    Modifier
                }
            )
            .clip(OctagonShape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, OctagonShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Preview(name = "Octagon frame", showBackground = true)
@Composable
private fun OctagonFramePreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        val dimens = NullWaveTheme.dimens

        Column(
            modifier = Modifier
                .background(colors.bg)
                // Room for the glow, which draws outside its node.
                .padding(NullWaveTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.lg),
        ) {
            // The EQ band handle, in its three states.
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // At 0.0 dB: outline only, so a flat EQ reads as flat.
                NullWaveOctagonFrame()
                // Set, but not the band being touched.
                NullWaveOctagonFrame(borderColor = colors.primary)
                // The one band under a finger.
                NullWaveOctagonFrame(
                    borderColor = colors.primary,
                    isActive = true,
                    glowColor = colors.primary,
                )
                NullWaveMicroLabel("0 dB / set / touched")
            }

            // Drawer avatar: bigger, raised fill, initials inside.
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NullWaveOctagonFrame(
                    size = 40.dp,
                    backgroundColor = colors.surfaceRaised,
                ) {
                    NullWaveMicroLabel("NW", color = colors.primary)
                }
                NullWaveOctagonFrame(
                    size = 64.dp,
                    backgroundColor = colors.surfaceRaised,
                    borderColor = colors.primary,
                ) {
                    NullWaveMicroLabel("VR", color = colors.primary)
                }
                NullWaveMicroLabel("avatar 40 / artist card 64")
            }

            // Scales without redrawing: same silhouette at slider-thumb and hero sizes.
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
                verticalAlignment = Alignment.Bottom,
            ) {
                NullWaveOctagonFrame(size = dimens.sliderNode, borderColor = colors.secondary)
                NullWaveOctagonFrame(size = 48.dp, borderColor = colors.secondary)
                NullWaveOctagonFrame(size = 96.dp, borderColor = colors.secondary)
            }
        }
    }
}
