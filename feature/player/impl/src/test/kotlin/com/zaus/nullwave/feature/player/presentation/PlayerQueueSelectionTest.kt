package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
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

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerQueueSelectionTest {
    private val fixture = PlayerFixture()

    @Before fun setUp() = fixture.setUp()
    @After fun tearDown() = fixture.tearDown()

    @Test
    fun `selection keeps the captured occurrence when shuffle and the current item change`() = with(fixture) {
        runTest(dispatcher) {
            val shown = connected(capabilities = setOf(PlaybackCapability.SelectQueueEntry))
            val vm = readyVm(shown)
            val original = shown.snapshot.queue.entries.single()
            val select = PlayerIntent.SelectQueueEntry(shown.snapshot.sessionId, original.entryId)

            val otherCopy = original.copy(entryId = QueueEntryId("another-copy"))
            val token = checkNotNull(shown.snapshot.queue.current)
            val shuffled = PlaybackSnapshot.create(
                sessionId = token.sessionId,
                revision = 1,
                queue = PlaybackQueue.create(
                    entries = listOf(otherCopy, original),
                    playOrder = listOf(original.entryId, otherCopy.entryId),
                    current = token.copy(entryId = otherCopy.entryId, activation = 1),
                    revision = 1,
                    shuffleEnabled = true,
                ),
                transport = shown.snapshot.transport,
                capabilities = shown.snapshot.capabilities,
            )
            control(PlaybackConnection.Connected(shuffled))
            send(vm, select)
            assertEquals(PlaybackCommand.SelectQueueEntry(token.sessionId, original.entryId), playback.calls.single().command)
            assertSame(shuffled, vm.snapshot.value.state.liveSnapshot)
            respond(AppResult.Success(Unit))
            assertSame(shuffled, vm.snapshot.value.state.liveSnapshot)
            assertFalse(vm.snapshot.value.state.playRequested)
        }
    }

    @Test
    fun `cached state and other transport capabilities cannot authorize selection`() = with(fixture) {
        runTest(dispatcher) {
            val live = connected()
            val vm = readyVm(live)
            val select = PlayerIntent.SelectQueueEntry(live.snapshot.sessionId, live.snapshot.queue.entries.single().entryId)
            for (connection in listOf(
                PlaybackConnection.Disconnected,
                PlaybackConnection.Connecting,
                connected(capabilities = setOf(PlaybackCapability.Next, PlaybackCapability.SeekTo)),
                PlaybackConnection.Connected(PlaybackSnapshot.create(live.snapshot.sessionId)),
            )) {
                control(connection)
                send(vm, select)
                assertTrue(playback.calls.isEmpty())
                assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
            }
            assertNull(vm.snapshot.value.state.commandNotice)
        }
    }

    @Test
    fun `stale rows are ignored before admission even if the entry ID or track still matches`() = with(fixture) {
        runTest(dispatcher) {
            val old = connected()
            val vm = readyVm(old)
            val select = PlayerIntent.SelectQueueEntry(old.snapshot.sessionId, old.snapshot.queue.entries.single().entryId)
            send(vm, PlayerIntent.Next)
            respond(AppResult.Failure(PlaybackCommandError.StartNotAllowed))
            val notice = checkNotNull(vm.snapshot.value.state.commandNotice)

            control(connected(entryId = "replacement-copy", revision = 1))
            send(vm, select)
            control(connected(sessionId = "new-session"))
            send(vm, select)

            assertEquals(listOf(PlaybackCommand.Next), playback.calls.map { it.command })
            assertSame(notice, vm.snapshot.value.state.commandNotice)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
        }
    }

    @Test
    fun `selection shares the transport slot and busy taps are never replayed`() = with(fixture) {
        runTest(dispatcher) {
            val live = connected()
            val vm = readyVm(live)
            val entryId = live.snapshot.queue.entries.single().entryId
            val select = PlayerIntent.SelectQueueEntry(live.snapshot.sessionId, entryId)
            val sample = anchor(live.snapshot, positionMs = 4_000)
            progress(sample)
            send(vm, PlayerIntent.Next)
            send(vm, select)
            respond(AppResult.Success(Unit))
            assertEquals(listOf(PlaybackCommand.Next), playback.calls.map { it.command })

            send(vm, select)
            val accepted = playback.calls.last()
            assertEquals(PlaybackCommand.SelectQueueEntry(live.snapshot.sessionId, entryId), accepted.command)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].isBusy)
            send(vm, select)
            send(vm, PlayerIntent.Next)
            send(vm, PlayerIntent.Reconnect)
            assertFalse(accepted.waiterCancelled)
            respond(AppResult.Success(Unit))
            assertEquals(2, playback.calls.size)
            assertTrue(playback.reconnectCalls.isEmpty())
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
            assertSame(sample, vm.snapshot.value.state.progress)
        }
    }

    @Test
    fun `late stale rejection keeps the newer snapshot and progress without retrying`() = with(fixture) {
        runTest(dispatcher) {
            val old = connected()
            val vm = readyVm(old)
            val select = PlayerIntent.SelectQueueEntry(old.snapshot.sessionId, old.snapshot.queue.entries.single().entryId)
            send(vm, select)
            val replacement = connected(sessionId = "new-session")
            control(replacement)
            val sample = anchor(replacement.snapshot)
            progress(sample)
            respond(AppResult.Failure(PlaybackCommandError.StaleTarget))

            assertSame(replacement.snapshot, vm.snapshot.value.state.liveSnapshot)
            assertSame(sample, vm.snapshot.value.state.progress)
            assertEquals(UiText.Resource(R.string.player_command_unavailable), vm.snapshot.value.state.commandNotice?.message)
            assertEquals(1, playback.calls.size)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
        }
    }
}
