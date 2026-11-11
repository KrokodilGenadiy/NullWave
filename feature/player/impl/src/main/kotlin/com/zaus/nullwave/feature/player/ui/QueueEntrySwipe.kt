package com.zaus.nullwave.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalPointerSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlin.math.abs

private val RemoveDistance = 72.dp

@Composable
internal fun QueueEntrySwipe(
    modifier: Modifier,
    onRemove: (() -> Unit)?,
    moveActions: List<CustomAccessibilityAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    val remove by rememberUpdatedState(onRemove)
    val hasReorderHandle by rememberUpdatedState(moveActions.isNotEmpty())
    val canRemove = onRemove != null
    val dragOffset = remember(canRemove) { if (canRemove) mutableFloatStateOf(0f) else null }
    val activeOffset by rememberUpdatedState(dragOffset)
    val label = stringResource(R.string.nw_queue_remove)
    val actions = if (!canRemove && moveActions.isEmpty()) Modifier else Modifier
        .semantics {
            customActions = buildList {
                addAll(moveActions)
                if (canRemove) add(CustomAccessibilityAction(label) {
                    val action = remove ?: return@CustomAccessibilityAction false
                    action()
                    true
                })
            }
        }
    val swipe = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // The handle consumes down. Keep watching the current row's nested playback button
            // so a horizontal swipe there still cancels its click.
            if (hasReorderHandle && down.isConsumed) return@awaitEachGesture
            // Revoking removal detaches this offset. A later grant belongs to a new press.
            val offset = activeOffset
            var yieldedToList = false
            val drag = awaitHorizontalPointerSlopOrCancellation(down.id, down.type) { change, overSlop ->
                val travel = change.position - down.position
                // The child sees a move before the list. Let a vertical or tied move start scrolling.
                if (change.id != down.id || abs(travel.y) >= abs(travel.x)) yieldedToList = true
                if (!yieldedToList) {
                    change.consume()
                    offset?.floatValue = overSlop
                }
            } ?: return@awaitEachGesture
            try {
                val released = horizontalDrag(drag.id) { change ->
                    if (offset != null) offset.floatValue += change.positionChange().x
                    change.consume()
                }
                val threshold = minOf(RemoveDistance.toPx(), size.width / 2f)
                if (released && offset != null && offset === activeOffset && -offset.floatValue >= threshold) {
                    remove?.invoke()
                }
            } finally {
                offset?.floatValue = 0f
            }
        }
    }
    val colors = NullWaveTheme.colors
    Box(modifier.then(actions).then(swipe).clipToBounds()) {
        if (canRemove) {
            NullWaveIcon(NullWaveIcons.Delete, null,
                Modifier.align(AbsoluteAlignment.CenterRight).padding(horizontal = 16.dp).size(20.dp),
                tint = colors.danger)
        }
        // The row follows the finger. Only a service snapshot can remove it from the list.
        val foreground = if (dragOffset == null) Modifier else Modifier.graphicsLayer {
            translationX = dragOffset.floatValue.coerceIn(-RemoveDistance.toPx(), 0f)
        }.background(colors.bg)
        Box(foreground) { content() }
    }
}
