package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Commands own one idle admission slot; their replies do not manufacture engine facts. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerCommandTest {
    private val fixture = PlayerFixture()

    @Before fun setUp() = fixture.setUp()
    @After fun tearDown() = fixture.tearDown()

    @Test
    fun `desired play and pause values are dispatched without optimistic transport changes`() = with(fixture) {
        runTest(dispatcher) {
            val initial = connected()
            val vm = readyVm(initial)

            send(vm, PlayerIntent.SetPlayRequested(true))

            assertEquals(PlaybackCommand.SetPlayRequested(true), playback.calls.single().command)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].isBusy)
            assertFalse(vm.snapshot.value.state.playRequested)
            assertSame(initial.snapshot, vm.snapshot.value.state.liveSnapshot)
            respond(AppResult.Success(Unit))
            assertFalse(vm.snapshot.value.state.playRequested)

            val buffering = connected(transport = PlaybackTransport(PlaybackPhase.Buffering, playRequested = true))
            control(buffering)
            assertTrue(vm.snapshot.value.state.playRequested)
            assertFalse(buffering.snapshot.transport.isPlaying)
            send(vm, PlayerIntent.SetPlayRequested(false))

            assertEquals(PlaybackCommand.SetPlayRequested(false), playback.calls.last().command)
            assertSame(buffering.snapshot, vm.snapshot.value.state.liveSnapshot)
            assertTrue(vm.snapshot.value.state.playRequested)
            respond(AppResult.Success(Unit))
            assertTrue(vm.snapshot.value.state.playRequested)
        }
    }

    @Test
    fun `transport requires a live connection a current item and its capability`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm()
            val unavailable = listOf(
                PlaybackConnection.Disconnected,
                PlaybackConnection.Connecting,
                PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected),
                PlaybackConnection.Connected(PlaybackSnapshot.create(PlaybackSessionId("empty"))),
                connected(capabilities = emptySet()),
            )
            for (connection in unavailable) {
                control(connection)
                send(vm, PlayerIntent.SetPlayRequested(true))
                send(vm, PlayerIntent.Next)
                send(vm, PlayerIntent.Previous)
            }
            assertTrue(playback.calls.isEmpty())
            assertNull(vm.snapshot.value.state.commandNotice)

            control(connected(capabilities = setOf(PlaybackCapability.Next)))
            send(vm, PlayerIntent.SetPlayRequested(true))
            send(vm, PlayerIntent.Previous)
            send(vm, PlayerIntent.Next)
            assertEquals(PlaybackCommand.Next, playback.calls.single().command)
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `one idle slot rejects busy commands and reconnect instead of accumulating or cancelling work`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm()
            send(vm, PlayerIntent.Next)
            val accepted = playback.calls.single()

            repeat(3) { send(vm, PlayerIntent.Next) }
            send(vm, PlayerIntent.Previous)
            send(vm, PlayerIntent.SetPlayRequested(false))
            send(vm, PlayerIntent.SetShuffle(true))
            send(vm, PlayerIntent.SetRepeat(RepeatMode.All))
            send(vm, PlayerIntent.Reconnect)

            assertEquals(1, playback.calls.size)
            assertTrue(playback.reconnectCalls.isEmpty())
            assertFalse(accepted.waiterCancelled)
            assertFalse(accepted.reply.isCancelled)
            assertFalse(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
            respond(AppResult.Success(Unit))
            assertEquals(1, playback.calls.size)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)

            send(vm, PlayerIntent.Next)
            assertEquals(listOf(PlaybackCommand.Next, PlaybackCommand.Next), playback.calls.map { it.command })
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `reconnect holds the same idle slot even if observation becomes connected before its reply`() = with(fixture) {
        runTest(dispatcher) {
            val vm = createVm()
            bind(vm)
            control(PlaybackConnection.Unavailable(PlaybackConnectionError.StartNotAllowed))
            send(vm, PlayerIntent.Reconnect)
            send(vm, PlayerIntent.Reconnect)
            assertEquals(1, playback.reconnectCalls.size)

            control(connected())
            send(vm, PlayerIntent.Next)
            send(vm, PlayerIntent.Previous)
            send(vm, PlayerIntent.SetPlayRequested(true))
            send(vm, PlayerIntent.SetShuffle(true))
            send(vm, PlayerIntent.SetRepeat(RepeatMode.One))
            assertTrue(playback.calls.isEmpty())
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].isBusy)

            reconnectResult(AppResult.Success(Unit))
            assertTrue(playback.calls.isEmpty())
            send(vm, PlayerIntent.SetPlayRequested(true))
            assertEquals(PlaybackCommand.SetPlayRequested(true), playback.calls.single().command)
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `successful reconnect without observation never invents a connected state or audio command`() = with(fixture) {
        runTest(dispatcher) {
            val vm = createVm()
            bind(vm)
            val unavailable = PlaybackConnection.Unavailable(PlaybackConnectionError.StartNotAllowed)
            control(unavailable)
            send(vm, PlayerIntent.Reconnect)
            reconnectResult(AppResult.Success(Unit))

            assertEquals(unavailable, vm.snapshot.value.state.connection)
            assertNull(vm.snapshot.value.state.progress)
            assertNull(vm.snapshot.value.state.lastKnownSnapshot)
            assertTrue(playback.calls.isEmpty())
            assertEquals(1, playback.reconnectCalls.size)
        }
    }

    @Test
    fun `retry requires the exact live problem token identity and capability`() = with(fixture) {
        runTest(dispatcher) {
            val current = problemSnapshot()
            val vm = readyVm(current)
            val problem = checkNotNull(current.snapshot.transport.problem)
            val target = checkNotNull(current.snapshot.queue.current)
            val stale = listOf(
                PlayerIntent.RetryProblem(target.copy(sessionId = PlaybackSessionId("old-session")), problem.id),
                PlayerIntent.RetryProblem(target.copy(entryId = QueueEntryId("old-entry")), problem.id),
                PlayerIntent.RetryProblem(target.copy(activation = 1), problem.id),
                PlayerIntent.RetryProblem(target, PlaybackProblemId("old-problem")),
            )
            for (intent in stale) send(vm, intent)
            assertTrue(playback.calls.isEmpty())

            control(connected(transport = current.snapshot.transport, capabilities = emptySet()))
            send(vm, PlayerIntent.RetryProblem(target, problem.id))
            assertTrue(playback.calls.isEmpty())

            control(current)
            send(vm, PlayerIntent.RetryProblem(target, problem.id))
            assertEquals(PlaybackCommand.Retry(target, problem.id), playback.calls.single().command)
            respond(AppResult.Success(Unit))
            assertSame(problem, vm.snapshot.value.state.visibleProblem)
        }
    }

    @Test
    fun `retry uses the current activation when an active problem has no explicit target`() = with(fixture) {
        runTest(dispatcher) {
            val current = problemSnapshot(targeted = false)
            val vm = readyVm(current)
            val target = checkNotNull(current.snapshot.queue.current)
            val problem = checkNotNull(current.snapshot.transport.problem)

            send(vm, PlayerIntent.RetryProblem(target, problem.id))

            assertEquals(PlaybackCommand.Retry(target, problem.id), playback.calls.single().command)
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `expected command failures expose localized notices without replacing live playback`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            val vm = readyVm(current)
            val errors = listOf(
                PlaybackCommandError.ConnectionUnavailable,
                PlaybackCommandError.StartNotAllowed,
                PlaybackCommandError.CommandUnavailable,
                PlaybackCommandError.StaleTarget,
                PlaybackCommandError.InvalidRequest,
            ) + PlaybackSourceError.entries.map { PlaybackCommandError.SourceFailure(track.id, it) }
            for (error in errors) {
                send(vm, PlayerIntent.Next)
                respond(AppResult.Failure(error, IllegalStateException("private diagnostic")))

                assertTrue("Expected resource message for $error", vm.snapshot.value.state.commandNotice!!.message is UiText.Resource)
                assertSame(current.snapshot, vm.snapshot.value.state.liveSnapshot)
                assertSame(current.snapshot.queue, vm.snapshot.value.state.lastKnownSnapshot!!.queue)
                send(vm, PlayerIntent.DismissCommandNotice(vm.snapshot.value.state.commandNotice!!.id))
                assertNull(vm.snapshot.value.state.commandNotice)
            }
        }
    }

    @Test
    fun `connection failures are localized without dispatching an audio command`() = with(fixture) {
        runTest(dispatcher) {
            val vm = createVm()
            bind(vm)
            for (error in PlaybackConnectionError.entries) {
                send(vm, PlayerIntent.Reconnect)
                reconnectResult(AppResult.Failure(error, IllegalStateException("private connect diagnostic")))
                assertTrue(vm.snapshot.value.state.commandNotice!!.message is UiText.Resource)
                assertEquals(PlaybackConnection.Disconnected, vm.snapshot.value.state.connection)
            }
            assertEquals(PlaybackConnectionError.entries.size, playback.reconnectCalls.size)
            assertTrue(playback.calls.isEmpty())
        }
    }

    @Test
    fun `new accepted request clears the previous notice and superseded remains silent`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm()
            send(vm, PlayerIntent.Next)
            respond(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
            assertNotNull(vm.snapshot.value.state.commandNotice)

            send(vm, PlayerIntent.Next)
            assertNull(vm.snapshot.value.state.commandNotice)
            respond(AppResult.Failure(PlaybackCommandError.Superseded))
            assertNull(vm.snapshot.value.state.commandNotice)
        }
    }

    @Test
    fun `old dismiss callback cannot erase a new identical command error`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm()
            send(vm, PlayerIntent.Next)
            respond(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
            val first = checkNotNull(vm.snapshot.value.state.commandNotice)
            send(vm, PlayerIntent.Next)
            respond(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
            val second = checkNotNull(vm.snapshot.value.state.commandNotice)

            assertEquals(first.message, second.message)
            assertNotSame(first.id, second.id)
            send(vm, PlayerIntent.DismissCommandNotice(first.id))
            assertSame(second, vm.snapshot.value.state.commandNotice)
            send(vm, PlayerIntent.DismissCommandNotice(second.id))
            assertNull(vm.snapshot.value.state.commandNotice)
        }
    }

    @Test
    fun `healthy snapshots preserve command refusal and dismissing it does not acknowledge engine problems`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm()
            send(vm, PlayerIntent.Next)
            respond(AppResult.Failure(PlaybackCommandError.StartNotAllowed))
            val notice = checkNotNull(vm.snapshot.value.state.commandNotice)
            control(connected(activation = 1, revision = 1))
            assertSame(notice, vm.snapshot.value.state.commandNotice)

            val failed = problemSnapshot(id = "new-engine-problem", activation = 1)
            control(failed)
            assertSame(notice, vm.snapshot.value.state.commandNotice)
            assertNull(vm.snapshot.value.state.dismissedProblem)
            assertNull(vm.snapshot.value.state.visibleProblem)
            assertSame(notice.message, vm.snapshot.value.state.noticeMessage)
            send(vm, PlayerIntent.DismissCommandNotice(notice.id))
            assertSame(failed.snapshot.transport.problem, vm.snapshot.value.state.visibleProblem)
            assertNull(vm.snapshot.value.state.dismissedProblem)
        }
    }

    @Test
    fun `controller cancellation is silent and releases the idle slot for a later command`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm()
            send(vm, PlayerIntent.Next)
            playback.calls.single().reply.completeExceptionally(CancellationException("cancelled waiter"))
            runCurrent()

            assertNull(vm.snapshot.value.state.commandNotice)
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit)
            send(vm, PlayerIntent.SetPlayRequested(true))
            assertEquals(2, playback.calls.size)
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `programming failures propagate without being converted into command notices`() = with(fixture) {
        val original = DiagnosticFailure("Broken playback adapter", "controller")
        val thrown = assertThrows(DiagnosticFailure::class.java) {
            runTest(dispatcher) {
                val vm = readyVm()
                send(vm, PlayerIntent.Next)
                playback.calls.single().reply.completeExceptionally(original)
                runCurrent()
                assertNull(vm.snapshot.value.state.commandNotice)
            }
        }
        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === original })
    }

    private class DiagnosticFailure(message: String, val diagnostic: String) : IllegalStateException(message)
}
