package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTest {
    @Test fun advancingPositionUsesMonotonicElapsedTimeAndSpeed() {
        val progress = progress().copy(speed = 0.5f)
        assertEquals(1_200L, progress.positionAt(nowMs = 1_400))
    }

    @Test fun pausedAnchorDoesNotAdvanceAndKeepsExactLargeIntegerPosition() {
        val exact = 9_007_199_254_740_993L
        val progress = progress().copy(positionMs = exact, advancing = false)
        assertEquals(exact, progress.positionAt(nowMs = Long.MAX_VALUE))
    }

    @Test fun advancingAnchorAboveDoublePrecisionAddsOnlyTheElapsedIncrement() {
        val exact = 9_007_199_254_740_993L
        val progress = progress().copy(positionMs = exact)
        assertEquals(exact + 1, progress.positionAt(nowMs = 1_001))
        assertEquals(exact + 3, progress.positionAt(nowMs = 1_003))
    }

    @Test fun advancingNearLongMaximumUsesRemainingHeadroomWithoutOverflow() {
        val progress = progress().copy(positionMs = Long.MAX_VALUE - 2)
        assertEquals(Long.MAX_VALUE - 1, progress.positionAt(nowMs = 1_001))
        assertEquals(Long.MAX_VALUE, progress.positionAt(nowMs = 1_002))
        assertEquals(Long.MAX_VALUE, progress.positionAt(nowMs = 1_003))
    }

    @Test fun clockEarlierThanAnchorDoesNotRunPositionBackwards() {
        assertEquals(1_000L, progress().positionAt(nowMs = 999))
    }

    @Test fun defaultHorizonPreventsIndefiniteExtrapolationWithoutNewSamples() {
        assertEquals(2_000L, progress().positionAt(nowMs = 60_000))
    }

    @Test fun customHorizonIsAppliedBeforeSpeed() {
        val progress = progress().copy(speed = 2f)
        assertEquals(1_500L, progress.positionAt(nowMs = 60_000, maxExtrapolationMs = 250))
    }

    @Test fun zeroHorizonPreservesExactAnchorPosition() {
        val exact = 9_007_199_254_740_993L
        assertEquals(exact, progress().copy(positionMs = exact).positionAt(Long.MAX_VALUE, 0))
    }

    @Test fun knownDurationCapsRenderedPositionIncludingShortenedDuration() {
        val progress = progress().copy(durationMs = 1_250)
        assertEquals(1_250L, progress.positionAt(2_000))
        assertEquals(500L, progress.copy(durationMs = 500).positionAt(1_000))
    }

    @Test fun zeroDurationProducesZeroWithoutDivisionOrFakePercentage() {
        assertEquals(0L, progress().copy(durationMs = 0).positionAt(2_000))
    }

    @Test fun unknownDurationAndBufferedPositionRemainUnknown() {
        val progress = progress()
        assertEquals(null, progress.durationMs)
        assertEquals(null, progress.bufferedPositionMs)
        assertEquals(1_500L, progress.positionAt(1_500))
    }

    @Test fun bufferedPositionMayLagAfterSeekAndDoesNotClampPlaybackPosition() {
        val progress = progress().copy(bufferedPositionMs = 10)
        assertEquals(1_200L, progress.positionAt(1_200))
    }

    @Test fun largeElapsedTimePositionAndSpeedSaturateWithoutOverflow() {
        val progress = progress().copy(
            positionMs = Long.MAX_VALUE - 1,
            sampledAtMs = 0,
            speed = Float.MAX_VALUE,
        )
        assertEquals(Long.MAX_VALUE, progress.positionAt(Long.MAX_VALUE))
    }

    @Test fun nonfiniteOrNonpositiveSpeedIsRejected() {
        for (speed in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows("speed=$speed", IllegalArgumentException::class.java) { progress().copy(speed = speed) }
        }
    }

    @Test fun negativeAnchorValuesAreRejected() {
        for (invalid in listOf<(PlaybackProgress) -> PlaybackProgress>(
            { it.copy(positionRevision = -1) },
            { it.copy(positionMs = -1) },
            { it.copy(durationMs = -1) },
            { it.copy(bufferedPositionMs = -1) },
            { it.copy(sampledAtMs = -1) },
        )) {
            assertThrows(IllegalArgumentException::class.java) { invalid(progress()) }
        }
    }

    @Test fun negativeClockOrHorizonIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { progress().positionAt(-1) }
        assertThrows(IllegalArgumentException::class.java) { progress().positionAt(2_000, -1) }
    }

    @Test fun anchorMatchesOnlyCurrentSessionOccurrenceAndPositionRevision() {
        val snapshot = PlaybackSnapshot.create(sessionId = session, queue = currentQueue(), positionRevision = 3)
        val current = progress()
        assertTrue(current.matches(snapshot))
        val staleTargets = listOf(
            firstToken.copy(sessionId = PlaybackSessionId("previous-service")),
            firstToken.copy(entryId = QueueEntryId("another-entry")),
            firstToken.copy(activation = 0),
        )
        for (target in staleTargets) assertFalse(current.copy(target = target).matches(snapshot))
        assertFalse(current.copy(positionRevision = 2).matches(snapshot))
        assertFalse(current.copy(positionRevision = 4).matches(snapshot))
    }

    @Test fun oldAnchorDoesNotMatchAfterSameEntryBecomesCurrentAgain() {
        val afterReturn = firstToken.copy(activation = 2)
        val snapshot = PlaybackSnapshot.create(sessionId = session, queue = currentQueue(afterReturn), positionRevision = 3)
        assertFalse(progress().matches(snapshot))
        assertTrue(progress().copy(target = afterReturn).matches(snapshot))
    }

    @Test fun noAnchorMatchesAnEmptyQueue() {
        assertFalse(progress().matches(PlaybackSnapshot.create(sessionId = session, positionRevision = 3)))
    }

    private fun progress() = PlaybackProgress(
        target = firstToken,
        positionRevision = 3,
        positionMs = 1_000,
        durationMs = null,
        bufferedPositionMs = null,
        sampledAtMs = 1_000,
        advancing = true,
    )
}
