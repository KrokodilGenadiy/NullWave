package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.player.impl.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** A seek keeps the gesture's full target; only observed engine facts authorize and confirm it. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSeekCommandTest {
    private val fixture = PlayerFixture()

    @Before fun setUp() = fixture.setUp()
    @After fun tearDown() = fixture.tearDown()

    @Test
    fun `previous uses its own capability and never invents a new current item`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected(capabilities = setOf(PlaybackCapability.Previous))
            val vm = readyVm(current)
            send(vm, PlayerIntent.Next)
            send(vm, PlayerIntent.SetPlayRequested(true))
            send(vm, PlayerIntent.Previous)

            assertEquals(PlaybackCommand.Previous, playback.calls.single().command)
            assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].isBusy)
            respond(AppResult.Success(Unit))
            assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
        }
    }

    @Test
    fun `seek clamps to engine duration and its reply does not move displayed progress`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected(capabilities = setOf(PlaybackCapability.SeekTo))
            val vm = readyVm(current)
            val target = checkNotNull(current.snapshot.queue.current)
            val sample = anchor(current.snapshot, positionMs = 1_000, durationMs = 10_000)
            progress(sample)
            assertTrue(track.durationMs != sample.durationMs)

            for ((requested, expected) in listOf(0L to 0L, 5_000L to 5_000L, 10_000L to 10_000L, Long.MAX_VALUE to 10_000L)) {
                send(vm, PlayerIntent.SeekTo(target, requested))
                val command = playback.calls.last().command as PlaybackCommand.SeekTo
                assertSame(target, command.target)
                assertEquals(expected, command.positionMs)
                assertSame(sample, vm.snapshot.value.state.progress)
                respond(AppResult.Success(Unit))
                assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
                assertSame(sample, vm.snapshot.value.state.progress)
            }
            assertEquals(4, playback.calls.size)
        }
    }

    @Test
    fun `seek requires a live current item and seek capability even with cached metadata`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val target = checkNotNull(current.snapshot.queue.current)
            progress(anchor(current.snapshot))
            val unavailable = listOf(
                PlaybackConnection.Disconnected,
                PlaybackConnection.Connecting,
                PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected),
                PlaybackConnection.Connected(PlaybackSnapshot.create(PlaybackSessionId("empty"))),
                connected(capabilities = setOf(PlaybackCapability.Previous)),
            )
            for (connection in unavailable) {
                control(connection)
                send(vm, PlayerIntent.SeekTo(target, 2_000))
            }
            assertTrue(playback.calls.isEmpty())
            assertNull(vm.snapshot.value.state.commandNotice)
        }
    }

    @Test
    fun `seek waits for known positive engine duration instead of using catalog metadata`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val target = checkNotNull(current.snapshot.queue.current)
            send(vm, PlayerIntent.SeekTo(target, 2_000))
            progress(anchor(current.snapshot, durationMs = null))
            send(vm, PlayerIntent.SeekTo(target, 2_000))
            progress(anchor(current.snapshot, durationMs = 0))
            send(vm, PlayerIntent.SeekTo(target, 2_000))
            assertTrue(playback.calls.isEmpty())

            progress(anchor(current.snapshot, durationMs = 3_000))
            send(vm, PlayerIntent.SeekTo(target, 4_000))
            assertEquals(PlaybackCommand.SeekTo(target, 3_000), playback.calls.single().command)
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `seek waits for matching position revision and resumes with a fresh anchor`() = with(fixture) {
        runTest(dispatcher) {
            val original = connected()
            val vm = readyVm(original)
            val target = checkNotNull(original.snapshot.queue.current)
            progress(anchor(original.snapshot))
            send(vm, PlayerIntent.SeekTo(target, 2_000))
            respond(AppResult.Success(Unit))
            val moved = connected(positionRevision = 1, revision = 1)
            control(moved)
            assertNull(vm.snapshot.value.state.progress)
            send(vm, PlayerIntent.SeekTo(target, 3_000))
            assertEquals(1, playback.calls.size)

            progress(anchor(moved.snapshot))
            send(vm, PlayerIntent.SeekTo(target, 3_000))
            assertEquals(listOf(PlaybackCommand.SeekTo(target, 2_000), PlaybackCommand.SeekTo(target, 3_000)),
                playback.calls.map { it.command })
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `stale token components and negative positions are ignored without clearing a notice`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val target = checkNotNull(current.snapshot.queue.current)
            progress(anchor(current.snapshot))
            send(vm, PlayerIntent.Next)
            respond(AppResult.Failure(PlaybackCommandError.StartNotAllowed))
            val notice = checkNotNull(vm.snapshot.value.state.commandNotice)

            val staleTargets = listOf(
                target.copy(sessionId = PlaybackSessionId("old-session")),
                target.copy(entryId = QueueEntryId("other-occurrence")),
                target.copy(activation = target.activation + 1),
            )
            for (stale in staleTargets) send(vm, PlayerIntent.SeekTo(stale, 2_000))
            send(vm, PlayerIntent.SeekTo(target, -1))
            assertEquals(1, playback.calls.size)
            assertSame(notice, vm.snapshot.value.state.commandNotice)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)

            send(vm, PlayerIntent.SeekTo(target, 2_000))
            assertEquals(PlaybackCommand.SeekTo(target, 2_000), playback.calls.last().command)
            assertNull(vm.snapshot.value.state.commandNotice)
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `returning to the same track occurrence does not revive an old gesture target`() = with(fixture) {
        runTest(dispatcher) {
            val original = connected()
            val vm = readyVm(original)
            val captured = checkNotNull(original.snapshot.queue.current)
            progress(anchor(original.snapshot))
            control(connected(entryId = "other-entry", activation = 1, revision = 1))
            val returned = connected(activation = 2, revision = 2)
            control(returned)
            progress(anchor(returned.snapshot))
            assertSame(original.snapshot.queue.entries.single().track, vm.snapshot.value.state.currentTrack)

            send(vm, PlayerIntent.SeekTo(captured, 2_000))
            assertTrue(playback.calls.isEmpty())
            val fresh = checkNotNull(returned.snapshot.queue.current)
            send(vm, PlayerIntent.SeekTo(fresh, 2_000))
            assertEquals(PlaybackCommand.SeekTo(fresh, 2_000), playback.calls.single().command)
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `seek shares the idle slot with previous next and reconnect without replaying busy inputs`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val target = checkNotNull(current.snapshot.queue.current)
            progress(anchor(current.snapshot))
            send(vm, PlayerIntent.SeekTo(target, 2_000))
            val accepted = playback.calls.single()
            send(vm, PlayerIntent.Previous)
            send(vm, PlayerIntent.Next)
            send(vm, PlayerIntent.SeekTo(target, 3_000))
            send(vm, PlayerIntent.Reconnect)
            assertEquals(1, playback.calls.size)
            assertTrue(playback.reconnectCalls.isEmpty())
            assertFalse(accepted.waiterCancelled)
            respond(AppResult.Success(Unit))
            assertEquals(1, playback.calls.size)

            send(vm, PlayerIntent.Reconnect)
            send(vm, PlayerIntent.Previous)
            send(vm, PlayerIntent.SeekTo(target, 4_000))
            assertEquals(1, playback.calls.size)
            reconnectResult(AppResult.Success(Unit))
            assertEquals(1, playback.calls.size)
            send(vm, PlayerIntent.Previous)
            assertEquals(PlaybackCommand.Previous, playback.calls.last().command)
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `backend stale rejection after a track change reports failure without retargeting seek`() = with(fixture) {
        runTest(dispatcher) {
            val original = connected()
            val vm = readyVm(original)
            val target = checkNotNull(original.snapshot.queue.current)
            progress(anchor(original.snapshot))
            send(vm, PlayerIntent.SeekTo(target, 2_000))
            val replacement = connected(activation = 1, revision = 1)
            control(replacement)
            val sample = anchor(replacement.snapshot, positionMs = 500)
            progress(sample)
            respond(AppResult.Failure(PlaybackCommandError.StaleTarget))

            assertEquals(listOf(PlaybackCommand.SeekTo(target, 2_000)), playback.calls.map { it.command })
            assertSame(replacement.snapshot, vm.snapshot.value.state.liveSnapshot)
            assertSame(sample, vm.snapshot.value.state.progress)
            assertEquals(UiText.Resource(R.string.player_command_unavailable), vm.snapshot.value.state.commandNotice?.message)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
        }
    }

    @Test
    fun `clearing during seek cancels only the UI waiter and never replays a gesture`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val target = checkNotNull(current.snapshot.queue.current)
            progress(anchor(current.snapshot))
            val ui = vm.uiApi()
            send(vm, PlayerIntent.SeekTo(target, 2_000))
            val accepted = playback.calls.single()
            store.clear()
            runCurrent()

            assertTrue(accepted.waiterCancelled)
            assertFalse(accepted.reply.isCancelled)
            assertEquals(0, playback.activeStateCollectors)
            assertEquals(0, playback.activeProgressCollectors)
            val clearedState = vm.snapshot.value.state
            respond(AppResult.Success(Unit))
            ui.onIntent(PlayerIntent.SeekTo(target, 3_000))
            ui.onIntent(PlayerIntent.Previous)
            runCurrent()
            assertSame(clearedState, vm.snapshot.value.state)
            assertEquals(listOf(PlaybackCommand.SeekTo(target, 2_000)), playback.calls.map { it.command })
        }
    }
}
