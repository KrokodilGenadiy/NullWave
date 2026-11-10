package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand.SelectQueueEntry
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackSuppression
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.result.AppResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectQueueEntryTest {
    private val first = queueEntry()
    private val duplicate = first.copy(entryId = QueueEntryId("second-occurrence"))
    private val other = queueEntry("other-entry", "volume:2")
    private val select = SelectQueueEntry(session, duplicate.entryId)

    @Test fun duplicateResolvesAtItsBaseIndexWhenShuffleAndTransportChange() {
        val queue = PlaybackQueue.create(
            entries = listOf(first, other, duplicate),
            playOrder = listOf(duplicate.entryId, first.entryId, other.entryId),
            current = firstToken, shuffleEnabled = true,
        )
        for (transport in listOf(
            PlaybackTransport(PlaybackPhase.Ready, playRequested = false),
            PlaybackTransport(PlaybackPhase.Ready, playRequested = true),
            PlaybackTransport(PlaybackPhase.Ready, playRequested = true, suppression = PlaybackSuppression.AudioFocus),
        )) {
            val snapshot = PlaybackSnapshot.create(
                session, queue = queue, transport = transport,
                capabilities = setOf(PlaybackCapability.SelectQueueEntry),
            )
            assertEquals(AppResult.Success(2), select.resolveBaseIndex(snapshot))
        }
    }

    @Test fun survivingEntryUsesItsNewIndexEvenIfItHasBecomeCurrent() {
        val before = PlaybackQueue.create(entries = listOf(first, duplicate, other), current = firstToken)
        val after = PlaybackQueue.create(
            entries = listOf(duplicate, other),
            current = firstToken.copy(entryId = duplicate.entryId, activation = 2), revision = 1,
        )
        assertEquals(AppResult.Success(1), select.resolveBaseIndex(PlaybackSnapshot.create(session, queue = before)))
        assertEquals(AppResult.Success(0), select.resolveBaseIndex(PlaybackSnapshot.create(session, queue = after)))
    }

    @Test fun differentSessionRejectsEvenAnIdenticalEntryId() {
        val newSession = PlaybackSessionId("restarted")
        val queue = PlaybackQueue.create(
            entries = listOf(first, duplicate), current = firstToken.copy(sessionId = newSession),
        )
        assertEquals(
            AppResult.Failure(PlaybackCommandError.StaleTarget),
            select.resolveBaseIndex(PlaybackSnapshot.create(newSession, queue = queue)),
        )
    }

    @Test fun removedOccurrenceIsStaleEvenWhenAnotherCopyOfTheTrackRemains() {
        val remaining = PlaybackQueue.create(entries = listOf(first), current = firstToken)
        for (queue in listOf(remaining, PlaybackQueue.create())) {
            assertEquals(
                AppResult.Failure(PlaybackCommandError.StaleTarget),
                select.resolveBaseIndex(PlaybackSnapshot.create(session, queue = queue)),
            )
        }
    }
}
