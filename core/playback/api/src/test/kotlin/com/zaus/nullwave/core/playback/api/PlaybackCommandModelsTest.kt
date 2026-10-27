package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackCommandModelsTest {
    @Test fun serviceEntryAndProblemIdentitiesRejectBlankValues() {
        val factories = listOf<(String) -> Any>(
            { PlaybackSessionId(it) }, { QueueEntryId(it) }, { PlaybackProblemId(it) },
        )
        for (factory in factories) {
            for (blank in listOf("", " ", "\t\n")) {
                assertThrows(IllegalArgumentException::class.java) { factory(blank) }
            }
        }
    }

    @Test fun negativeActivationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { PlaybackItemToken(session, firstEntryId, -1) }
    }

    @Test fun requestRetainsRepeatedTracksAndCallerSelectedOccurrence() {
        val repeated = TrackId("volume:7")
        val request = PlaybackQueueRequest.create(listOf(repeated, repeated), startIndex = 1)
        assertEquals(listOf(repeated, repeated), request.trackIds)
        assertEquals(1, request.startIndex)
    }

    @Test fun queueRequestCapturesInputListInsteadOfFollowingLaterCatalogChanges() {
        val input = mutableListOf(TrackId("volume:1"), TrackId("volume:2"))
        val request = PlaybackQueueRequest.create(input, startIndex = 1)
        input.reverse()
        input.removeAt(0)
        assertEquals(listOf(TrackId("volume:1"), TrackId("volume:2")), request.trackIds)
        assertReadOnlyList(request.trackIds, TrackId("volume:3"))
    }

    @Test fun emptyReplacementCannotAccidentallyClearCurrentPlayback() {
        assertThrows(IllegalArgumentException::class.java) { PlaybackQueueRequest.create(emptyList(), 0) }
    }

    @Test fun requestRequiresIndexInsideCapturedQueue() {
        for (index in listOf(-1, 1, Int.MAX_VALUE)) {
            assertThrows("startIndex=$index", IllegalArgumentException::class.java) {
                PlaybackQueueRequest.create(listOf(TrackId("volume:1")), index)
            }
        }
    }

    @Test fun requestRejectsNegativeStartPosition() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueueRequest.create(listOf(TrackId("volume:1")), 0, startPositionMs = -1)
        }
    }

    @Test fun seekRejectsNegativePositionButAllowsZero() {
        assertThrows(IllegalArgumentException::class.java) { PlaybackCommand.SeekTo(firstToken, -1) }
        assertEquals(0L, PlaybackCommand.SeekTo(firstToken, 0).positionMs)
    }

    @Test fun retryCapturesSpecificOccurrenceAndProblemIdentity() {
        val id = PlaybackProblemId("failure-3")
        val retry = PlaybackCommand.Retry(firstToken, id)
        assertEquals(firstToken, retry.target)
        assertEquals(id, retry.problemId)
    }

    @Test fun resolvedResourceUsesSharedTrackWithoutDuplicatingItsMetadata() {
        val track = mediaTrack()
        val resource = ResolvedPlaybackTrack(track, "content://media/external/audio/media/1")
        assertSame(track, resource.track)
        assertEquals("content://media/external/audio/media/1", resource.resourceUri)
    }

    @Test fun resolvedResourceRejectsMissingAddress() {
        for (blank in listOf("", " ", "\n\t")) {
            assertThrows(IllegalArgumentException::class.java) { ResolvedPlaybackTrack(mediaTrack(), blank) }
        }
    }
}
