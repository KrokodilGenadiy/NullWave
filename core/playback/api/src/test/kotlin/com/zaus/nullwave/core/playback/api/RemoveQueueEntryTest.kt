package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand.RemoveQueueEntry
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.result.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoveQueueEntryTest {
    private val first = queueEntry()
    private val copy = first.copy(entryId = QueueEntryId("second-copy"))
    private val other = queueEntry("other", "volume:2")
    private val remove = RemoveQueueEntry(session, copy.entryId)
    private val stale = AppResult.Failure(PlaybackCommandError.StaleTarget)
    private val entries = listOf(first, copy, other)

    @Test fun reorderedUpcomingCopyResolvesAtItsNewBaseIndex() {
        val before = PlaybackQueue.create(entries, current = firstToken)
        val reordered = PlaybackQueue.create(
            entries = listOf(copy, other, first),
            playOrder = listOf(other.entryId, first.entryId, copy.entryId),
            current = firstToken.copy(activation = 5), revision = 2, shuffleEnabled = true,
        )
        assertEquals(AppResult.Success(1), remove.resolveBaseIndex(PlaybackSnapshot.create(session, queue = before)))
        assertEquals(AppResult.Success(0), remove.resolveBaseIndex(PlaybackSnapshot.create(session, queue = reordered)))
    }

    @Test fun currentAndPastOccurrencesBecomeStaleEvenWithRepeatAll() {
        val order = listOf(copy.entryId, first.entryId, other.entryId)
        for (current in listOf(copy.entryId, first.entryId)) {
            val queue = PlaybackQueue.create(
                entries, playOrder = order, current = firstToken.copy(entryId = current),
                repeatMode = RepeatMode.All, shuffleEnabled = true,
            )
            assertEquals(stale, remove.resolveBaseIndex(PlaybackSnapshot.create(session, queue = queue)))
        }
    }

    @Test fun missingCopyIsStaleEvenIfTheTrackStillExists() {
        val remaining = PlaybackQueue.create(listOf(first, other), current = firstToken)
        assertEquals(stale, remove.resolveBaseIndex(PlaybackSnapshot.create(session, queue = remaining)))
        assertEquals(stale, remove.resolveBaseIndex(PlaybackSnapshot.create(session)))
    }

    @Test fun restartedSessionRejectsAnOtherwiseUpcomingOccurrenceWithTheSameId() {
        val restarted = PlaybackSessionId("restarted")
        val queue = PlaybackQueue.create(entries, current = firstToken.copy(sessionId = restarted))
        assertEquals(stale, remove.resolveBaseIndex(PlaybackSnapshot.create(restarted, queue = queue)))
    }

    @Test fun removalCapabilityRequiresASuccessorInPlaybackOrder() {
        val order = listOf(copy.entryId, other.entryId, first.entryId)
        val capability = setOf(PlaybackCapability.RemoveQueueEntry)
        fun snapshot(current: QueueEntryId): PlaybackSnapshot {
            val queue = PlaybackQueue.create(
                entries, playOrder = order, current = firstToken.copy(entryId = current),
                shuffleEnabled = true, repeatMode = RepeatMode.All,
            )
            return PlaybackSnapshot.create(session, queue = queue, capabilities = capability)
        }
        assertEquals(capability, snapshot(other.entryId).capabilities)
        assertThrows(IllegalArgumentException::class.java) { snapshot(first.entryId) }
    }
}
