package com.zaus.nullwave.feature.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalPointerSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.motion.decorativeMotionEnabled
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RemoveDistance = 72.dp
private val EdgeGlowWidth = 28.dp
private val ScanGlowWidth = 24.dp

/** How long a hidden row waits for the service to drop it before it comes back. */
private const val RestoreDelayMillis = 1_500L

private fun Density.removeThreshold(widthPx: Float) = minOf(RemoveDistance.toPx(), widthPx / 2f)

/** One row's removal animation. Pending means the row is shredding and the command is not sent yet. */
private class QueueRowExit {
    val shred = Animatable(0f)
    val collapse = Animatable(0f)
    var active by mutableStateOf(false)
    var pending = false
    var startPx = 0f
    var widthPx = 0f
    /** A fresh random layout per removal, so no two rows break apart the same way. */
    var plan: List<ShredBlock> = emptyList()
}

/**
 * Swipe a row left to remove it. While dragging, a danger glow grows behind the row and along its
 * trailing edge; at the threshold it arms with a haptic tick. Releasing there shreds the row into
 * randomly cut blocks that drift, hop and drop out one by one, collapses its height so the rows
 * below close the gap, and only then sends the
 * command. The service snapshot still decides: a row it keeps comes back after [RestoreDelayMillis].
 * Leaving composition mid-animation sends the command at once, so a removal is never lost.
 * Reduced motion skips the animation and sends immediately; the glow follows the theme glow policy.
 */
@Composable
internal fun QueueEntrySwipe(
    modifier: Modifier,
    onRemove: (() -> Unit)?,
    moveActions: List<CustomAccessibilityAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    val remove by rememberUpdatedState(onRemove)
    val canRemove = onRemove != null
    val dragOffset = remember(canRemove) { if (canRemove) mutableFloatStateOf(0f) else null }
    val activeOffset by rememberUpdatedState(dragOffset)
    val exit = remember { QueueRowExit() }
    val scope = rememberCoroutineScope()
    val animateRemoval by rememberUpdatedState(decorativeMotionEnabled())
    val haptics = LocalHapticFeedback.current
    val glow = NullWaveTheme.effects.glowEnabled
    val colors = NullWaveTheme.colors
    val danger = colors.danger

    DisposableEffect(exit) {
        onDispose {
            if (exit.pending) {
                exit.pending = false
                remove?.invoke()
            }
        }
    }

    fun beginRemoval(fromPx: Float): Boolean {
        val action = remove ?: return false
        if (exit.active) return false
        if (!animateRemoval) {
            action()
            return true
        }
        exit.startPx = fromPx
        exit.plan = QueueRemovalMotion.shredPlan(Random.Default)
        exit.active = true
        exit.pending = true
        scope.launch {
            exit.shred.animateTo(1f, tween(QueueRemovalMotion.ShredMillis, easing = LinearEasing))
            exit.collapse.animateTo(1f, tween(QueueRemovalMotion.CollapseMillis, easing = FastOutSlowInEasing))
            exit.pending = false
            val confirmed = remove
            if (confirmed != null) {
                confirmed()
                delay(RestoreDelayMillis)
            }
            // Still composed: the service kept this entry (busy, rejected or stale). Bring it back.
            exit.collapse.animateTo(0f, tween(QueueRemovalMotion.CollapseMillis, easing = FastOutSlowInEasing))
            exit.shred.animateTo(0f, tween(QueueRemovalMotion.RestoreMillis, easing = LinearEasing))
            exit.active = false
        }
        return true
    }

    val label = stringResource(R.string.nw_queue_remove)
    val actions = if (!canRemove && moveActions.isEmpty()) Modifier else Modifier
        .semantics {
            customActions = buildList {
                addAll(moveActions)
                if (canRemove) add(CustomAccessibilityAction(label) { beginRemoval(0f) })
            }
        }
    val swipe = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            // The reorder handle and the current row's playback button consume down to keep their
            // press from selecting the row. A horizontal swipe from either still belongs to this row:
            // the handle sits at the trailing edge, where a left swipe naturally starts.
            val down = awaitFirstDown(requireUnconsumed = false)
            if (exit.active) return@awaitEachGesture
            // Revoking removal detaches this offset. A later grant belongs to a new press.
            val offset = activeOffset
            val threshold = removeThreshold(size.width.toFloat())
            var armed = false
            fun trackArming() {
                val nowArmed = offset != null && -offset.floatValue >= threshold
                if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                armed = nowArmed
            }
            var yieldedToList = false
            val drag = awaitHorizontalPointerSlopOrCancellation(down.id, down.type) { change, overSlop ->
                val travel = change.position - down.position
                // The child sees a move before the list. Let a vertical or tied move start scrolling.
                if (change.id != down.id || abs(travel.y) >= abs(travel.x)) yieldedToList = true
                if (!yieldedToList) {
                    change.consume()
                    offset?.floatValue = overSlop
                    trackArming()
                }
            } ?: return@awaitEachGesture
            try {
                val released = horizontalDrag(drag.id) { change ->
                    if (offset != null) offset.floatValue += change.positionChange().x
                    trackArming()
                    change.consume()
                }
                if (released && offset != null && offset === activeOffset && -offset.floatValue >= threshold) {
                    beginRemoval(offset.floatValue.coerceIn(-RemoveDistance.toPx(), 0f))
                }
            } finally {
                offset?.floatValue = 0f
            }
        }
    }

    // How strongly the danger cue shows: drag progress before release, fading out while shredding.
    fun intensity(density: Density): Float = if (exit.active) {
        1f - exit.shred.value
    } else {
        val offset = dragOffset?.floatValue ?: 0f
        QueueRemovalMotion.armProgress(offset, with(density) { removeThreshold(exit.widthPx) })
    }

    Box(
        modifier.then(actions).then(swipe)
            // Clip outside the collapse so the shrinking height also clips the row.
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = (placeable.height * (1f - exit.collapse.value)).roundToInt()
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .onSizeChanged { exit.widthPx = it.width.toFloat() }
            .drawBehind {
                if (!glow) return@drawBehind
                val strength = intensity(this)
                if (strength <= 0f) return@drawBehind
                val revealed = if (exit.active) size.width
                else (-(dragOffset?.floatValue ?: 0f)).coerceIn(0f, RemoveDistance.toPx())
                val left = size.width - revealed
                drawRect(danger.copy(alpha = 0.10f + 0.18f * strength), Offset(left, 0f), Size(revealed, size.height))
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, danger.copy(alpha = 0.35f * strength)), left, size.width),
                    Offset(left, 0f), Size(revealed, size.height),
                )
            },
    ) {
        if (canRemove || exit.active) {
            NullWaveIcon(NullWaveIcons.Delete, null,
                Modifier.align(AbsoluteAlignment.CenterRight).padding(horizontal = 16.dp).size(20.dp)
                    .graphicsLayer {
                        val strength = intensity(this)
                        alpha = 0.35f + 0.65f * strength
                        val scale = if (strength >= 1f) 1.15f else 1f + 0.08f * strength
                        scaleX = scale
                        scaleY = scale
                    },
                tint = danger)
        }
        // The row follows the finger. Only a service snapshot can remove it from the list.
        val foreground = Modifier
            .graphicsLayer {
                translationX = if (exit.active || dragOffset == null) 0f
                else dragOffset.floatValue.coerceIn(-RemoveDistance.toPx(), 0f)
                alpha = if (exit.active) QueueRemovalMotion.shredAlpha(exit.shred.value) else 1f
            }
            .drawWithContent {
                if (!exit.active) {
                    drawContent()
                    val strength = intensity(this)
                    if (!glow || strength <= 0f) return@drawWithContent
                    // The trailing edge heats up as the row approaches the threshold.
                    val width = EdgeGlowWidth.toPx()
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, danger.copy(alpha = 0.45f * strength)),
                            size.width - width, size.width),
                        Offset(size.width - width, 0f), Size(width, size.height),
                    )
                    val stroke = (if (strength >= 1f) 3.dp else 2.dp).toPx()
                    drawLine(danger.copy(alpha = strength), Offset(size.width - stroke / 2, 0f),
                        Offset(size.width - stroke / 2, size.height), stroke)
                    return@drawWithContent
                }
                val progress = exit.shred.value
                if (progress >= 1f) return@drawWithContent
                val outline = 1.dp.toPx()
                for (block in exit.plan) {
                    val pose = QueueRemovalMotion.pose(block, progress, exit.startPx, size.width, size.height)
                    if (!pose.visible) continue
                    val topLeft = Offset(block.left * size.width, block.top * size.height)
                    val blockSize = Size((block.right - block.left) * size.width, (block.bottom - block.top) * size.height)
                    // Move first, then clip: the block carries its own piece of the row with it.
                    translate(pose.dx, pose.dy) {
                        clipRect(topLeft.x, topLeft.y, topLeft.x + blockSize.width, topLeft.y + blockSize.height) {
                            this@drawWithContent.drawContent()
                        }
                        if (pose.flash) drawRect(danger, topLeft, blockSize, style = Stroke(outline))
                    }
                }
                // A danger scan line sweeps right to left across the shredding row.
                val x = size.width * (1f - progress)
                val scanGlow = ScanGlowWidth.toPx()
                if (glow) drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, danger.copy(alpha = 0.4f)), x, x + scanGlow),
                    Offset(x, 0f), Size(scanGlow, size.height),
                )
                drawLine(danger, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
            .then(if (canRemove || exit.active) Modifier.background(colors.bg) else Modifier)
        Box(foreground) { content() }
    }
}
