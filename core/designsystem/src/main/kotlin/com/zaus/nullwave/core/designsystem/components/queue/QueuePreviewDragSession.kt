package com.zaus.nullwave.core.designsystem.components.queue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/** Measured, fully visible rows in one demo section; this is not a production reorder controller. */
internal data class QueuePreviewDragItem(val id: Long, val top: Float, val height: Float) {
    val bottom: Float get() = top + height
    val center: Float get() = top + height / 2f
}

/** Keeps the controlled order unchanged during the gesture and consumes a drop at most once. */
internal class QueuePreviewDragSession(
    val id: Long,
    private val originalOrder: List<Long>,
    private val visibleGroup: List<QueuePreviewDragItem>,
) {
    private val source = visibleGroup.single { it.id == id }
    private var finished = false
    var offsetPx by mutableFloatStateOf(0f)
        private set

    val targetId: Long
        get() {
            // The leading edge crosses a neighbour's midpoint. Using only the dragged center
            // would make a tall row unable to reach a shorter row at a clamped viewport edge.
            return if (offsetPx >= 0f) {
                visibleGroup.lastOrNull { it.top > source.top && it.center <= source.bottom + offsetPx }?.id ?: id
            } else {
                visibleGroup.firstOrNull { it.top < source.top && it.center >= source.top + offsetPx }?.id ?: id
            }
        }

    val insertAfter: Boolean get() = offsetPx > 0f

    fun dragBy(delta: Float) {
        if (finished || !delta.isFinite()) return
        // Keep the entire translated row inside the fully visible part of its own section.
        offsetPx = (offsetPx + delta).coerceIn(
            visibleGroup.first().top - source.top,
            visibleGroup.last().bottom - source.bottom,
        )
    }

    fun finish(currentOrder: List<Long>, accepted: Boolean, cancelled: Boolean): List<Long>? {
        if (finished) return null
        finished = true
        if (cancelled || !accepted || currentOrder != originalOrder || targetId == id) return null
        return moveQueuePreviewEntry(currentOrder, id, targetId)
    }

    fun matchesOrder(order: List<Long>): Boolean = order == originalOrder

    /** Final translations before committing the new order; includes neighbours making room. */
    fun settleOffsets(order: List<Long>?): Map<Long, Float> {
        if (order == null) return emptyMap()
        val rows = visibleGroup.associateBy { it.id }
        var top = visibleGroup.first().top
        return buildMap {
            order.forEach { entryId ->
                val row = rows[entryId] ?: return@forEach
                put(entryId, top - row.top)
                top += row.height
            }
        }
    }
}

/** IDs refer to queue entries, so two entries with identical track metadata remain independent. */
internal fun moveQueuePreviewEntry(order: List<Long>, id: Long, targetId: Long): List<Long> {
    val from = order.indexOf(id)
    val to = order.indexOf(targetId)
    if (from < 0 || to < 0 || from == to) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}
