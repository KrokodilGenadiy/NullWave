package com.zaus.nullwave.feature.player.ui

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.playback.api.model.QueueEntryId

/** One displayed traversal. Replacing it cancels its pointer input and edge scrolling together. */
internal class QueueReorderGesture(
    private val list: LazyListState,
    private val order: List<QueueEntryId>,
    private val currentIndex: Int,
) {
    var pressedHandle: QueueEntryId? = null
    var dragging by mutableStateOf<QueueEntryId?>(null)
    var pointerY by mutableStateOf<Float?>(null)

    // List slots 0 and 1 contain the intro and the current entry. The hint has an Int key.
    val insertionIndex by derivedStateOf {
        val y = pointerY ?: return@derivedStateOf null
        val visible = list.layoutInfo.visibleItemsInfo
        for (item in visible) {
            if (item.index < 2 || item.key !is String) continue
            val index = currentIndex + item.index - 1
            if (y < item.offset + item.size / 2f) return@derivedStateOf index
        }
        (currentIndex + (visible.lastOrNull()?.index ?: 1)).coerceIn(currentIndex + 1, order.size)
    }

    fun drop(move: (QueueEntryId, QueueEntryId?) -> Unit) {
        val source = dragging ?: return
        val gap = insertionIndex ?: return
        // Both gaps bordering the original row leave it in place.
        if (order.getOrNull(gap) != source && order.getOrNull(gap - 1) != source) {
            move(source, order.getOrNull(gap))
        }
    }

    @Composable
    fun ScrollAtEdge() {
        if (dragging == null) return
        val density = LocalDensity.current
        val edge = with(density) { 48.dp.toPx() }
        val speed = with(density) { 420.dp.toPx() } / 1000
        val direction by remember(this, edge) {
            derivedStateOf {
                val y = pointerY ?: return@derivedStateOf 0
                val layout = list.layoutInfo
                val band = minOf(edge, (layout.viewportEndOffset - layout.viewportStartOffset) / 4f)
                when {
                    y < layout.viewportStartOffset + band && list.canScrollBackward -> -1
                    y > layout.viewportEndOffset - band && list.canScrollForward -> 1
                    else -> 0
                }
            }
        }
        LaunchedEffect(this, direction, speed) {
            if (direction == 0) return@LaunchedEffect
            list.scroll(MutatePriority.UserInput) {
                var previous = withFrameMillis { it }
                while (dragging != null) {
                    val now = withFrameMillis { it }
                    val consumed = scrollBy(direction * speed * (now - previous).coerceAtMost(40))
                    if (consumed == 0f && now > previous) break
                    previous = now
                }
            }
        }
    }
}

internal fun Modifier.queueReorderHandle(gesture: QueueReorderGesture?, id: QueueEntryId): Modifier {
    return pointerInput(gesture, id) {
        awaitEachGesture {
            val down = awaitFirstDown()
            // The handle column never forwards a press to playback, even when the icon is hidden.
            gesture?.pressedHandle = id
            down.consume()
            if (gesture != null) {
                awaitTouchSlopOrCancellation(down.id) { _, _ ->
                    // Revoke the pending hold. Leave the movement available to the scrolling list.
                    gesture.pressedHandle = null
                }
            }
        }
    }
}

@Composable
internal fun Modifier.queueReorderInput(
    gesture: QueueReorderGesture?,
    onMove: ((QueueEntryId, QueueEntryId?) -> Unit)?,
): Modifier {
    val move by rememberUpdatedState(onMove)
    if (gesture == null) return this
    // The list owns the rest of the gesture so the source row may scroll out of composition.
    return pointerInput(gesture) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            gesture.pressedHandle = null
            awaitPointerEvent(PointerEventPass.Main) // The touched handle claims this down first.
            val source = gesture.pressedHandle ?: return@awaitEachGesture
            val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            if (held.id != down.id || gesture.pressedHandle != source) return@awaitEachGesture
            gesture.dragging = source
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.singleOrNull() ?: break
                    if (change.id != down.id || change.isConsumed) break
                    // A stationary long press near the edge must not start scrolling or reorder.
                    if (gesture.pointerY != null || change.position.y != held.position.y) {
                        gesture.pointerY = change.position.y
                    }
                    change.consume()
                    if (!change.pressed) {
                        move?.let(gesture::drop)
                        break
                    }
                }
            } finally {
                gesture.pointerY = null
                gesture.dragging = null
            }
        }
    }
}
