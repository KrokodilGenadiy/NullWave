package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Direction of the small delta arrow beside a [NullWaveStatBar], or none. */
enum class StatDelta { None, Up, Down }

/**
 * A thin segmented meter with a label, a value and an optional delta arrow - the weapon-comparison
 * card from the reference material.
 *
 * Reused by the track-detail sheet (bitrate, sample rate, codec, channels) and the equalizer's
 * readouts, so it takes an already-formatted [value] string rather than a number: `320 kbps` and
 * `48 kHz` and `FLAC` are all legitimate, and formatting is not this component's problem.
 *
 * Segments rather than a continuous bar. A solid fill would read as a progress indicator, which is
 * what the seek bar is - this is a measurement.
 */
@Composable
fun NullWaveStatBar(
    label: String,
    value: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    segments: Int = DefaultSegments,
    barColor: Color = NullWaveTheme.colors.primary,
    delta: StatDelta = StatDelta.None,
) {
    val colors = NullWaveTheme.colors
    // A caller passing 0 would divide by zero in the draw block below and paint nothing but NaNs.
    val segmentCount = segments.coerceAtLeast(1)
    val filled = (fraction.coerceIn(0f, 1f) * segmentCount).toInt()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NullWaveMicroLabel(label)
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    style = NullWaveTheme.typography.mono,
                    color = colors.textPrimary,
                )
                when (delta) {
                    StatDelta.None -> Unit
                    StatDelta.Up -> DeltaArrow(NullWaveIcons.Expand, colors.success)
                    StatDelta.Down -> DeltaArrow(NullWaveIcons.Collapse, colors.danger)
                }
            }
        }

        // A Spacer, not an empty Row: the meter has no children, and a layout that exists only to be
        // drawn into should say so.
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .drawBehind {
                    val gap = SegmentGap.toPx()
                    val segmentWidth = (size.width - gap * (segmentCount - 1)) / segmentCount
                    repeat(segmentCount) { index ->
                        drawRect(
                            color = if (index < filled) barColor else colors.outline,
                            topLeft = Offset(index * (segmentWidth + gap), 0f),
                            size = Size(segmentWidth, size.height),
                        )
                    }
                },
        )
    }
}

@Composable
private fun DeltaArrow(icon: Int, tint: Color) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(DeltaSize),
    )
}

private const val DefaultSegments = 12
private val BarHeight = 4.dp
private val SegmentGap = 2.dp
private val DeltaSize = 12.dp

@Preview(name = "Stat bar", showBackground = true)
@Composable
private fun StatBarPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Column(
            modifier = Modifier
                .background(colors.bg)
                .padding(NullWaveTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
        ) {
            NullWaveStatBar("Bitrate", "320 kbps", fraction = 0.8f, delta = StatDelta.Up)
            NullWaveStatBar("Sample rate", "48 kHz", fraction = 0.6f)
            NullWaveStatBar("Codec", "FLAC", fraction = 1f, barColor = colors.secondary)
            NullWaveStatBar("Channels", "2.0", fraction = 0.25f, delta = StatDelta.Down)
        }
    }
}
