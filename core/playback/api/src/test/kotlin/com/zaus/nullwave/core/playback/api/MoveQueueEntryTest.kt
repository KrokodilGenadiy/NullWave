package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand.MoveQueueEntry
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.result.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveQueueEntryTest {
    private val past = queueEntry("past", "past-track")
    private val active = queueEntry()
    private val a = queueEntry("a", "repeated-track")
    private val b = queueEntry("b", "other-track")
    private val secondA = a.copy(entryId = QueueEntryId("second-a"))
    private val entries = listOf(past, active, a, b, secondA)
    private val queue = PlaybackQueue.create(entries, current = firstToken, revision = 7, repeatMode = RepeatMode.All)
    private val stale = AppResult.Failure(PlaybackCommandError.StaleTarget)

    @Test fun movingUpOrDownInBaseOrderInsertsImmediatelyBeforeTheAnchor() {
        val up = command(secondA, a).resolve(queue)
        val down = command(a, secondA).resolve(queue)

        assertEquals(listOf(past, active, secondA, a, b), up.entries)
        assertEquals(listOf(past, active, b, a, secondA), down.entries)
        for (moved in listOf(up, down)) {
            assertEquals(moved.entries.map { it.entryId }, moved.playOrder)
            assertEquals(queue.revision + 1, moved.revision)
            assertEquals(firstToken, moved.current)
            assertEquals(RepeatMode.All, moved.repeatMode)
        }
        assertEquals(entries, queue.entries)
        assertEquals(entries.map { it.entryId }, queue.playOrder)
        assertReadOnlyList(up.entries, a)
        assertReadOnlyList(up.playOrder, a.entryId)
    }

    @Test fun nullAnchorMovesToTheEndAndRepeatingTheCommandKeepsTheRevision() {
        val toEnd = command(a, null)
        val moved = toEnd.resolve(queue)
        assertEquals(listOf(past, active, b, secondA, a), moved.entries)
        assertSame(moved, toEnd.resolve(moved))
    }

    @Test fun shuffledMoveUsesOccurrenceIdsAndChangesOnlyTraversalOrder() {
        val shuffled = PlaybackQueue.create(
            entries = listOf(secondA, b, past, a, active),
            playOrder = queue.playOrder, current = firstToken, revision = 12,
            repeatMode = RepeatMode.One, shuffleEnabled = true,
        )
        val move = command(secondA, a)
        val moved = move.resolve(shuffled)
        assertSame(shuffled.entries, moved.entries)
        assertEquals(listOf(past, active, secondA, a, b).map { it.entryId }, moved.playOrder)
        assertEquals(queue.playOrder, shuffled.playOrder)
        assertEquals(shuffled.revision + 1, moved.revision)
        assertEquals(shuffled.current, moved.current)
        assertEquals(shuffled.repeatMode, moved.repeatMode)
        assertTrue(moved.shuffleEnabled)
        assertReadOnlyList(moved.playOrder, a.entryId)
        assertSame(moved, move.resolve(moved))
    }

    @Test fun movesBeforeSelfBeforeTheImmediateSuccessorAndFromLastToEndAreNoOps() {
        assertSame(queue, command(b, b).resolve(queue))
        assertSame(queue, command(a, b).resolve(queue))
        assertSame(queue, command(secondA, null).resolve(queue))
    }

    @Test fun missingCurrentAndPastSourceOrAnchorAreStaleEvenWithRepeatAll() {
        val snapshot = PlaybackSnapshot.create(session, queue = queue)
        val removedCopy = a.copy(entryId = QueueEntryId("removed-a"))
        for (invalid in listOf(past, active, removedCopy)) {
            assertEquals(stale, command(invalid, b).resolveQueue(snapshot))
            assertEquals(stale, command(a, invalid).resolveQueue(snapshot))
            assertEquals(stale, command(invalid, invalid).resolveQueue(snapshot))
        }
        assertEquals(stale, command(a, null).resolveQueue(PlaybackSnapshot.create(session)))
    }

    @Test fun aNewSessionRejectsACommandEvenWhenBothIdsAreReused() {
        val restarted = PlaybackSessionId("restarted")
        val live = PlaybackQueue.create(entries, current = firstToken.copy(sessionId = restarted))
        assertEquals(stale, command(secondA, a).resolveQueue(PlaybackSnapshot.create(restarted, queue = live)))
    }

    @Test fun movingUsesTheLiveAnchorAfterAnUnrelatedQueueEdit() {
        val move = command(secondA, b)
        val inserted = queueEntry("new", "new-track")
        val live = PlaybackQueue.create(listOf(past, active, a, inserted, b, secondA), current = firstToken, revision = 8)
        assertEquals(listOf(past, active, a, inserted, secondA, b), move.resolve(live).entries)
    }

    @Test fun capabilityRequiresAtLeastTwoUpcomingOccurrencesInTheLiveTraversal() {
        val capability = setOf(PlaybackCapability.MoveQueueEntry)
        val traversal = entries.reversed()
        fun snapshotWithCurrent(current: QueueEntry) = PlaybackSnapshot.create(
            session,
            queue = PlaybackQueue.create(entries, traversal.map { it.entryId },
                firstToken.copy(entryId = current.entryId), shuffleEnabled = true, repeatMode = RepeatMode.All),
            capabilities = capability,
        )
        assertEquals(capability, snapshotWithCurrent(traversal[2]).capabilities)
        for (current in traversal.takeLast(2)) {
            assertThrows(IllegalArgumentException::class.java) { snapshotWithCurrent(current) }
        }
    }

    private fun command(source: QueueEntry, before: QueueEntry?) =
        MoveQueueEntry(session, source.entryId, before?.entryId)

    private fun MoveQueueEntry.resolve(queue: PlaybackQueue): PlaybackQueue =
        when (val result = resolveQueue(PlaybackSnapshot.create(session, queue = queue))) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> throw AssertionError("Expected a resolved move, got ${result.error}")
        }
}
