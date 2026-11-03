package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSuppression
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure rendering projection: controller observation and stale sample filtering have separate tests. */
class PlayerMiniProgressTest {
    private val fixture = PlayerFixture()

    @Test
    fun `fraction uses engine duration instead of catalog duration`() = with(fixture) {
        val connection = connected()
        val state = PlayerState(
            connection = connection,
            progress = anchor(connection.snapshot, positionMs = 2_500, durationMs = 10_000),
        )
        assertTrue(track.durationMs != state.progress!!.durationMs)
        assertEquals(0.25f, state.progressFractionAt(100)!!, 0.000001f)
    }

    @Test
    fun `missing anchor and unknown or zero duration do not invent a progress strip`() = with(fixture) {
        val connection = connected()
        val state = PlayerState(connection = connection)
        assertNull(state.progressFractionAt(100))
        for (duration in listOf(null, 0L)) {
            val sample = anchor(connection.snapshot, positionMs = 2_500, durationMs = duration)
            assertNull(state.copy(progress = sample).progressFractionAt(100))
        }
    }

    @Test
    fun `nonmatching generation cannot supply a fraction even with a known duration`() = with(fixture) {
        val connection = connected()
        val sample = anchor(connection.snapshot)
        val state = PlayerState(connection = connection)
        val mismatches = listOf(
            sample.copy(target = sample.target.copy(sessionId = PlaybackSessionId("older-session"))),
            sample.copy(target = sample.target.copy(entryId = QueueEntryId("another-entry"))),
            sample.copy(target = sample.target.copy(activation = 1)),
            sample.copy(positionRevision = 1),
        )
        for (wrong in mismatches) assertNull(state.copy(progress = wrong).progressFractionAt(100))
    }

    @Test
    fun `cached metadata never keeps live progress after a connection loss`() = with(fixture) {
        val connection = connected()
        val state = PlayerState(
            connection = connection,
            lastKnownSnapshot = connection.snapshot,
            progress = anchor(connection.snapshot),
        )
        for (status in listOf(
            PlaybackConnection.Disconnected,
            PlaybackConnection.Connecting,
            PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected),
        )) assertNull(state.copy(connection = status).progressFractionAt(100))
    }

    @Test
    fun `fractions remain finite and clamped for Long sized engine values`() = with(fixture) {
        val connection = connected()
        val state = PlayerState(connection = connection)
        val halfway = state.copy(progress = anchor(
            connection.snapshot,
            positionMs = Long.MAX_VALUE / 2,
            durationMs = Long.MAX_VALUE,
        )).progressFractionAt(100)!!
        assertTrue(halfway.isFinite())
        assertEquals(0.5f, halfway, 0.000001f)
        val beyondDuration = state.copy(progress = anchor(
            connection.snapshot,
            positionMs = Long.MAX_VALUE,
            durationMs = 10_000,
        )).progressFractionAt(100)!!
        assertEquals(1f, beyondDuration, 0f)
        assertEquals(0f, state.copy(progress = anchor(connection.snapshot, positionMs = 0)).progressFractionAt(100)!!, 0f)
    }

    @Test
    fun `fraction uses bounded interpolation only while transport actually advances`() = with(fixture) {
        val playing = connected(transport = PlaybackTransport(PlaybackPhase.Ready, playRequested = true))
        val sample = anchor(playing.snapshot, positionMs = 2_000, durationMs = 10_000, sampledAtMs = 100, advancing = true)
        val state = PlayerState(connection = playing, progress = sample)
        assertEquals(0.25f, state.progressFractionAt(600)!!, 0.000001f)
        assertEquals(0.3f, state.progressFractionAt(Long.MAX_VALUE)!!, 0.000001f)

        for (transport in listOf(
            PlaybackTransport(PlaybackPhase.Ready, playRequested = false),
            PlaybackTransport(PlaybackPhase.Buffering, playRequested = true),
            PlaybackTransport(PlaybackPhase.Ready, playRequested = true, suppression = PlaybackSuppression.AudioFocus),
        )) {
            val stationary = state.copy(connection = connected(transport = transport))
            assertEquals(0.2f, stationary.progressFractionAt(600)!!, 0.000001f)
        }
        assertEquals(0.2f, state.copy(progress = sample.copy(advancing = false)).progressFractionAt(600)!!, 0.000001f)
    }
}
