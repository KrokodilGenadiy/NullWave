package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackProblem
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackSuppression
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSnapshotTest {
    @Test fun playingRequiresReadyRequestedAndUnsuppressedTransport() {
        for (phase in PlaybackPhase.entries) {
            for (requested in listOf(false, true)) {
                for (suppression in PlaybackSuppression.entries) {
                    val transport = PlaybackTransport(phase, requested, suppression)
                    assertEquals(
                        "$phase / requested=$requested / $suppression",
                        phase == PlaybackPhase.Ready && requested && suppression == PlaybackSuppression.None,
                        transport.isPlaying,
                    )
                }
            }
        }
    }

    @Test fun fatalProblemKeepsIdleTransportAndNeverClaimsPlaying() {
        val failure = problem()
        assertFalse(PlaybackTransport(PlaybackPhase.Idle, playRequested = true, problem = failure).isPlaying)
        for (phase in PlaybackPhase.entries.filterNot { it == PlaybackPhase.Idle }) {
            assertThrows(IllegalArgumentException::class.java) {
                PlaybackTransport(phase, problem = failure)
            }
        }
    }

    @Test fun snapshotRejectsCurrentItemFromDifferentServiceSession() {
        val foreign = firstToken.copy(sessionId = PlaybackSessionId("other-service"))
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackSnapshot.create(sessionId = session, queue = currentQueue(foreign))
        }
    }

    @Test fun problemForEarlierActivationCannotDescribeCurrentItem() {
        val stale = firstToken.copy(activation = 0)
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackSnapshot.create(
                sessionId = session, queue = currentQueue(),
                transport = PlaybackTransport(problem = problem(stale)),
            )
        }
    }

    @Test fun currentItemProblemPreservesQueueAndCanExposeRetry() {
        val snapshot = PlaybackSnapshot.create(
            sessionId = session, queue = currentQueue(),
            transport = PlaybackTransport(problem = problem(firstToken)),
            capabilities = setOf(PlaybackCapability.Retry, PlaybackCapability.Next),
        )
        assertEquals(firstToken, snapshot.queue.current)
        assertEquals(1, snapshot.queue.entries.size)
        assertFalse(snapshot.transport.isPlaying)
        assertTrue(PlaybackCapability.Retry in snapshot.capabilities)
    }

    @Test fun sessionProblemDoesNotRequireInventingAnItemToken() {
        val snapshot = PlaybackSnapshot.create(
            sessionId = session, queue = currentQueue(),
            transport = PlaybackTransport(problem = problem()),
        )
        assertEquals(PlaybackProblemCode.OutputUnavailable, snapshot.transport.problem?.code)
    }

    @Test fun retryCapabilityRequiresAnActualProblem() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackSnapshot.create(sessionId = session, queue = currentQueue(), capabilities = setOf(PlaybackCapability.Retry))
        }
    }

    @Test fun emptySnapshotOnlyAllowsIdleNonplayingProblemFreeTransport() {
        for (invalid in listOf(
            PlaybackTransport(PlaybackPhase.Buffering),
            PlaybackTransport(PlaybackPhase.Ready),
            PlaybackTransport(PlaybackPhase.Ended),
            PlaybackTransport(playRequested = true),
            PlaybackTransport(suppression = PlaybackSuppression.AudioFocus),
            PlaybackTransport(problem = problem()),
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                PlaybackSnapshot.create(sessionId = session, transport = invalid)
            }
        }
    }

    @Test fun emptySnapshotCannotAdvertiseItemCommands() {
        for (capability in listOf(
            PlaybackCapability.SetPlayRequested, PlaybackCapability.SeekTo,
            PlaybackCapability.Next, PlaybackCapability.Previous, PlaybackCapability.Retry,
        )) {
            assertThrows("Empty queue / $capability", IllegalArgumentException::class.java) {
                PlaybackSnapshot.create(sessionId = session, capabilities = setOf(capability))
            }
        }
    }

    @Test fun emptySnapshotMayAdvertiseQueueAndPreferenceCommands() {
        val allowed = setOf(
            PlaybackCapability.ReplaceQueue, PlaybackCapability.SetRepeat,
            PlaybackCapability.SetShuffle, PlaybackCapability.StopAndClear,
        )
        assertEquals(allowed, PlaybackSnapshot.create(sessionId = session, capabilities = allowed).capabilities)
    }

    @Test fun capabilityPublicationIsIndependentFromCallerMutableSet() {
        val input = mutableSetOf(PlaybackCapability.ReplaceQueue)
        val snapshot = PlaybackSnapshot.create(sessionId = session, capabilities = input)
        input.clear()
        assertEquals(setOf(PlaybackCapability.ReplaceQueue), snapshot.capabilities)
        assertReadOnlySet(snapshot.capabilities, PlaybackCapability.SetShuffle)
    }

    @Test fun negativeSnapshotRevisionsAreRejectedIndependently() {
        assertThrows(IllegalArgumentException::class.java) { PlaybackSnapshot.create(sessionId = session, revision = -1) }
        assertThrows(IllegalArgumentException::class.java) { PlaybackSnapshot.create(sessionId = session, positionRevision = -1) }
    }

    private fun problem(target: PlaybackItemToken? = null) = PlaybackProblem(
        PlaybackProblemId("problem-1"), PlaybackProblemCode.OutputUnavailable, target,
    )
}
