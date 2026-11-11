package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.player.impl.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerQueueMoveTest {
    private val fixture = PlayerFixture()
    private val session = PlaybackSessionId("original")
    private val active = QueueEntry(QueueEntryId("current"), fixture.track)
    private val source = active.copy(entryId = QueueEntryId("source"))
    private val destination = active.copy(entryId = QueueEntryId("destination"))
    private val other = active.copy(entryId = QueueEntryId("other"))
    private val last = active.copy(entryId = QueueEntryId("last"))
    private val entries = listOf(active, source, other, destination, last)
    private val move = PlayerIntent.MoveQueueEntry(session, source.entryId, destination.entryId)
    private val command = PlaybackCommand.MoveQueueEntry(session, source.entryId, destination.entryId)

    @Before fun setUp() = fixture.setUp()
    @After fun tearDown() = fixture.tearDown()

    @Test fun `move keeps the captured copies after shuffle and waits for the confirmed order`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(queue())
            val shuffled = queue(order = listOf(active, destination, other, source, last), revision = 1)
            control(shuffled)
            val sample = anchor(shuffled.snapshot)
            progress(sample)

            send(vm, move)
            assertEquals(command, playback.calls.single().command)
            assertSame(shuffled.snapshot, vm.snapshot.value.state.displaySnapshot)
            respond(AppResult.Success(Unit))
            assertSame(shuffled.snapshot, vm.snapshot.value.state.displaySnapshot)

            val confirmed = queue(order = listOf(active, source, destination, other, last), revision = 2)
            control(confirmed)
            assertSame(confirmed.snapshot, vm.snapshot.value.state.displaySnapshot)
            assertSame(sample, vm.snapshot.value.state.progress)
        }
    }

    @Test fun `only a live move capability admits a move to the end`() = with(fixture) {
        runTest(dispatcher) {
            val shown = queue()
            val vm = readyVm(shown)
            val toEnd = move.copy(beforeEntryId = null)
            control(PlaybackConnection.Disconnected)
            assertSame(shown.snapshot, vm.snapshot.value.state.displaySnapshot)
            send(vm, toEnd)
            control(PlaybackConnection.Connecting)
            send(vm, toEnd)
            control(queue(capabilities = setOf(PlaybackCapability.SelectQueueEntry, PlaybackCapability.RemoveQueueEntry)))
            send(vm, toEnd)
            assertTrue(playback.calls.isEmpty())
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)

            control(shown)
            send(vm, toEnd)
            assertEquals(command.copy(beforeEntryId = null), playback.calls.single().command)
            respond(AppResult.Success(Unit))
        }
    }

    @Test fun `busy transport and moves drop competing requests without replay`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(queue(capabilities = setOf(PlaybackCapability.Next, PlaybackCapability.MoveQueueEntry)))
            send(vm, PlayerIntent.Next)
            send(vm, move)
            respond(AppResult.Success(Unit))
            assertEquals(listOf(PlaybackCommand.Next), playback.calls.map { it.command })

            send(vm, move)
            val accepted = playback.calls.last()
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].isBusy)
            send(vm, move.copy(beforeEntryId = null))
            send(vm, PlayerIntent.Next)
            respond(AppResult.Success(Unit))
            assertFalse(accepted.waiterCancelled)
            assertEquals(listOf(PlaybackCommand.Next, command), playback.calls.map { it.command })
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
        }
    }

    @Test fun `stale sources destinations and sessions preserve the notice and skip submission`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(queue())
            send(vm, move)
            respond(AppResult.Failure(PlaybackCommandError.CommandUnavailable))
            val notice = checkNotNull(vm.snapshot.value.state.commandNotice)

            for (live in listOf(
                queue(base = entries - source),
                queue(base = entries - destination),
                queue(current = source),
                queue(current = destination, order = listOf(active, destination, source, other, last)),
                queue(order = listOf(source, active, other, destination, last)),
                queue(order = listOf(destination, active, other, source, last)),
                queue(sessionId = PlaybackSessionId("new-session")),
            )) {
                control(live)
                send(vm, move)
                assertEquals(1, playback.calls.size)
                assertSame(notice, vm.snapshot.value.state.commandNotice)
                assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
            }
        }
    }

    @Test fun `late stale rejection keeps the live queue and progress without retrying`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(queue())
            send(vm, move)
            val edited = queue(base = entries - destination, revision = 1)
            control(edited)
            val sample = anchor(edited.snapshot, positionMs = 12_000)
            progress(sample)
            respond(AppResult.Failure(PlaybackCommandError.StaleTarget))

            val state = vm.snapshot.value.state
            assertSame(edited.snapshot, state.liveSnapshot)
            assertSame(sample, state.progress)
            assertEquals(UiText.Resource(R.string.player_command_unavailable), state.commandNotice?.message)
            assertEquals(listOf(command), playback.calls.map { it.command })
        }
    }

    private fun queue(
        base: List<QueueEntry> = entries,
        order: List<QueueEntry> = base,
        current: QueueEntry = active,
        sessionId: PlaybackSessionId = session,
        capabilities: Set<PlaybackCapability> = setOf(PlaybackCapability.MoveQueueEntry),
        revision: Long = 0,
    ) = PlaybackConnection.Connected(PlaybackSnapshot.create(
        sessionId = sessionId, revision = revision, capabilities = capabilities,
        queue = PlaybackQueue.create(
            entries = base, playOrder = order.map { it.entryId },
            current = PlaybackItemToken(sessionId, current.entryId, activation = 0),
            revision = revision, shuffleEnabled = order != base,
        ),
    ))
}
