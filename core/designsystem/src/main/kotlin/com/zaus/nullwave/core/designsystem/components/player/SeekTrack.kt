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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlin.math.abs

/** All geometry is measured along the full timeline. Recompute it only when data or size changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeekTrack(
    state: SliderState,
    enabled: Boolean,
    waveform: List<Float>?,
    scrubbing: Boolean,
    glowEnabled: Boolean,
    motion: WaveformMotion? = null,
) {
    val colors = NullWaveTheme.colors
    Spacer(Modifier.fillMaxWidth().height(SeekTrackHeight).drawWithCache {
        val rtl = layoutDirection == LayoutDirection.Rtl
        val stroke = PlayheadWidth.toPx()
        val ticks = (size.width / RulerPitch.toPx()).toInt().coerceAtLeast(1)
        fun Path.mark(start: Float, end: Float, height: Float) {
            if (end <= start) return
            val left = if (rtl) size.width - end else start
            addRect(Rect(left, (size.height - height) / 2f, left + (end - start), (size.height + height) / 2f))
        }
        val timeline = Path().apply {
            if (waveform == null) {
                val pitch = size.width / ticks
                val width = minOf(RulerTickWidth.toPx(), pitch)
                for (index in 0 until ticks) {
                    val x = (index + 0.5f) * pitch - width / 2f
                    val height = if (index % 4 == 0) RulerMajorHeight else RulerMinorHeight
                    mark(x, x + width, height.toPx())
                }
            } else if (waveform.isEmpty()) {
                mark(0f, size.width, stroke * 2f)
            } else {
                val bars = minOf(waveform.size, (size.width / BarPitch.toPx()).toInt().coerceAtLeast(1))
                val pitch = size.width / bars
                val width = (pitch - BarGap.toPx()).coerceAtLeast(1f).coerceAtMost(pitch)
                for (bar in 0 until bars) {
                    val startBin = (bar.toLong() * waveform.size / bars).toInt()
                    val endBin = ((bar + 1L) * waveform.size / bars).toInt()
                    var peak = 0f
                    for (index in startBin until endBin) peak = maxOf(peak, waveform[index])
                    val x = bar * pitch + (pitch - width) / 2f
                    mark(x, x + width, maxOf(stroke, size.height * peak))
                }
            }
        }
        val waveRadius = WaveGlowRadius.toPx()
        val waveGlow = PlayerLineGlow(colors.secondary, waveRadius, WaveGlowAlpha)
        val playheadGlow = PlayerLineGlow(colors.primary, PlayheadGlowRadius.toPx(), PlayheadGlowAlpha)
        val scrubGlow = PlayerLineGlow(colors.primary, ScrubGlowRadius.toPx(), ScrubGlowAlpha)
        val playedTimeline = Path()
        val playedBounds = Path()
        fun DrawScope.drawTimeline(boundary: Float, active: Color) {
            drawPath(timeline, colors.outline)
            if (enabled && glowEnabled && state.coercedValueAsFraction > 0f) {
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
        }
        onDrawBehind {
            val fraction = state.coercedValueAsFraction
            val boundary = size.width * if (rtl) 1f - fraction else fraction
            val active = if (enabled) colors.secondary else colors.textSecondary
            val heightScale = if (waveform.isNullOrEmpty()) 1f else motion?.barHeightScale?.value ?: 1f
            if (heightScale < 1f) {
                scale(scaleX = 1f, scaleY = heightScale) { drawTimeline(boundary, active) }
            } else {
                drawTimeline(boundary, active)
            }
            motion?.scanPhase?.let { drawRulerScan(ticks, it.value, active) }
            // Identical centre/width in both states. Drag changes emphasis, never precision.
            val halfStroke = minOf(stroke, size.width) / 2f
            val x = boundary.coerceIn(halfStroke, size.width - halfStroke)
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

/** A small bank of illuminated slashes above the ruler. The playhead stays visible in front. */
private fun DrawScope.drawRulerScan(ticks: Int, phase: Float, color: Color) {
    val direction = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    val center = (ticks - 1) * phase
    val pitch = size.width / ticks
    val halfWidth = minOf(1.5.dp.toPx(), pitch / 4)
    val top = size.height / 2f - 11.dp.toPx()
    val bottom = top + 4.dp.toPx()
    val radius = 4
    for (index in maxOf(0, center.toInt() - radius)..minOf(ticks - 1, center.toInt() + radius)) {
        val strength = (1f - abs(index - center) / radius).coerceAtLeast(0f)
        val fromStart = (index + 0.5f) * pitch
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - fromStart else fromStart
        drawLine(color, Offset(x - direction * halfWidth, bottom), Offset(x + direction * halfWidth, top),
            strokeWidth = RulerTickWidth.toPx(), alpha = strength * 0.9f)
    }
}

private val SeekTrackHeight = 32.dp
private val PlayheadWidth = 2.dp
private val BarPitch = 4.dp
private val BarGap = 2.dp
private val RulerPitch = 8.dp
private val RulerTickWidth = 1.5.dp
private val RulerMinorHeight = 4.dp
private val RulerMajorHeight = 8.dp
private val WaveGlowRadius = 4.dp
private const val WaveGlowAlpha = 0.28f
private val PlayheadGlowRadius = 6.dp
private const val PlayheadGlowAlpha = 0.75f
private val ScrubGlowRadius = 10.dp
private const val ScrubGlowAlpha = 0.85f
