package com.zaus.nullwave.core.designsystem.components.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePreviewDragSessionTest {
    private val order = listOf(10L, 20L, 21L, 22L, 30L)
    private val visibleGroup = listOf(
        QueuePreviewDragItem(20L, top = 100f, height = 60f),
        QueuePreviewDragItem(21L, top = 160f, height = 100f),
        QueuePreviewDragItem(22L, top = 260f, height = 80f),
    )

    @Test fun differentHeightsUseMeasuredCentersAndConsumeDropOnce() {
        val drag = QueuePreviewDragSession(20L, order, visibleGroup)
        drag.dragBy(49f)
        assertEquals(20L, drag.targetId)
        drag.dragBy(2f)
        assertEquals(21L, drag.targetId)
        assertEquals(listOf(10L, 21L, 20L, 22L, 30L), drag.finish(order, accepted = true, cancelled = false))
        assertNull(drag.finish(order, accepted = true, cancelled = false))
    }

    @Test fun translatedRowStaysWithinVisibleSectionAtBothEdges() {
        val drag = QueuePreviewDragSession(21L, order, visibleGroup)
        drag.dragBy(-10_000f)
        assertEquals(-60f, drag.offsetPx, 0f)
        assertEquals(20L, drag.targetId)
        drag.dragBy(10_000f)
        assertEquals(80f, drag.offsetPx, 0f)
        assertEquals(22L, drag.targetId)
        assertEquals(listOf(10L, 20L, 22L, 21L, 30L), drag.finish(order, accepted = true, cancelled = false))
    }

    @Test fun rejectionCancellationAndExternalReplacementDoNotCommit() {
        fun session() = QueuePreviewDragSession(20L, order, visibleGroup).apply { dragBy(100f) }
        assertNull(session().finish(order, accepted = false, cancelled = false))
        assertNull(session().finish(order, accepted = true, cancelled = true))
        assertNull(session().finish(order.reversed(), accepted = true, cancelled = false))
        assertNull(session().finish(order - 20L, accepted = true, cancelled = false))
    }

    @Test fun noMovementAndInvalidDeltaDoNotCreateMoveRequests() {
        val drag = QueuePreviewDragSession(20L, order, visibleGroup)
        drag.dragBy(Float.NaN)
        drag.dragBy(Float.POSITIVE_INFINITY)
        assertEquals(0f, drag.offsetPx, 0f)
        assertNull(drag.finish(order, accepted = true, cancelled = false))
    }

    @Test fun movingDuplicateTracksUsesEntryIdentityWithoutDroppingEitherEntry() {
        // Entries 20 and 21 deliberately represent the same media item in the demo.
        assertEquals(listOf(10L, 21L, 22L, 20L, 30L), moveQueuePreviewEntry(order, 20L, 22L))
        assertEquals(listOf(10L, 21L, 20L, 22L, 30L), moveQueuePreviewEntry(order, 21L, 20L))
        assertEquals(order, moveQueuePreviewEntry(order, 99L, 20L))
        assertEquals(order, moveQueuePreviewEntry(order, 20L, 20L))
    }

    @Test fun tallRowCanReachShortNeighbourAtClampedEdge() {
        val drag = QueuePreviewDragSession(2L, listOf(1L, 2L), listOf(
            QueuePreviewDragItem(1L, top = 0f, height = 40f),
            QueuePreviewDragItem(2L, top = 40f, height = 240f),
        ))
        drag.dragBy(-1_000f)
        assertEquals(-40f, drag.offsetPx, 0f)
        assertEquals(1L, drag.targetId)
        assertEquals(listOf(2L, 1L), drag.finish(listOf(1L, 2L), accepted = true, cancelled = false))
    }

    @Test fun settlingMixedHeightsMatchesReorderedLayoutInBothDirections() {
        // Expected absolute tops describe the future measured layout, not just displacement signs.
        val cases = listOf(
            Triple(20L, 180f, mapOf(21L to 100f, 22L to 200f, 20L to 280f)),
            Triple(21L, 80f, mapOf(20L to 100f, 22L to 160f, 21L to 240f)),
            Triple(21L, -60f, mapOf(21L to 100f, 20L to 200f, 22L to 260f)),
        )
        for ((source, delta, expectedTops) in cases) {
            val drag = QueuePreviewDragSession(source, order, visibleGroup).apply { dragBy(delta) }
            val proposed = checkNotNull(drag.finish(order, accepted = true, cancelled = false))
            val offsets = drag.settleOffsets(proposed)
            assertEquals("Only visible section rows participate in settling", expectedTops.keys, offsets.keys)
            for (row in visibleGroup) {
                assertEquals("Entry ${row.id} must land at its post-commit position",
                    expectedTops.getValue(row.id), row.top + offsets.getValue(row.id), 0f)
            }
            assertEquals("Other sections keep their place", 10L, proposed.first())
            assertEquals("Other sections keep their place", 30L, proposed.last())
        }
    }

    @Test fun rejectedAndCancelledDropsKeepHeldOffsetForRollbackAndNeighboursUnchanged() {
        for ((accepted, cancelled) in listOf(false to false, true to true)) {
            val drag = QueuePreviewDragSession(20L, order, visibleGroup).apply { dragBy(100f) }
            val proposed = drag.finish(order, accepted = accepted, cancelled = cancelled)
            assertNull(proposed)
            assertEquals("Rollback starts at the last pointer position", 100f, drag.offsetPx, 0f)
            assertTrue("An empty destination map returns every row to zero translation", drag.settleOffsets(proposed).isEmpty())
            drag.dragBy(-100f)
            assertEquals("Late pointer movement cannot change a rollback snapshot", 100f, drag.offsetPx, 0f)
            assertTrue(drag.matchesOrder(order))
        }
    }

    @Test fun latePointerMovementCannotChangeAcceptedSettleSnapshot() {
        val drag = QueuePreviewDragSession(20L, order, visibleGroup).apply { dragBy(51f) }
        val proposed = checkNotNull(drag.finish(order, accepted = true, cancelled = false))
        val offsets = drag.settleOffsets(proposed)
        drag.dragBy(10_000f)
        drag.dragBy(-10_000f)
        assertEquals(51f, drag.offsetPx, 0f)
        assertEquals(21L, drag.targetId)
        assertEquals(offsets, drag.settleOffsets(proposed))
        assertEquals(listOf(10L, 20L, 21L, 22L, 30L), order)
        assertTrue(drag.matchesOrder(order))
        assertFalse("A changed host order invalidates the pending commit", drag.matchesOrder(proposed))
        assertNull(drag.finish(order, accepted = true, cancelled = false))
    }
}
