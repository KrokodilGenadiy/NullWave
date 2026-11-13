package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.mvi.testing.send
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.player.impl.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Shuffle/repeat are explicit session commands; only engine observations change displayed modes. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerModeCommandTest {
    private val fixture = PlayerFixture()

    @Before fun setUp() = fixture.setUp()
    @After fun tearDown() = fixture.tearDown()

    @Test
    fun `shuffle forwards explicit values including unchanged and repeated requests`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val sample = anchor(current.snapshot)
            progress(sample)
            val desiredValues = listOf(false, true, true, false)

            for (enabled in desiredValues) {
                send(vm, PlayerIntent.SetShuffle(enabled))
                assertEquals(PlaybackCommand.SetShuffle(enabled), playback.calls.last().command)
                assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
                assertSame(sample, vm.snapshot.value.state.progress)
                respond(AppResult.Success(Unit))
                assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
                assertSame(sample, vm.snapshot.value.state.progress)
            }
            assertEquals(desiredValues.map { PlaybackCommand.SetShuffle(it) }, playback.calls.map { it.command })
        }
    }

    @Test
    fun `repeat forwards every explicit mode without cycling or deduplicating`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val sample = anchor(current.snapshot)
            progress(sample)
            val modes = RepeatMode.entries.reversed() + RepeatMode.Off
            for (mode in modes) {
                send(vm, PlayerIntent.SetRepeat(mode))
                assertEquals(PlaybackCommand.SetRepeat(mode), playback.calls.last().command)
                assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
                assertSame(sample, vm.snapshot.value.state.progress)
                respond(AppResult.Success(Unit))
                assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
                assertSame(sample, vm.snapshot.value.state.progress)
            }
            assertEquals(modes.map { PlaybackCommand.SetRepeat(it) }, playback.calls.map { it.command })
        }
    }

    @Test
    fun `shuffle and repeat capabilities are independent`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(connected(capabilities = setOf(PlaybackCapability.SetShuffle)))
            send(vm, PlayerIntent.SetRepeat(RepeatMode.All))
            assertTrue(playback.calls.isEmpty())
            send(vm, PlayerIntent.SetShuffle(true))
            assertEquals(PlaybackCommand.SetShuffle(true), playback.calls.single().command)
            respond(AppResult.Success(Unit))

            control(connected(capabilities = setOf(PlaybackCapability.SetRepeat)))
            send(vm, PlayerIntent.SetShuffle(false))
            assertEquals(1, playback.calls.size)
            send(vm, PlayerIntent.SetRepeat(RepeatMode.One))
            assertEquals(PlaybackCommand.SetRepeat(RepeatMode.One), playback.calls.last().command)
            respond(AppResult.Success(Unit))
            assertEquals(2, playback.calls.size)
        }
    }

    @Test
    fun `modes require live capabilities even when the cached snapshot offers both`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            for (connection in listOf(
                PlaybackConnection.Disconnected,
                PlaybackConnection.Connecting,
                PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected),
            )) {
                control(connection)
                assertSame(current.snapshot, vm.snapshot.value.state.lastKnownSnapshot)
                send(vm, PlayerIntent.SetShuffle(true))
                send(vm, PlayerIntent.SetRepeat(RepeatMode.All))
            }
            control(connected(capabilities = emptySet()))
            send(vm, PlayerIntent.SetShuffle(true))
            send(vm, PlayerIntent.SetRepeat(RepeatMode.All))
            assertTrue(playback.calls.isEmpty())
            assertNull(vm.snapshot.value.state.commandNotice)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
        }
    }

    @Test
    fun `an empty live session accepts modes when their capabilities are offered`() = with(fixture) {
        runTest(dispatcher) {
            val empty = PlaybackSnapshot.create(
                sessionId = PlaybackSessionId("empty"),
                capabilities = setOf(PlaybackCapability.SetShuffle, PlaybackCapability.SetRepeat),
            )
            val vm = readyVm(PlaybackConnection.Connected(empty))
            send(vm, PlayerIntent.SetShuffle(true))
            assertEquals(PlaybackCommand.SetShuffle(true), playback.calls.single().command)
            respond(AppResult.Success(Unit))
            send(vm, PlayerIntent.SetRepeat(RepeatMode.All))
            assertEquals(PlaybackCommand.SetRepeat(RepeatMode.All), playback.calls.last().command)
            respond(AppResult.Success(Unit))

            assertEquals(2, playback.calls.size)
            assertSame(empty, vm.snapshot.value.state.liveSnapshot)
            assertNull(vm.snapshot.value.state.currentTrack)
            assertNull(vm.snapshot.value.state.progress)
        }
    }

    @Test
    fun `either mode holds the shared idle slot without cancellation or replay`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val target = checkNotNull(current.snapshot.queue.current)
            progress(anchor(current.snapshot))
            val requests = listOf(
                PlayerIntent.SetShuffle(true) to PlaybackCommand.SetShuffle(true),
                PlayerIntent.SetRepeat(RepeatMode.All) to PlaybackCommand.SetRepeat(RepeatMode.All),
            )
            for ((index, request) in requests.withIndex()) {
                send(vm, request.first)
                val accepted = playback.calls.last()
                assertEquals(request.second, accepted.command)
                send(vm, PlayerIntent.SetShuffle(false))
                send(vm, PlayerIntent.SetRepeat(RepeatMode.One))
                send(vm, PlayerIntent.SetPlayRequested(true))
                send(vm, PlayerIntent.Next)
                send(vm, PlayerIntent.Previous)
                send(vm, PlayerIntent.SeekTo(target, 2_000))
                send(vm, PlayerIntent.Reconnect)
                assertEquals(index + 1, playback.calls.size)
                assertTrue(playback.reconnectCalls.isEmpty())
                assertFalse(accepted.waiterCancelled)
                assertFalse(accepted.reply.isCancelled)
                assertFalse(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
                respond(AppResult.Success(Unit))
                assertEquals(index + 1, playback.calls.size)
                assertTrue(playback.reconnectCalls.isEmpty())
                assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
            }
        }
    }

    @Test
    fun `engine mode and play order observations win before and after a command reply`() = with(fixture) {
        runTest(dispatcher) {
            val seed = connected().snapshot
            val entries = seed.queue.entries + QueueEntry(QueueEntryId("second"), track)
            val initial = PlaybackSnapshot.create(
                sessionId = seed.sessionId,
                queue = PlaybackQueue.create(entries = entries, current = seed.queue.current),
                transport = seed.transport,
                capabilities = seed.capabilities,
            )
            val vm = readyVm(PlaybackConnection.Connected(initial))
            val sample = anchor(initial)
            progress(sample)
            send(vm, PlayerIntent.SetShuffle(true))
            assertSame(initial, vm.snapshot.value.state.liveSnapshot)
            val observed = PlaybackSnapshot.create(
                sessionId = initial.sessionId,
                revision = 1,
                queue = PlaybackQueue.create(
                    entries = entries,
                    playOrder = entries.reversed().map { it.entryId },
                    current = initial.queue.current,
                    revision = 1,
                    shuffleEnabled = true,
                    repeatMode = RepeatMode.All,
                ),
                transport = initial.transport,
                capabilities = initial.capabilities,
            )
            control(PlaybackConnection.Connected(observed))
            assertSame(observed, vm.snapshot.value.state.liveSnapshot)
            assertSame(sample, vm.snapshot.value.state.progress)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].isBusy)
            respond(AppResult.Success(Unit))
            assertSame(observed, vm.snapshot.value.state.liveSnapshot)
            assertSame(sample, vm.snapshot.value.state.progress)

            // External controls can publish a different mode after our command has completed.
            val later = PlaybackSnapshot.create(
                sessionId = initial.sessionId,
                revision = 2,
                queue = PlaybackQueue.create(
                    entries = entries, current = initial.queue.current, revision = 2,
                    shuffleEnabled = false, repeatMode = RepeatMode.One,
                ),
                transport = initial.transport,
                capabilities = initial.capabilities,
            )
            control(PlaybackConnection.Connected(later))
            assertSame(later, vm.snapshot.value.state.liveSnapshot)
            assertSame(later, vm.snapshot.value.state.lastKnownSnapshot)
            assertSame(sample, vm.snapshot.value.state.progress)
            assertEquals(1, playback.calls.size)
        }
    }

    @Test
    fun `mode refusal uses the shared notice and a new accepted mode clears it`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            send(vm, PlayerIntent.SetRepeat(RepeatMode.One))
            respond(AppResult.Failure(PlaybackCommandError.CommandUnavailable))
            assertEquals(UiText.Resource(R.string.player_command_unavailable), vm.snapshot.value.state.commandNotice?.message)
            assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)

            send(vm, PlayerIntent.SetShuffle(true))
            assertNull(vm.snapshot.value.state.commandNotice)
            respond(AppResult.Failure(PlaybackCommandError.Superseded))
            assertNull(vm.snapshot.value.state.commandNotice)
            assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
        }
    }
}
