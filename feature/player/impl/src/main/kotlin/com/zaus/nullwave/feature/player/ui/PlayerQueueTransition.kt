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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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

private class ScanEdge(private val size: Size, density: Density) {
    private val cut = minOf(with(density) { 26.dp.toPx() }, size.width / 4f, size.height / 4f)
    private val margin = with(density) { 6.dp.toPx() }
    private val joint = size.width * 0.58f
    val traceStartX = joint + cut

    fun trace(path: Path, progress: Float): Float {
        val y = size.height + cut + margin - (size.height + cut + 2 * margin) * progress
        val rightY = y - cut
        path.rewind()
        path.moveTo(0f, y)
        path.lineTo(joint, y)
        path.lineTo(traceStartX, rightY)
        path.lineTo(size.width, rightY)
        return rightY
    }
}

private class RevealedPage(private val queue: Boolean, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        ScanEdge(size, density).trace(path, progress)
        val end = if (queue) size.height else 0f
        path.lineTo(size.width, end)
        path.lineTo(0f, end)
        path.close()
        return Outline.Generic(path)
    }
}

private fun Modifier.queueScan(queue: Boolean, colors: NullWaveColors, progress: () -> Float) =
    drawWithCache {
        val scan = ScanEdge(size, this)
        val path = Path()
        val separator = Stroke(5.dp.toPx())
        val rail = Stroke(2.dp.toPx())
        onDrawWithContent {
            drawContent()
            val traceY = scan.trace(path, progress()) + 4.dp.toPx()
            drawPath(path, colors.bg, style = separator)
            drawPath(path, colors.primary, style = rail)
            drawLine(colors.secondary, Offset(scan.traceStartX, traceY), Offset(size.width, traceY), rail.width)
        }
    }.graphicsLayer {
        // Compose uses the layer outline for both drawing and the initial pointer hit test.
        shape = RevealedPage(queue, progress())
        clip = true
    }

private fun Modifier.blockPageInput() = clearAndSetSemantics {}
    .onPreviewKeyEvent { true }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
