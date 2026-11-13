package com.zaus.nullwave.feature.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import kotlin.random.Random

/**
 * One confirmed move playing out. A single frame clock drives both the passed rows' slide and the
 * moved row's arrival, so the animation ends with its clock at any system animation speed, and a
 * row scrolled into view mid-move joins at the current moment instead of starting over.
 */
internal class SwapPlayback(val landing: QueueSwapLanding, val plan: List<SwapSlice>) {
    private val clock = Animatable(0f)
    val progress: Float get() = clock.value

    suspend fun play() {
        clock.animateTo(1f, tween(QueueSwapMotion.ArriveMillis, easing = LinearEasing))
    }

    /** Vertical offset of a row that [shift]s one slot, from its old slot towards 0. */
    fun slideOffset(shift: Int, rowHeight: Float): Float =
        -shift * rowHeight * QueueSwapMotion.slideRemaining(progress)
}

/** A playback for [order] if it is the move this queue asked for; null otherwise or under reduced motion. */
@Composable
internal fun rememberSwapPlayback(tracker: QueueSwapTracker, order: List<QueueEntryId>, motion: Boolean): SwapPlayback? {
    val playback = remember(tracker, order) {
        tracker.landed(order)?.takeIf { motion }?.let { SwapPlayback(it, QueueSwapMotion.plan(Random.Default)) }
    }
    LaunchedEffect(tracker, order) {
        tracker.settle(order)
        playback?.play()
    }
    return playback
}

/**
 * The moved row teleporting in: an empty slot with a blinking frame, then offset strips that
 * snap together under a downward scan line, and a frame that locks in and fades.
 */
internal fun ContentDrawScope.drawSwapArrival(arrival: SwapPlayback, accent: Color, bg: Color, glow: Boolean) {
    val progress = arrival.progress
    if (progress >= 1f) {
        drawContent()
        return
    }
    val width = size.width
    val height = size.height
    if (QueueSwapMotion.revealed(progress)) {
        val outline = 1.dp.toPx()
        for (slice in arrival.plan) {
            val pose = QueueSwapMotion.pose(slice, progress, width)
            val top = slice.top * height
            val bottom = slice.bottom * height
            translate(left = pose.dx) {
                clipRect(0f, top, width, bottom) { this@drawSwapArrival.drawContent() }
                if (pose.flash) drawRect(accent, Offset(0f, top), Size(width, bottom - top), style = Stroke(outline))
            }
        }
        val scan = QueueSwapMotion.scanFraction(progress)
        if (scan < 1f) {
            val y = height * scan
            // Not materialized yet below the line.
            drawRect(bg.copy(alpha = 0.6f), Offset(0f, y), Size(width, height - y))
            if (glow) {
                val trail = 16.dp.toPx()
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.35f)), y - trail, y),
                    Offset(0f, y - trail), Size(width, trail),
                )
            }
            drawLine(accent, Offset(0f, y), Offset(width, y), 2.dp.toPx())
        }
    }
    val frame = QueueSwapMotion.frameAlpha(progress)
    if (frame > 0f) drawHorizontalFrame(accent.copy(alpha = frame))
}

/** The row being dragged: a dimmed ghost with scan lines and the accent frame of the reorder artboard. */
internal fun DrawScope.drawDragGhost(accent: Color, surface: Color) {
    drawRect(surface.copy(alpha = 0.65f))
    val step = 4.dp.toPx()
    val line = accent.copy(alpha = 0.08f)
    var y = step / 2
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    drawHorizontalFrame(accent)
}

/** Where the dragged row will land: an accent rail with end brackets and, if allowed, a glow. */
internal fun DrawScope.drawDropRail(y: Float, accent: Color, glow: Boolean) {
    val width = size.width
    if (glow) {
        val halo = 8.dp.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.4f), Color.Transparent), y - halo, y + halo),
            Offset(0f, y - halo), Size(width, halo * 2),
        )
    }
    drawLine(accent, Offset(0f, y), Offset(width, y), 2.dp.toPx())
    val tickHeight = 10.dp.toPx()
    val tickWidth = 3.dp.toPx()
    drawRect(accent, Offset(0f, y - tickHeight / 2), Size(tickWidth, tickHeight))
    drawRect(accent, Offset(width - tickWidth, y - tickHeight / 2), Size(tickWidth, tickHeight))
}

private fun DrawScope.drawHorizontalFrame(color: Color) {
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
    drawLine(color, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
}
