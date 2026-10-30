package com.zaus.nullwave.core.playback.media3.notification

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackNotificationUpdatesTest {
    @Test
    fun `content burst publishes the latest value at the original deadline`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        val posted = mutableListOf<String>()
        updates.schedule { posted += "preparing" }
        runCurrent()
        advanceTimeBy(100)
        updates.schedule { posted += "playing" }
        advanceTimeBy(100)
        updates.schedule { posted += "paused" }
        advanceTimeBy(49)
        runCurrent()
        assertTrue(posted.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("paused"), posted)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("paused"), posted)
    }

    @Test
    fun `reentrant content update gets a fresh window and cancellation does not discard its successor`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        val posted = mutableListOf<String>()
        updates.schedule {
            posted += "first"
            updates.schedule { posted += "second" }
        }
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("first"), posted)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("first", "second"), posted)

        updates.schedule { posted += "cancelled" }
        runCurrent()
        updates.cancelScheduled()
        updates.schedule { posted += "successor" }
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("first", "second", "successor"), posted)
    }

    @Test
    fun `a silently dropped notification is repaired and confirmation stops after readback succeeds`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        var published: Long? = null
        val repaired = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        var checks = 0
        updates.confirm(7, { true }, { checks++; published == it }, {
            repaired += it
            published = it
        }, failed::add)
        runCurrent()

        advanceTimeBy(750)
        runCurrent()
        assertEquals(listOf(7L), repaired)
        assertEquals(1, checks)
        advanceTimeBy(750)
        runCurrent()
        assertEquals(2, checks)
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(listOf(7L), repaired)
        assertTrue(failed.isEmpty())
        assertEquals(2, checks)
    }

    @Test
    fun `persistent rejection is reported once after the bounded repairs and final readback`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        val repaired = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        var checks = 0
        updates.confirm(4, { true }, { checks++; false }, repaired::add, failed::add)
        runCurrent()

        repeat(2) {
            advanceTimeBy(750)
            runCurrent()
        }
        assertEquals(listOf(4L, 4L), repaired)
        assertTrue(failed.isEmpty())
        advanceTimeBy(750)
        runCurrent()
        assertEquals(listOf(4L), failed)
        assertEquals(3, checks)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(4L, 4L), repaired)
        assertEquals(listOf(4L), failed)
        assertEquals(3, checks)
    }

    @Test
    fun `an already published notification needs no repair or failure report`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        var checks = 0
        updates.confirm(1, { true }, { checks++; true }, { error("Already delivered") }, { error("Already delivered") })
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(1, checks)
    }

    @Test
    fun `a newer publication cancels the old verification and stale content is never republished`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        val repaired = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        var current = 1L
        updates.confirm(1, { it == current }, { false }, repaired::add, failed::add)
        runCurrent()
        advanceTimeBy(500)
        current = 2
        updates.confirm(2, { it == current }, { false }, repaired::add, failed::add)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        assertTrue(repaired.isEmpty())
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(2L), repaired)

        current = 3
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf(2L), repaired)
        assertTrue(failed.isEmpty())
    }

    @Test
    fun `repair reentry can confirm a newer version without old cleanup losing its job`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        val repaired = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        var current = 1L
        updates.confirm(1, { it == current }, { false }, {
            repaired += it
            current = 2
            updates.confirm(2, { it == current }, { false }, repaired::add, failed::add)
        }, failed::add)
        runCurrent()
        advanceTimeBy(750)
        runCurrent()
        assertEquals(listOf(1L), repaired)

        advanceTimeBy(750)
        runCurrent()
        assertEquals(listOf(1L, 2L), repaired)
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(listOf(1L, 2L, 2L), repaired)
        assertEquals(listOf(2L), failed)
    }

    @Test
    fun `close cancels scheduled content and verification and rejects later work`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        val posted = mutableListOf<String>()
        updates.schedule { posted += "content" }
        updates.confirm(1, { true }, { false }, { posted += "repair" }, { posted += "failure" })
        runCurrent()
        advanceTimeBy(100)

        updates.close()
        updates.schedule { posted += "late content" }
        updates.confirm(2, { true }, { false }, { posted += "late repair" }, { posted += "late failure" })
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(posted.isEmpty())
    }

    @Test
    fun `readback reentry cannot repair or report an obsolete notification`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        val repaired = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        updates.confirm(1, { true }, {
            updates.confirm(2, { true }, { true }, repaired::add, failed::add)
            false
        }, repaired::add, failed::add)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()

        assertTrue(repaired.isEmpty())
        assertTrue(failed.isEmpty())
    }

    @Test
    fun `readback that makes a publication stale cannot repair it before the next check`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        val repaired = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        var current = 1L
        updates.confirm(1, { it == current }, {
            current = 2
            false
        }, repaired::add, failed::add)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()

        assertTrue(repaired.isEmpty())
        assertTrue(failed.isEmpty())
    }

    @Test
    fun `equal confirmation events keep the first verification deadline`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        val repaired = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        updates.confirm(1, { true }, { false }, repaired::add, failed::add)
        runCurrent()
        repeat(7) {
            advanceTimeBy(100)
            updates.confirm(1, { true }, { false }, repaired::add, failed::add)
            runCurrent()
        }
        assertTrue(repaired.isEmpty())

        advanceTimeBy(50)
        runCurrent()

        assertEquals(listOf(1L), repaired)
        assertTrue(failed.isEmpty())
    }

    @Test
    fun `cancel and rearm of the same publication retain the total repair limit`() = runTest {
        val updates = PlaybackNotificationUpdates(backgroundScope)
        val repaired = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        repeat(3) {
            updates.confirm(1, { true }, { false }, repaired::add, failed::add)
            runCurrent()
            advanceTimeBy(750)
            runCurrent()
            updates.cancelConfirmation()
        }

        assertEquals(listOf(1L, 1L), repaired)
        assertEquals(listOf(1L), failed)
        updates.confirm(1, { true }, { false }, repaired::add, failed::add)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(1L, 1L), repaired)
        assertEquals(listOf(1L), failed)
    }

    @Test
    fun `finished publications are not polled again and a different version starts a new check`() = runTest {
        for (published in listOf(true, false)) {
            val updates = PlaybackNotificationUpdates(backgroundScope, maxRepublishes = 0)
            var checks = 0
            val failed = mutableListOf<Long>()
            updates.confirm(1, { true }, { checks++; published }, { error("No repair budget") }, failed::add)
            runCurrent()
            advanceTimeBy(750)
            runCurrent()
            assertEquals(1, checks)
            assertEquals(if (published) emptyList<Long>() else listOf(1L), failed)

            repeat(3) {
                updates.confirm(1, { true }, { checks++; false }, { error("Already finished") }, failed::add)
                advanceTimeBy(750)
                runCurrent()
            }
            assertEquals(1, checks)
            assertEquals(if (published) emptyList<Long>() else listOf(1L), failed)

            updates.confirm(2, { true }, { checks++; true }, { error("Already delivered") }, failed::add)
            runCurrent()
            advanceTimeBy(750)
            runCurrent()
            assertEquals(2, checks)
        }
    }
}
