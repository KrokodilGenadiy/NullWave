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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.components.control.slider.SliderNodeSize

/** Real peaks only. Aggregate to visible bars by peak, once per data/size change, not each frame. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeekTrack(state: SliderState, enabled: Boolean, waveform: List<Float>, scrubbing: Boolean) {
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
        onDrawBehind {
            val fraction = state.coercedValueAsFraction
            val boundary = size.width * if (rtl) 1f - fraction else fraction
            val active = if (enabled) colors.secondary else colors.textSecondary
            fun drawTimeline(color: androidx.compose.ui.graphics.Color) {
                if (peaks.isEmpty()) drawRect(color, Offset(0f, center.y - stroke), Size(size.width, stroke * 2f))
                else {
                    val pitch = size.width / peaks.size
                    val width = (pitch - BarGap.toPx()).coerceAtLeast(1f).coerceAtMost(pitch)
                    peaks.forEachIndexed { index, peak ->
                        val height = maxOf(stroke, size.height * peak)
                        val x = if (rtl) size.width - (index + 1) * pitch else index * pitch
                        drawRect(color, Offset(x + (pitch - width) / 2f, (size.height - height) / 2f), Size(width, height))
                    }
                }
            }
            fun drawProgress() {
                drawTimeline(colors.outline)
                clipRect(left = if (rtl) boundary else 0f, right = if (rtl) size.width else boundary) { drawTimeline(active) }
            }
            if (scrubbing) {
                val halfNode = SliderNodeSize.toPx() / 2f
                clipRect(right = (boundary - halfNode).coerceAtLeast(0f)) { drawProgress() }
                clipRect(left = (boundary + halfNode).coerceAtMost(size.width)) { drawProgress() }
            } else {
                drawProgress()
                // Keep the full 2 dp playhead inside the track at both endpoints.
                val x = boundary.coerceIn(stroke / 2f, maxOf(stroke / 2f, size.width - stroke / 2f))
                drawLine(if (enabled) colors.primary else colors.textSecondary, Offset(x, 0f), Offset(x, size.height), stroke)
            }
        }
    })
}

private val SeekTrackHeight = 32.dp
private val PlayheadWidth = 2.dp
private val BarPitch = 4.dp
private val BarGap = 2.dp
