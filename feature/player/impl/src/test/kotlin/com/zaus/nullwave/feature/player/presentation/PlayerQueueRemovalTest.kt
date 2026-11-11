package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
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
class PlayerQueueRemovalTest {
    private val fixture = PlayerFixture()
    private val session = PlaybackSessionId("original")
    private val first = QueueEntry(QueueEntryId("first"), fixture.track)
    private val duplicate = first.copy(entryId = QueueEntryId("second"))
    private val third = QueueEntry(QueueEntryId("third"), fixture.track.copy(id = TrackId("primary:8"), title = "Echo"))
    private val remove = PlayerIntent.RemoveQueueEntry(session, duplicate.entryId)
    private val command = PlaybackCommand.RemoveQueueEntry(session, duplicate.entryId)

    @Before fun setUp() = fixture.setUp()
    @After fun tearDown() = fixture.tearDown()

    @Test
    fun `remove forwards the captured duplicate after shuffle and waits for the engine to publish the edit`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(queue())
            val shuffled = queue(
                entries = listOf(duplicate, first, third), order = listOf(first, third, duplicate), revision = 1,
            )
            control(shuffled)
            val sample = anchor(shuffled.snapshot, positionMs = 12_000)
            progress(sample)

            send(vm, remove)
            assertEquals(command, playback.calls.single().command)
            assertSame(shuffled.snapshot, vm.snapshot.value.state.displaySnapshot)
            respond(AppResult.Success(Unit))
            assertSame(shuffled.snapshot, vm.snapshot.value.state.displaySnapshot)
            assertSame(sample, vm.snapshot.value.state.progress)

            val edited = queue(entries = listOf(first, third), revision = 2)
            control(edited)
            assertSame(edited.snapshot, vm.snapshot.value.state.displaySnapshot)
            assertSame(sample, vm.snapshot.value.state.progress)
        }
    }

    @Test
    fun `only the live removal capability authorizes a request`() = with(fixture) {
        runTest(dispatcher) {
            val shown = queue()
            val vm = readyVm(shown)
            control(PlaybackConnection.Disconnected)
            assertSame(shown.snapshot, vm.snapshot.value.state.displaySnapshot)
            send(vm, remove)
            control(PlaybackConnection.Connecting)
            send(vm, remove)
            control(queue(capabilities = setOf(PlaybackCapability.SelectQueueEntry)))
            send(vm, remove)

            assertTrue(playback.calls.isEmpty())
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
            control(shown)
            send(vm, remove)
            assertEquals(command, playback.calls.single().command)
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `busy transport and removal drop conflicting requests in both directions`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(queue(capabilities = setOf(PlaybackCapability.Next, PlaybackCapability.RemoveQueueEntry)))
            send(vm, PlayerIntent.Next)
            send(vm, remove)
            respond(AppResult.Success(Unit))
            assertEquals(listOf(PlaybackCommand.Next), playback.calls.map { it.command })

            send(vm, remove)
            val accepted = playback.calls.last()
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].isBusy)
            send(vm, remove)
            send(vm, PlayerIntent.Next)
            assertFalse(accepted.waiterCancelled)
            respond(AppResult.Success(Unit))
            assertEquals(listOf(PlaybackCommand.Next, command), playback.calls.map { it.command })
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
        }
    }

    @Test
    fun `locally stale rows preserve the existing notice and never reach the controller`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(queue())
            send(vm, remove)
            respond(AppResult.Failure(PlaybackCommandError.CommandUnavailable))
            val notice = checkNotNull(vm.snapshot.value.state.commandNotice)

            for (live in listOf(
                queue(current = duplicate),
                queue(order = listOf(duplicate, first, third)),
                queue(entries = listOf(first, third)),
                queue(sessionId = PlaybackSessionId("restarted")),
            )) {
                control(live)
                send(vm, remove)
                assertEquals(1, playback.calls.size)
                assertSame(notice, vm.snapshot.value.state.commandNotice)
                assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
            }
        }
    }

    @Test
    fun `late stale rejection keeps the newly active copy and its progress without replay`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(queue())
            send(vm, remove)
            val advanced = queue(current = duplicate)
            control(advanced)
            val sample = anchor(advanced.snapshot, positionMs = 800)
            progress(sample)
            respond(AppResult.Failure(PlaybackCommandError.StaleTarget))

            val state = vm.snapshot.value.state
            assertSame(advanced.snapshot, state.liveSnapshot)
            assertSame(sample, state.progress)
            assertEquals(UiText.Resource(R.string.player_command_unavailable), state.commandNotice?.message)
            assertEquals(listOf(command), playback.calls.map { it.command })
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
        }
    }

    private fun queue(
        entries: List<QueueEntry> = listOf(first, duplicate, third),
        order: List<QueueEntry> = entries,
        current: QueueEntry = first,
        sessionId: PlaybackSessionId = session,
        capabilities: Set<PlaybackCapability> = setOf(PlaybackCapability.RemoveQueueEntry),
        revision: Long = 0,
    ) = PlaybackConnection.Connected(PlaybackSnapshot.create(
        sessionId = sessionId, revision = revision, transport = PlaybackTransport(PlaybackPhase.Ready),
        queue = PlaybackQueue.create(
            entries = entries, playOrder = order.map { it.entryId },
            current = PlaybackItemToken(sessionId, current.entryId, activation = 0),
            revision = revision, shuffleEnabled = order != entries,
        ),
        capabilities = capabilities,
    ))
}
