package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Real peaks only. Aggregate to visible bars by peak, once per data/size change, not each frame. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeekTrack(state: SliderState, enabled: Boolean, waveform: List<Float>, scrubbing: Boolean, glowEnabled: Boolean) {
    val colors = NullWaveTheme.colors
    Spacer(Modifier.fillMaxWidth().height(SeekTrackHeight).drawWithCache {
        val bars = minOf(waveform.size, (size.width / BarPitch.toPx()).toInt().coerceAtLeast(1))
        val peaks = FloatArray(bars) { bar ->
            val start = (bar.toLong() * waveform.size / bars).toInt()
            val end = ((bar + 1L) * waveform.size / bars).toInt()
            var peak = 0f
            for (index in start until end) peak = maxOf(peak, waveform[index])
            peak
        }
        val rtl = layoutDirection == LayoutDirection.Rtl
        val stroke = PlayheadWidth.toPx()
        val timeline = Path().apply {
            if (peaks.isEmpty()) {
                addRect(Rect(0f, size.height / 2f - stroke, size.width, size.height / 2f + stroke))
            } else {
                val pitch = size.width / peaks.size
                val width = (pitch - BarGap.toPx()).coerceAtLeast(1f).coerceAtMost(pitch)
                peaks.forEachIndexed { index, peak ->
                    val height = maxOf(stroke, size.height * peak)
                    val x = (if (rtl) size.width - (index + 1) * pitch else index * pitch) + (pitch - width) / 2f
                    addRect(Rect(x, (size.height - height) / 2f, x + width, (size.height + height) / 2f))
                }
            }
        }
        val waveRadius = WaveGlowRadius.toPx()
        val waveGlow = PlayerLineGlow(colors.secondary, waveRadius, WaveGlowAlpha)
        val playheadGlow = PlayerLineGlow(colors.primary, PlayheadGlowRadius.toPx(), PlayheadGlowAlpha)
        val scrubGlow = PlayerLineGlow(colors.primary, ScrubGlowRadius.toPx(), ScrubGlowAlpha)
        val playedTimeline = Path()
        val playedBounds = Path()
        onDrawBehind {
            val fraction = state.coercedValueAsFraction
            val boundary = size.width * if (rtl) 1f - fraction else fraction
            val active = if (enabled) colors.secondary else colors.textSecondary
            drawPath(timeline, colors.outline)
            if (enabled && glowEnabled && fraction > 0f) {
                // Cut the source geometry, then draw its halo freely past the progress boundary.
                // Clipping an already drawn halo produces a flat edge and includes unplayed bars.
                playedTimeline.reset()
                playedBounds.reset()
                playedBounds.addRect(Rect(if (rtl) boundary else 0f, 0f,
                    if (rtl) size.width else boundary, size.height))
                if (playedTimeline.op(timeline, playedBounds, PathOperation.Intersect)) {
                    waveGlow.drawPath(this, playedTimeline)
                }
            }
            clipRect(left = if (rtl) boundary else 0f, right = if (rtl) size.width else boundary) { drawPath(timeline, active) }
            // Identical centre/width in both states. Drag changes emphasis, never precision.
            val x = boundary.coerceIn(stroke / 2f, maxOf(stroke / 2f, size.width - stroke / 2f))
            val start = Offset(x, 0f)
            val end = Offset(x, size.height)
            if (enabled && glowEnabled) {
                (if (scrubbing) scrubGlow else playheadGlow).drawLine(this, start, end, stroke)
            }
            val playheadColor = if (enabled) colors.primary else colors.textSecondary
            drawLine(playheadColor, start, end, stroke)
        }
    })
}

private val SeekTrackHeight = 32.dp
private val PlayheadWidth = 2.dp
private val BarPitch = 4.dp
private val BarGap = 2.dp
private val WaveGlowRadius = 4.dp
private const val WaveGlowAlpha = 0.28f
private val PlayheadGlowRadius = 6.dp
private const val PlayheadGlowAlpha = 0.75f
private val ScrubGlowRadius = 10.dp
private const val ScrubGlowAlpha = 0.85f
