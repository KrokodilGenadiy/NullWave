package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSuppression
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerProgressTest {
    private val fixture = PlayerFixture()

    @Before
    fun setUp() = fixture.setUp()

    @After
    fun tearDown() = fixture.tearDown()

    @Test
    fun `control can arrive before progress without inventing a zero position`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
            assertNull(vm.snapshot.value.state.progress)
            assertNull(vm.snapshot.value.state.positionAt(1_000))

            val sample = anchor(current.snapshot, positionMs = 4_000)
            progress(sample)

            assertSame(sample, vm.snapshot.value.state.progress)
            assertEquals(4_000L, vm.snapshot.value.state.positionAt(1_000))
            assertTrue(playback.calls.isEmpty())
        }
    }

    @Test
    fun `paused progress arriving before its first control snapshot is retained until control arrives`() = with(fixture) {
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            playback.beforeStateCollection = { gate.await() }
            val current = connected()
            playback.states.value = current
            val sample = anchor(current.snapshot, positionMs = 7_000)
            playback.progresses.value = sample
            val vm = createVm()
            bind(vm)

            assertEquals(PlaybackConnection.Connecting, vm.snapshot.value.state.connection)
            assertNull(vm.snapshot.value.state.progress)
            gate.complete(Unit)
            runCurrent()

            // Paused playback may never emit another sample, so the first one must survive.
            assertSame(sample, vm.snapshot.value.state.progress)
            assertEquals(7_000L, vm.snapshot.value.state.positionAt(5_000))
            assertEquals(1, playback.progressObservations)
        }
    }

    @Test
    fun `progress requires the full session entry activation and position revision`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val matching = anchor(current.snapshot)
            val mismatches = listOf(
                matching.copy(target = matching.target.copy(sessionId = PlaybackSessionId("other"))),
                matching.copy(target = matching.target.copy(entryId = QueueEntryId("duplicate-track-entry"))),
                matching.copy(target = matching.target.copy(activation = 1)),
                matching.copy(positionRevision = 1),
            )
            for (wrong in mismatches) {
                progress(matching)
                assertSame(matching, vm.snapshot.value.state.progress)
                progress(wrong)
                assertNull(vm.snapshot.value.state.progress)
                assertNull(vm.snapshot.value.state.positionAt(500))
            }

            // A repeated track still belongs to a different queue entry/activation.
            val next = connected(entryId = "duplicate-track-entry", activation = 1, positionRevision = 1)
            assertSame(current.snapshot.queue.entries.single().track, next.snapshot.queue.entries.single().track)
            val early = anchor(next.snapshot, positionMs = 8_000)
            progress(early)
            assertNull(vm.snapshot.value.state.progress)
            control(next)
            assertSame(early, vm.snapshot.value.state.progress)
        }
    }

    @Test
    fun `older samples cannot rewind one generation but a new generation may use a lower sample time`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val latest = anchor(current.snapshot, positionMs = 9_000, sampledAtMs = 900)
            progress(latest)
            progress(latest.copy(positionMs = 8_000, sampledAtMs = 899))
            assertSame(latest, vm.snapshot.value.state.progress)

            val next = connected(activation = 1, positionRevision = 1)
            control(next)
            assertNull(vm.snapshot.value.state.progress)
            val restarted = anchor(next.snapshot, positionMs = 1_000, sampledAtMs = 10)
            progress(restarted)
            assertSame(restarted, vm.snapshot.value.state.progress)
        }
    }

    @Test
    fun `null progress and every nonconnected status remove live position but retain display metadata`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val sample = anchor(current.snapshot)
            progress(sample)
            progress(null)
            assertNull(vm.snapshot.value.state.progress)

            val statuses = listOf(
                PlaybackConnection.Disconnected,
                PlaybackConnection.Connecting,
                PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected),
            )
            for (status in statuses) {
                control(current)
                progress(sample.copy(sampledAtMs = sample.sampledAtMs + statuses.indexOf(status)))
                control(status)
                assertNull(vm.snapshot.value.state.liveSnapshot)
                assertNull(vm.snapshot.value.state.progress)
                assertNull(vm.snapshot.value.state.positionAt(5_000))
                assertSame(current.snapshot, vm.snapshot.value.state.displaySnapshot)
            }
        }
    }

    @Test
    fun `engine duration remains unknown or zero and is never replaced by catalog duration`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            assertTrue(track.durationMs!! > 0)
            progress(anchor(current.snapshot, positionMs = 20_000, durationMs = null))
            assertNull(vm.snapshot.value.state.progress!!.durationMs)
            assertEquals(20_000L, vm.snapshot.value.state.positionAt(1_000))

            progress(anchor(current.snapshot, positionMs = 20_000, durationMs = 0, sampledAtMs = 101))
            assertEquals(0L, vm.snapshot.value.state.progress!!.durationMs)
            assertEquals(0L, vm.snapshot.value.state.positionAt(1_000))

            progress(anchor(current.snapshot, positionMs = 20_000, durationMs = 15_000, sampledAtMs = 102))
            assertEquals(15_000L, vm.snapshot.value.state.positionAt(1_000))
        }
    }

    @Test
    fun `pause buffering and suppression stop extrapolation before a stopped progress anchor arrives`() = with(fixture) {
        runTest(dispatcher) {
            val playing = connected(transport = PlaybackTransport(PlaybackPhase.Ready, playRequested = true))
            val vm = readyVm(playing)
            progress(anchor(playing.snapshot, positionMs = 2_000, sampledAtMs = 100, advancing = true))
            assertEquals(2_500L, vm.snapshot.value.state.positionAt(600))

            val stationary = listOf(
                PlaybackTransport(PlaybackPhase.Ready, playRequested = false),
                PlaybackTransport(PlaybackPhase.Buffering, playRequested = true),
                PlaybackTransport(PlaybackPhase.Ready, playRequested = true, suppression = PlaybackSuppression.AudioFocus),
            )
            for ((index, transport) in stationary.withIndex()) {
                control(connected(revision = index + 1L, transport = transport))
                assertEquals(2_000L, vm.snapshot.value.state.positionAt(600))
                assertEquals(transport.playRequested, vm.snapshot.value.state.playRequested)
            }

            control(connected(revision = 4, transport = playing.snapshot.transport))
            assertEquals("Control alone must not reanimate the pre-pause sample", 2_000L, vm.snapshot.value.state.positionAt(600))
            progress(anchor(playing.snapshot, positionMs = 2_200, sampledAtMs = 600, advancing = true))
            assertEquals(2_500L, vm.snapshot.value.state.positionAt(900))
        }
    }

    @Test
    fun `an early advancing anchor is not frozen by the previous paused generation`() = with(fixture) {
        runTest(dispatcher) {
            val paused = connected()
            val vm = readyVm(paused)
            progress(anchor(paused.snapshot, positionMs = 2_000))
            val next = connected(
                activation = 1,
                positionRevision = 1,
                transport = PlaybackTransport(PlaybackPhase.Ready, playRequested = true),
            )
            val early = anchor(next.snapshot, positionMs = 5_000, sampledAtMs = 200, advancing = true)

            progress(early)
            assertSame(paused.snapshot, vm.snapshot.value.state.liveSnapshot)
            assertNull(vm.snapshot.value.state.progress)
            control(next)

            assertSame(early, vm.snapshot.value.state.progress)
            assertTrue(vm.snapshot.value.state.progress!!.advancing)
            assertEquals(5_500L, vm.snapshot.value.state.positionAt(700))
            assertTrue(playback.calls.isEmpty())
        }
    }

    @Test
    fun `reconnected playing control cannot reanimate an anchor retained from before disconnect`() = with(fixture) {
        runTest(dispatcher) {
            val playing = connected(transport = PlaybackTransport(PlaybackPhase.Ready, playRequested = true))
            val vm = readyVm(playing)
            progress(anchor(playing.snapshot, positionMs = 2_000, sampledAtMs = 100, advancing = true))
            assertEquals(2_500L, vm.snapshot.value.state.positionAt(600))

            control(PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected))
            assertNull(vm.snapshot.value.state.progress)
            control(PlaybackConnection.Connecting)
            assertNull(vm.snapshot.value.state.positionAt(600))
            control(playing)

            assertTrue(vm.snapshot.value.state.playRequested)
            assertFalse(vm.snapshot.value.state.progress!!.advancing)
            assertEquals(2_000L, vm.snapshot.value.state.positionAt(600))
            progress(anchor(playing.snapshot, positionMs = 2_400, sampledAtMs = 600, advancing = true))
            assertTrue(vm.snapshot.value.state.progress!!.advancing)
            assertEquals(2_700L, vm.snapshot.value.state.positionAt(900))
            assertTrue(playback.calls.isEmpty())
        }
    }

    @Test
    fun `interpolation is bounded while progress updates preserve snapshot queue and track identity`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected(transport = PlaybackTransport(PlaybackPhase.Ready, playRequested = true))
            val vm = readyVm(current)
            val firstTrack = vm.snapshot.value.state.currentTrack
            progress(anchor(current.snapshot, positionMs = 1_000, sampledAtMs = 100, advancing = true))
            assertEquals(2_000L, vm.snapshot.value.state.positionAt(5_000))

            progress(anchor(current.snapshot, positionMs = 9_900, sampledAtMs = 200, advancing = true))
            assertEquals(10_000L, vm.snapshot.value.state.positionAt(700))
            assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
            assertSame(current.snapshot, vm.snapshot.value.state.lastKnownSnapshot)
            assertSame(current.snapshot.queue, vm.snapshot.value.state.liveSnapshot!!.queue)
            assertSame(firstTrack, vm.snapshot.value.state.currentTrack)

            val state = vm.snapshot.value.state
            advanceTimeBy(5_000)
            runCurrent()
            assertSame("VM must not generate frame or timer state updates itself", state, vm.snapshot.value.state)
            assertTrue(playback.calls.isEmpty())
            assertFalse(vm.snapshot.value.operations[PlayerViewModel.RequestKey].isBusy)
        }
    }
}
