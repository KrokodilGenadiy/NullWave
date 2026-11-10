package com.zaus.nullwave.feature.player.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zaus.nullwave.core.designsystem.motion.decorativeMotionEnabled
import com.zaus.nullwave.core.designsystem.theme.NullWaveColors
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

private val QueueSweep = tween<Float>(380, easing = CubicBezierEasing(0.25f, 0f, 0.35f, 1f))

@Composable
internal fun PlayerQueueTransition(
    queueOpen: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(showQueue: Boolean) -> Unit,
) {
    val target = if (queueOpen) 1f else 0f
    val progress = if (active && decorativeMotionEnabled()) {
        animateFloatAsState(target, QueueSweep, label = "Queue scan")
    } else {
        rememberUpdatedState(target)
    }
    val moving by remember(progress, target) { derivedStateOf { progress.value != target } }
    val colors = NullWaveTheme.colors
    Box(modifier.clipToBounds()) {
        for (showQueue in listOf(false, true)) {
            val destination = showQueue == queueOpen
            if (destination || moving) key(showQueue) {
                val page = when {
                    !destination -> Modifier.blockPageInput()
                    moving -> Modifier.zIndex(1f).queueScan(showQueue, colors) { progress.value }
                    else -> Modifier
                }
                Column(Modifier.matchParentSize().then(page).background(colors.bg)) { content(showQueue) }
            }
        }
    }
}

// Both pages keep their final layout. Only the part of the destination behind the scan is drawn.
private fun Modifier.queueScan(queue: Boolean, colors: NullWaveColors, progress: () -> Float) = drawWithCache {
    val cut = minOf(26.dp.toPx(), size.width / 4f, size.height / 4f)
    val joint = size.width * 0.58f
    val margin = 6.dp.toPx()
    val mask = Path()
    val edge = Path()
    val separator = Stroke(5.dp.toPx())
    val rail = Stroke(2.dp.toPx())
    onDrawWithContent {
        val y = size.height + cut + margin - (size.height + cut + 2 * margin) * progress()
        edge.reset()
        edge.moveTo(0f, y)
        edge.lineTo(joint, y)
        edge.lineTo(joint + cut, y - cut)
        edge.lineTo(size.width, y - cut)

        val end = if (queue) size.height else 0f
        mask.reset()
        mask.addPath(edge)
        mask.lineTo(size.width, end)
        mask.lineTo(0f, end)
        mask.close()
        clipPath(mask) { this@onDrawWithContent.drawContent() }

        drawPath(edge, colors.bg, style = separator)
        drawPath(edge, colors.primary, style = rail)
        val traceY = y - cut + 4.dp.toPx()
        drawLine(colors.secondary, Offset(joint + cut, traceY), Offset(size.width, traceY), rail.width)
    }
}

private fun Modifier.blockPageInput() = clearAndSetSemantics {}
    .onPreviewKeyEvent { true }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
