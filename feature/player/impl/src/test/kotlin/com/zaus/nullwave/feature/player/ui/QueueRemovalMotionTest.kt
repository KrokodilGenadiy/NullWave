package com.zaus.nullwave.feature.player.ui

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueRemovalMotionTest {
    private val width = 1_000f
    private val height = 200f
    private val start = -180f
    private val seeds = 0 until 50

    private fun plan(seed: Int) = QueueRemovalMotion.shredPlan(Random(seed))

    @Test
    fun `glow grows with the leftward drag and is full at the threshold`() {
        assertEquals(0f, QueueRemovalMotion.armProgress(0f, 200f), 0f)
        assertEquals(0f, QueueRemovalMotion.armProgress(40f, 200f), 0f)
        assertEquals(0.5f, QueueRemovalMotion.armProgress(-100f, 200f), 0.0001f)
        assertEquals(1f, QueueRemovalMotion.armProgress(-200f, 200f), 0f)
        assertEquals(1f, QueueRemovalMotion.armProgress(-900f, 200f), 0f)
        assertEquals("A zero-width row never arms", 0f, QueueRemovalMotion.armProgress(-50f, 0f), 0f)
    }

    @Test
    fun `blocks tile the whole row without gaps or overlaps`() {
        for (seed in seeds) {
            val blocks = plan(seed)
            assertEquals("Seed $seed covers the row", 1f, blocks.sumOf { ((it.right - it.left) * (it.bottom - it.top)).toDouble() }.toFloat(), 0.0001f)
            val bands = blocks.groupBy { it.top to it.bottom }
            assertTrue("Seed $seed has 5–8 bands", bands.size in 5..8)
            for ((_, row) in bands) {
                val sorted = row.sortedBy { it.left }
                assertTrue(row.size in 1..3)
                assertEquals(0f, sorted.first().left, 0f)
                assertEquals(1f, sorted.last().right, 0f)
                sorted.zipWithNext { a, b -> assertEquals(a.right, b.left, 0f) }
            }
            val edges = bands.keys.sortedBy { it.first }
            assertEquals(0f, edges.first().first, 0f)
            assertEquals(1f, edges.last().second, 0f)
            edges.zipWithNext { a, b -> assertEquals(a.second, b.first, 0f) }
        }
    }

    @Test
    fun `each removal breaks apart differently but a seed is reproducible`() {
        assertEquals(plan(7), plan(7))
        val layouts = seeds.map { seed -> plan(seed).map { listOf(it.left, it.right, it.top, it.bottom) } }.toSet()
        assertTrue("Layouts repeat too often: ${layouts.size}", layouts.size > seeds.count() * 0.9)
        assertNotEquals(plan(1), plan(2))
    }

    @Test
    fun `the first frame is the intact row at the release offset`() {
        for (seed in seeds) for (block in plan(seed)) {
            assertEquals(ShredPose(start, 0f, visible = true, flash = false),
                QueueRemovalMotion.pose(block, 0f, start, width, height))
        }
    }

    @Test
    fun `every block is gone by the end`() {
        for (seed in seeds) for (block in plan(seed)) {
            assertFalse(QueueRemovalMotion.pose(block, 1f, start, width, height).visible)
            assertFalse(QueueRemovalMotion.pose(block, 0.96f, start, width, height).visible)
        }
    }

    @Test
    fun `blocks move independently mid shred, including vertically`() {
        val blocks = plan(3)
        val poses = blocks.map { QueueRemovalMotion.pose(it, 0.4f, start, width, height) }
        assertEquals("Every block takes its own position", poses.size, poses.map { it.dx }.toSet().size)
        assertTrue("Some block leaves its line", poses.any { abs(it.dy) > 1f })
        assertTrue("Glitch flashes happen", seeds.any { seed ->
            plan(seed).any { block -> (0..100).any { QueueRemovalMotion.pose(block, it / 100f, start, width, height).flash } }
        })
    }

    @Test
    fun `blocks drop out one by one rather than all at once`() {
        val blocks = plan(11)
        val visibleCounts = (0..20).map { step -> blocks.count { QueueRemovalMotion.pose(it, step / 20f, start, width, height).visible } }
        assertEquals(blocks.size, visibleCounts.first())
        assertEquals(0, visibleCounts.last())
        assertTrue("Count only falls", visibleCounts.zipWithNext().all { (a, b) -> b <= a })
        assertTrue("Blocks vanish over several frames", visibleCounts.distinct().size >= 3)
    }

    @Test
    fun `the row drifts left on average`() {
        for (seed in seeds) {
            val blocks = plan(seed)
            val first = blocks.map { QueueRemovalMotion.pose(it, 0.05f, start, width, height).dx }.average()
            val late = blocks.map { QueueRemovalMotion.pose(it, 0.5f, start, width, height).dx }.average()
            assertTrue("Seed $seed moved right", late < first)
        }
    }

    @Test
    fun `opacity holds early and reaches zero at the end`() {
        assertEquals(1f, QueueRemovalMotion.shredAlpha(0f), 0f)
        assertTrue(QueueRemovalMotion.shredAlpha(0.4f) > 0.9f)
        assertEquals(0f, QueueRemovalMotion.shredAlpha(1f), 0f)
        assertEquals(0f, QueueRemovalMotion.shredAlpha(2f), 0f)
    }
}
