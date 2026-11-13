package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.mvi.testing.assertHandlesEveryIntent
import com.zaus.nullwave.core.mvi.testing.bind
import com.zaus.nullwave.core.mvi.testing.send
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.result.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerLifecycleTest {
    private val fixture = PlayerFixture()

    @Before
    fun setUp() = fixture.setUp()

    @After
    fun tearDown() = fixture.tearDown()

    @Test
    fun `every Player intent has a handler`() = with(fixture) {
        runTest(dispatcher) {
            val session = PlaybackSessionId("player-session")
            val entry = QueueEntryId("entry")
            val target = PlaybackItemToken(session, entry, 0)
            val problem = PlaybackProblemId("problem")
            assertHandlesEveryIntent(listOf(
                PlayerIntent.SetPlayRequested(true),
                PlayerIntent.Next,
                PlayerIntent.Previous,
                PlayerIntent.SelectQueueEntry(session, entry),
                PlayerIntent.RemoveQueueEntry(session, entry),
                PlayerIntent.MoveQueueEntry(PlaybackQueue.create(), entry, null),
                PlayerIntent.SeekTo(target, 1_000),
                PlayerIntent.SetShuffle(true),
                PlayerIntent.SetRepeat(RepeatMode.All),
                PlayerIntent.Reconnect,
                PlayerIntent.RetryProblem(target, problem),
                PlayerIntent.DismissProblem(PlayerProblemKey(session, problem)),
                PlayerIntent.DismissCommandNotice(PlayerCommandNoticeId()),
            )) { createVm() }
        }
    }

    @Test
    fun `reading state is inert and multiple UI bindings share one pair of immediately released subscriptions`() = with(fixture) {
        runTest(dispatcher) {
            val vm = createVm()
            vm.uiApi()
            backgroundScope.launch { vm.snapshot.collect() }
            runCurrent()
            assertEquals(0, playback.stateObservations)
            assertEquals(0, playback.progressObservations)

            val first = bind(vm)
            val second = bind(vm)
            assertEquals(1, playback.stateObservations)
            assertEquals(1, playback.progressObservations)
            assertEquals(1, playback.activeStateCollectors)
            assertEquals(1, playback.activeProgressCollectors)
            first.cancel()
            runCurrent()
            assertEquals(1, playback.activeStateCollectors)
            second.cancel()
            runCurrent()
            // No virtual-time advance: Player deliberately has no source grace period.
            assertEquals(0, playback.activeStateCollectors)
            assertEquals(0, playback.activeProgressCollectors)
            assertTrue(playback.calls.isEmpty())
            assertTrue(playback.reconnectCalls.isEmpty())
        }
    }

    @Test
    fun `rebind disables stale control until fresh service facts and an empty queue clears old metadata`() = with(fixture) {
        runTest(dispatcher) {
            val current = connected()
            playback.states.value = current
            playback.progresses.value = anchor(current.snapshot)
            val vm = createVm()
            val first = bind(vm)
            first.cancel()
            runCurrent()

            val gate = CompletableDeferred<Unit>()
            playback.beforeStateCollection = { if (it == 2) gate.await() }
            val empty = PlaybackConnection.Connected(PlaybackSnapshot.create(PlaybackSessionId("fresh-empty-service")))
            playback.states.value = empty
            bind(vm)
            assertEquals(PlaybackConnection.Connecting, vm.snapshot.value.state.connection)
            assertSame(current.snapshot, vm.snapshot.value.state.displaySnapshot)
            assertNull(vm.snapshot.value.state.progress)
            send(vm, PlayerIntent.Next)
            assertTrue(playback.calls.isEmpty())

            gate.complete(Unit)
            runCurrent()
            assertEquals(empty, vm.snapshot.value.state.connection)
            assertSame(empty.snapshot, vm.snapshot.value.state.lastKnownSnapshot)
            assertNull(vm.snapshot.value.state.currentTrack)
            assertNull(vm.snapshot.value.state.progress)
            assertEquals(2, playback.stateObservations)
            assertEquals(2, playback.progressObservations)
        }
    }

    @Test
    fun `unbinding releases observations without cancelling an already accepted command`() = with(fixture) {
        runTest(dispatcher) {
            playback.states.value = connected()
            val vm = createVm()
            val binding = bind(vm)
            send(vm, PlayerIntent.Next)
            val call = playback.calls.single()
            assertTrue(vm.snapshot.value.operations[PlayerViewModel.RequestKey].isBusy)

            binding.cancel()
            runCurrent()
            assertEquals(0, playback.activeStateCollectors)
            assertEquals(0, playback.activeProgressCollectors)
            assertFalse(call.waiterCancelled)
            assertFalse(call.reply.isCancelled)
            respond(AppResult.Success(Unit))
            assertFalse(vm.snapshot.value.operations[PlayerViewModel.RequestKey].isBusy)
            bind(vm)
            assertEquals(listOf(PlaybackCommand.Next), playback.calls.map { it.command })
            assertTrue(playback.reconnectCalls.isEmpty())
        }
    }

    @Test
    fun `clearing the ViewModel cancels its waiter and observers but leaves the service reply independent`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm()
            val ui = vm.uiApi()
            send(vm, PlayerIntent.Next)
            val call = playback.calls.single()
            store.clear()
            runCurrent()

            assertTrue(call.waiterCancelled)
            assertFalse(call.reply.isCancelled)
            assertEquals(0, playback.activeStateCollectors)
            assertEquals(0, playback.activeProgressCollectors)
            val clearedState = vm.snapshot.value.state
            call.reply.complete(AppResult.Success(Unit))
            playback.states.value = connected(activation = 1)
            ui.onIntent(PlayerIntent.SetPlayRequested(false))
            bind(vm)
            runCurrent()
            assertSame(clearedState, vm.snapshot.value.state)
            assertEquals(listOf(PlaybackCommand.Next), playback.calls.map { it.command })
            assertEquals(0, playback.activeStateCollectors)
        }
    }

    @Test
    fun `clearing during reconnect does not replay recovery or send a transport command`() = with(fixture) {
        runTest(dispatcher) {
            val vm = createVm()
            bind(vm)
            send(vm, PlayerIntent.Reconnect)
            val call = playback.reconnectCalls.single()
            store.clear()
            runCurrent()
            assertTrue(call.waiterCancelled)
            assertFalse(call.reply.isCancelled)
            assertEquals(0, playback.activeStateCollectors)
            assertEquals(0, playback.activeProgressCollectors)
            call.reply.complete(AppResult.Success(Unit))
            runCurrent()
            assertEquals(1, playback.reconnectCalls.size)
            assertTrue(playback.calls.isEmpty())
        }
    }

    @Test
    fun `engine problems appear without a first command and dismissal survives rebind but not a different session`() = with(fixture) {
        runTest(dispatcher) {
            val problem = problemSnapshot(id = "shared-id")
            playback.states.value = problem
            val vm = createVm()
            val first = bind(vm)
            assertSame(problem.snapshot.transport.problem, vm.snapshot.value.state.visibleProblem)
            assertTrue(playback.calls.isEmpty())
            val key = PlayerProblemKey(problem.snapshot.sessionId, problem.snapshot.transport.problem!!.id)
            send(vm, PlayerIntent.DismissProblem(key))
            assertNull(vm.snapshot.value.state.visibleProblem)
            first.cancel()
            runCurrent()
            bind(vm)
            assertEquals(key, vm.snapshot.value.state.dismissedProblem)
            assertNull(vm.snapshot.value.state.visibleProblem)

            val replacement = problemSnapshot(id = "shared-id", sessionId = "replacement")
            control(replacement)
            assertSame(replacement.snapshot.transport.problem, vm.snapshot.value.state.visibleProblem)
            send(vm, PlayerIntent.DismissProblem(key))
            assertSame(replacement.snapshot.transport.problem, vm.snapshot.value.state.visibleProblem)
            assertTrue(playback.calls.isEmpty())

            // Healthy engine facts remove the problem even with the same item activation.
            control(connected(sessionId = "replacement"))
            assertNull(vm.snapshot.value.state.visibleProblem)
        }
    }

    @Test
    fun `a new ViewModel restores service facts through observation rather than inheriting UI cache or dismissal`() = with(fixture) {
        runTest(dispatcher) {
            val problem = problemSnapshot()
            val first = readyVm(problem)
            val key = PlayerProblemKey(problem.snapshot.sessionId, problem.snapshot.transport.problem!!.id)
            send(first, PlayerIntent.DismissProblem(key))
            store.clear()
            runCurrent()

            val fresh = createVm()
            assertEquals(PlaybackConnection.Disconnected, fresh.snapshot.value.state.connection)
            assertNull(fresh.snapshot.value.state.lastKnownSnapshot)
            assertNull(fresh.snapshot.value.state.progress)
            assertNull(fresh.snapshot.value.state.commandNotice)
            assertNull(fresh.snapshot.value.state.dismissedProblem)
            bind(fresh)
            assertSame(problem.snapshot, fresh.snapshot.value.state.liveSnapshot)
            assertNotNull(fresh.snapshot.value.state.visibleProblem)
            assertTrue(playback.calls.isEmpty())
            assertTrue(playback.reconnectCalls.isEmpty())
        }
    }
}
