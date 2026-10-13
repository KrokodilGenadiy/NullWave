package com.zaus.nullwave.core.designsystem.components.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabTransitionTest {
    @Test
    fun restingStateOnlyShowsSelection() {
        val transition = TabTransition.settled(5, 3, reduced = false)
        assertEquals(listOf(0f, 0f, 0f, 1f, 0f), (0..4).map { transition.underline(it, 1f).width })
    }

    @Test
    fun lockCollapsesBeforeGrowingNewLine() {
        val transition = TabTransition.settled(3, 0, false).retarget(2, 1f)
        for (step in 0..100) {
            val p = step / 100f
            val lines = (0..2).map { transition.underline(it, p) }
            assertTrue("Two selection lines at $p", lines.count { it.width * it.alpha > 0f } <= 1)
            assertTrue(lines.all { it.width in 0f..1f && it.alpha in 0f..1f })
        }
        assertEquals(0f, transition.underline(0, 0.25f).width, 0f)
        assertEquals(0f, transition.underline(2, 0.25f).width, 0f)
        assertEquals(1f, transition.underline(2, 1f).width, 0f)
    }

    @Test
    fun rapidRetargetPreservesVisibleGeometryDuringCollapseAndGrowth() {
        val transition = TabTransition.settled(4, 0, false).retarget(1, 1f)
        for (p in listOf(0.1f, 0.3f, 0.5f, 0.9f)) {
            val interrupted = transition.retarget(3, p)
            for (index in 0..3) {
                assertEquals(transition.underline(index, p), interrupted.underline(index, 0f))
            }
            assertEquals(TabUnderline(1f, 1f), interrupted.underline(3, 1f))
        }
    }

    @Test
    fun reducedModeCrossFadesWithoutChangingVisibleWidths() {
        val transition = TabTransition.settled(3, 0, true).retarget(1, 1f)
        for (step in 1..99) {
            val p = step / 100f
            val old = transition.underline(0, p)
            val next = transition.underline(1, p)
            assertEquals(1f, old.width, 0f)
            assertEquals(1f, next.width, 0f)
            assertEquals(1f, old.alpha + next.alpha, 0.00001f)
        }
    }

    @Test
    fun repeatedReducedRetargetsPreserveOpacityAndEventuallyRemoveStaleLines() {
        var transition = TabTransition.settled(3, 0, true)
        for (target in listOf(1, 2, 0, 2, 1)) {
            val before = (0..2).map { transition.underline(it, 0.3f) }
            transition = transition.retarget(target, 0.3f)
            for (index in 0..2) {
                val after = transition.underline(index, 0f)
                assertEquals(before[index].alpha, after.alpha, 0f)
                if (after.alpha > 0f) assertEquals(before[index].width, after.width, 0f)
            }
        }
        assertEquals(listOf(0f, 1f, 0f), (0..2).map { transition.underline(it, 1f).alpha })
    }
}
