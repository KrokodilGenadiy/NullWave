package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.modifier.chamferBorder
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveOctagonFrame
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

/**
 * An album or artist tile for the 2-column grids.
 *
 * Albums are chamfered squares; **artists use [NullWaveOctagonFrame]**, which is why the artwork is
 * a slot rather than a parameter - the two shapes are genuinely different and a boolean would only
 * hide that.
 *
 * Selection is a border rather than a fill: these tiles are mostly artwork, and tinting them would
 * fight whatever colour the cover happens to be.
 *
 * ## The one component whose ripple is not chamfered
 *
 * Everywhere else the system puts `clip(shape)` before `clickable` so the ripple takes the chamfer.
 * Not here. `CardShape` cuts the top-start and **bottom-end** corners, and the bottom-end corner is
 * exactly where the metadata line ends - clipping the column would slice the tail off a long album
 * name to fix a ripple that is only visible while a finger is down. The 8dp of padding is less than
 * the 12dp cut, so padding cannot buy its way out either.
 *
 * So the ripple stays rectangular and the chamfer lives on the border and the artwork. If it reads
 * badly on a device, the fix is a clipped inner wrapper for the artwork plus a separate unclipped
 * text block - not a clip on the whole card. See NOTES.md.
 */
@Composable
fun NullWaveGridCard(
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    artwork: @Composable () -> Unit,
) {
    val colors = NullWaveTheme.colors

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.chamferBorder(colors.primary, CardShape) else Modifier
            )
            .padding(NullWaveTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            artwork()
        }
        Text(
            text = title,
            style = NullWaveTheme.typography.bodyEmphasis,
            color = if (isSelected) colors.primary else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = meta,
            style = NullWaveTheme.typography.caption,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The loading state. Same geometry as a real card, so the grid does not reflow when content lands.
 *
 * Deliberately static - no shimmer. A shimmer sweep across a grid of these would be a second
 * animated thing competing with the design's one signature transition.
 */
@Composable
fun NullWaveGridCardSkeleton(modifier: Modifier = Modifier) {
    val colors = NullWaveTheme.colors
    Column(
        modifier = modifier.padding(NullWaveTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(colors.surface, CardShape),
        )
        Box(modifier = Modifier.fillMaxWidth(SkeletonTitleFraction).background(colors.surface)) {
            Text(" ", style = NullWaveTheme.typography.bodyEmphasis)
        }
        Box(modifier = Modifier.fillMaxWidth(SkeletonMetaFraction).background(colors.surface)) {
            Text(" ", style = NullWaveTheme.typography.caption)
        }
    }
}

private val CardShape = chamfer(12.dp)
private const val SkeletonTitleFraction = 0.7f
private const val SkeletonMetaFraction = 0.4f

@Preview(name = "Grid card", showBackground = true)
@Composable
private fun GridCardPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Row(
            modifier = Modifier
                .background(colors.bg)
                .padding(NullWaveTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
        ) {
            // Album: chamfered square.
            NullWaveGridCard(
                title = "Null Wave",
                meta = "12 tracks",
                onClick = {},
                modifier = Modifier.width(140.dp),
                artwork = { NullWaveCoverArt(coverArtSource("Null Wave"), size = 124.dp) },
            )
            // Artist: octagon.
            NullWaveGridCard(
                title = "Static Pilgrim",
                meta = "48 tracks",
                onClick = {},
                isSelected = true,
                modifier = Modifier.width(140.dp),
                artwork = {
                    NullWaveOctagonFrame(
                        size = 124.dp,
                        backgroundColor = colors.surfaceRaised,
                        borderColor = colors.primary,
                    ) {
                        NullWaveMicroLabel("SP", color = colors.primary)
                    }
                },
            )
            NullWaveGridCardSkeleton(modifier = Modifier.width(140.dp))
        }
    }
}
