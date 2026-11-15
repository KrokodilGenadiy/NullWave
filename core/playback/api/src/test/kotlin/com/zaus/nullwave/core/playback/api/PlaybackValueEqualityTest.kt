package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueOrder
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Collection owners need structural equality so equal observations do not imply a new value. */
class PlaybackValueEqualityTest {
    @Test fun equalQueuesDoNotDependOnInputCollectionIdentity() {
        val first = currentQueue()
        val equal = currentQueue(firstToken.copy())
        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertNotEquals(first, PlaybackQueue.create(entries = listOf(queueEntry()), current = firstToken, revision = 1))
        assertNotEquals(first, currentQueue(firstToken.copy(activation = 2)))
    }

    @Test fun equalSnapshotsUseCollectionContentsButIncludeControlAndPositionRevisions() {
        fun snapshot(revision: Long = 0, positionRevision: Long = 0) = PlaybackSnapshot.create(
            sessionId = session, revision = revision, queue = currentQueue(),
            positionRevision = positionRevision, capabilities = linkedSetOf(PlaybackCapability.Next),
        )
        val first = snapshot()
        val equal = snapshot()
        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertNotEquals(first, snapshot(revision = 1))
        assertNotEquals(first, snapshot(positionRevision = 1))
        assertNotEquals(first, PlaybackSnapshot.create(sessionId = session, queue = currentQueue()))
    }

    @Test fun queueRequestEqualityRetainsSelectionAndPlaybackIntent() {
        val ids = listOf(TrackId("volume:1"), TrackId("volume:2"))
        val first = PlaybackQueueRequest.create(ids, startIndex = 0)
        val equal = PlaybackQueueRequest.create(ids.toMutableList(), startIndex = 0)
        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertNotEquals(first, PlaybackQueueRequest.create(ids.reversed(), startIndex = 0))
        assertNotEquals(first, PlaybackQueueRequest.create(ids, startIndex = 1))
        assertNotEquals(first, PlaybackQueueRequest.create(ids, startIndex = 0, startPositionMs = 1))
        assertNotEquals(first, PlaybackQueueRequest.create(ids, startIndex = 0, playRequested = false))
        assertEquals(PlaybackQueueOrder.KeepShuffleMode, first.order)
        assertNotEquals(first, PlaybackQueueRequest.create(ids, startIndex = 0, order = PlaybackQueueOrder.Shuffled))
    }
}
