package com.zaus.nullwave.feature.player.ui

import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSwapMotionTest {
    private val width = 1_000f
    private val seeds = 0 until 50

    private fun plan(seed: Int) = QueueSwapMotion.plan(Random(seed))
    private fun displacement(slices: List<SwapSlice>, progress: Float) =
        slices.map { abs(QueueSwapMotion.pose(it, progress, width).dx) }.average()

    @Test
    fun `strips cover the row from top to bottom`() {
        for (seed in seeds) {
            val slices = plan(seed)
            assertTrue("Seed $seed has 6–9 strips", slices.size in 6..9)
            assertEquals(0f, slices.first().top, 0f)
            assertEquals(1f, slices.last().bottom, 0f)
            slices.zipWithNext { a, b -> assertEquals(a.bottom, b.top, 0f) }
            assertTrue(slices.all { abs(it.offset) in 0.04f..0.18f })
        }
    }

    @Test
    fun `each move breaks the row differently but a seed is reproducible`() {
        assertEquals(plan(5), plan(5))
        assertNotEquals(plan(1), plan(2))
        val layouts = seeds.map { seed -> plan(seed).map { it.top to it.offset } }.toSet()
        assertEquals(seeds.count(), layouts.size)
    }

    @Test
    fun `the slot opens before the row appears`() {
        assertFalse(QueueSwapMotion.revealed(0f))
        assertFalse(QueueSwapMotion.revealed(QueueSwapMotion.RevealAt - 0.01f))
        assertTrue(QueueSwapMotion.revealed(QueueSwapMotion.RevealAt))
        val blink = (0..29).map { QueueSwapMotion.frameAlpha(it / 100f) }.toSet()
        assertEquals("The empty slot's frame blinks", setOf(0.9f, 0.3f), blink)
        assertTrue("The neighbours finish sliding first",
            QueueSwapMotion.SlideMillis <= QueueSwapMotion.ArriveMillis * QueueSwapMotion.RevealAt)
    }

    @Test
    fun `strips start displaced and snap in from the top as the scan passes`() {
        for (seed in seeds) {
            val slices = plan(seed)
            val start = QueueSwapMotion.RevealAt
            assertTrue("Seed $seed starts broken", slices.all { QueueSwapMotion.pose(it, start, width).dx != 0f })
            for (step in 0..50) {
                val p = start + (QueueSwapMotion.ScanEnd - start) * step / 50f
                val scan = QueueSwapMotion.scanFraction(p)
                for (slice in slices.filter { it.bottom <= scan }) {
                    assertEquals(SwapSlicePose(0f, flash = false), QueueSwapMotion.pose(slice, p, width))
                }
            }
        }
    }

    @Test
    fun `passed rows slide on the move's clock and stay put once there`() {
        val slideEnd = QueueSwapMotion.SlideMillis.toFloat() / QueueSwapMotion.ArriveMillis
        assertEquals(1f, QueueSwapMotion.slideRemaining(0f), 0f)
        val curve = (0..100).map { QueueSwapMotion.slideRemaining(slideEnd * it / 100f) }
        curve.zipWithNext { a, b -> assertTrue("The slide never moves back", b <= a) }
        assertTrue("Halfway through the slide the row is on its way", curve[50] in 0.01f..0.99f)
        for (p in listOf(slideEnd, 0.5f, 1f, 1.5f)) assertEquals(0f, QueueSwapMotion.slideRemaining(p), 1e-4f)
    }

    @Test
    fun `the row is whole from the end of the scan`() {
        for (seed in seeds) for (slice in plan(seed)) for (p in listOf(QueueSwapMotion.ScanEnd, 0.9f, 1f, 1.5f)) {
            assertEquals(SwapSlicePose(0f, flash = false), QueueSwapMotion.pose(slice, p, width))
        }
        assertEquals(1f, QueueSwapMotion.scanFraction(QueueSwapMotion.ScanEnd), 0f)
        assertEquals(0f, QueueSwapMotion.scanFraction(0f), 0f)
    }

    @Test
    fun `strips jitter and flash while they wait, and calm down overall`() {
        val reveal = QueueSwapMotion.RevealAt
        assertTrue(seeds.any { seed ->
            plan(seed).any { slice -> (30..79).any { QueueSwapMotion.pose(slice, it / 100f, width).flash } }
        })
        assertTrue("Some strip jumps the other way", seeds.any { seed ->
            plan(seed).any { slice ->
                (30..79).map { QueueSwapMotion.pose(slice, it / 100f, width).dx }.filter { it != 0f }
                    .map { it > 0f }.distinct().size == 2
            }
        })
        for (seed in seeds) {
            val slices = plan(seed)
            assertTrue("Seed $seed calms down", displacement(slices, 0.65f) < displacement(slices, reveal))
        }
    }

    @Test
    fun `the frame locks in at full strength and then fades out`() {
        assertEquals(0.5f, QueueSwapMotion.frameAlpha(0.5f), 0f)
        assertEquals(1f, QueueSwapMotion.frameAlpha(QueueSwapMotion.ScanEnd), 0f)
        assertTrue(QueueSwapMotion.frameAlpha(0.9f) in 0.4f..0.6f)
        assertEquals(0f, QueueSwapMotion.frameAlpha(1f), 0f)
    }
}

class QueueSwapTrackerTest {
    private val a = QueueEntryId("a")
    private val b = QueueEntryId("b")
    private val c = QueueEntryId("c")
    private val d = QueueEntryId("d")
    private val shown = listOf(a, b, c, d)

    @Test
    fun `nothing lands without a request`() {
        assertNull(QueueSwapTracker().landed(listOf(a, c, b, d))?.entry)
    }

    @Test
    fun `the requested entry lands right before the requested neighbour`() {
        val tracker = QueueSwapTracker()
        tracker.requested(b, d, shown)
        assertNull("The service has not answered yet", tracker.landed(shown))
        assertEquals(b, tracker.landed(listOf(a, c, b, d))?.entry)
    }

    @Test
    fun `moving to the end lands as the last entry`() {
        val tracker = QueueSwapTracker()
        tracker.requested(a, null, shown)
        assertEquals(a, tracker.landed(listOf(b, c, d, a))?.entry)
    }

    @Test
    fun `only the passed entries slide, one slot towards the old position`() {
        val down = QueueSwapTracker().apply { requested(b, null, shown) }.landed(listOf(a, c, d, b))!!
        assertEquals(listOf(0, 0, -1, -1), listOf(a, b, c, d).map(down::shift))

        val up = QueueSwapTracker().apply { requested(d, b, shown) }.landed(listOf(a, d, b, c))!!
        assertEquals(listOf(0, 1, 1, 0), listOf(a, b, c, d).map(up::shift))
    }

    @Test
    fun `another change to the order is not the requested move`() {
        val tracker = QueueSwapTracker()
        tracker.requested(b, d, shown)
        assertNull("Someone else's edit", tracker.landed(listOf(a, b, d, c))?.entry)
        assertNull("The entry was removed", tracker.landed(listOf(a, c, d))?.entry)
    }

    @Test
    fun `any new order answers the request`() {
        val tracker = QueueSwapTracker()
        tracker.requested(b, d, shown)
        tracker.settle(shown)
        assertEquals("The order it was sent from keeps it open", b, tracker.landed(listOf(a, c, b, d))?.entry)
        tracker.settle(listOf(a, b, d, c))
        assertNull("A rejected move is not replayed by a later change", tracker.landed(listOf(a, c, b, d))?.entry)
    }
}
