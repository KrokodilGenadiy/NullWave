package com.zaus.nullwave.core.playback.media3.client

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackProblem
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.media3.state.PlaybackServiceRegistry
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedPlaybackClientTest {
    @Test
    fun `expected connector rejection preserves its cause and permits a fresh command`() = runTest {
        withFixture {
            val original = SecurityException("Session rejected connection")
            val cleanup = IllegalStateException("Connection release failed")
            original.addSuppressed(cleanup)
            connector.beforeConnect = { throw PlaybackConnectException(PlaybackConnectionError.ConnectionFailed, original) }
            var beforeDispatchCalls = 0
            val first = backgroundScope.async { client.execute(PlaybackCommand.Next) { beforeDispatchCalls++ } }
            runCurrent()
            val rejection = first.await() as AppResult.Failure
            assertEquals(PlaybackCommandError.ConnectionUnavailable, rejection.error)
            assertSame(original, rejection.cause)
            assertEquals(listOf(cleanup), rejection.cause!!.suppressed.toList())
            assertTrue(connector.connections.isEmpty())
            assertEquals(0, beforeDispatchCalls)

            connector.beforeConnect = {}
            val retry = backgroundScope.async { client.execute(PlaybackCommand.Previous) }
            runCurrent()
            assertEquals(AppResult.Success(Unit), retry.await())
            assertEquals(2, connector.calls)
            assertEquals(listOf(PlaybackCommand.Previous), connector.connections.single().commands)
            assertEquals(1, connector.connections.single().closes)
        }
    }

    @Test
    fun `connector programming failure escapes execute without retaining its lease`() = runTest {
        withFixture {
            val original = DiagnosticConnectorFailure("Invalid service configuration", "manifest")
            val cleanup = IllegalArgumentException("Failed release")
            original.addSuppressed(cleanup)
            connector.beforeConnect = { throw original }
            var beforeDispatchCalls = 0
            val first = backgroundScope.async { runCatching { client.execute(PlaybackCommand.Next) { beforeDispatchCalls++ } } }
            runCurrent()
            val failure = first.await().exceptionOrNull()
            assertSame(original, failure)
            assertEquals(listOf(cleanup), failure!!.suppressed.toList())
            assertTrue(connector.connections.isEmpty())
            assertEquals(0, beforeDispatchCalls)

            connector.beforeConnect = {}
            val retry = backgroundScope.async { client.execute(PlaybackCommand.Previous) }
            runCurrent()
            assertEquals(AppResult.Success(Unit), retry.await())
            assertEquals(2, connector.calls)
            assertEquals(listOf(PlaybackCommand.Previous), connector.connections.single().commands)
            assertEquals(1, connector.connections.single().closes)
        }
    }

    @Test
    fun `before dispatch receives the old snapshot before the command publishes its engine problem`() = runTest {
        withFixture {
            collect(client.observeState())
            runCurrent()
            val old = registry.snapshot.value!!
            val failed = PlaybackSnapshot.create(
                sessionId = session,
                revision = old.revision + 1,
                queue = old.queue,
                transport = PlaybackTransport(problem = PlaybackProblem(
                    PlaybackProblemId("new-engine-problem"), PlaybackProblemCode.DecoderFailure, old.queue.current,
                )),
            )
            val order = mutableListOf<String>()
            connector.connections.single().executeAction = {
                order += "dispatch"
                registry.publish(failed, null)
                AppResult.Success(Unit)
            }
            var callbacks = 0
            val result = backgroundScope.async {
                client.execute(PlaybackCommand.Next) { baseline ->
                    callbacks++
                    assertSame(old, baseline)
                    order += "baseline"
                }
            }
            runCurrent()

            assertEquals(AppResult.Success(Unit), result.await())
            assertEquals(1, callbacks)
            assertEquals(listOf("baseline", "dispatch"), order)
            assertSame(failed, registry.snapshot.value)
            assertEquals(1, connector.calls)
        }
    }

    @Test
    fun `before dispatch permits a confirmed connection without its first snapshot`() = runTest {
        withFixture {
            registry.attach(session) { null }
            var callbacks = 0
            val result = backgroundScope.async {
                client.execute(PlaybackCommand.Next) { baseline ->
                    callbacks++
                    assertNull(baseline)
                }
            }
            runCurrent()

            assertEquals(AppResult.Success(Unit), result.await())
            assertEquals(1, callbacks)
            assertEquals(listOf(PlaybackCommand.Next), connector.connections.single().commands)
            assertEquals(1, connector.connections.single().closes)
        }
    }

    @Test
    fun `detachment inside before dispatch rejects the old connection until fresh demand reconnects`() = runTest {
        val dispatchers = listOf(
            StandardTestDispatcher(testScheduler),
            HandlerLikeImmediateDispatcher(StandardTestDispatcher(testScheduler)),
        )
        for (main in dispatchers) {
            withFixture(main) {
                val observer = collect(client.observeState())
                runCurrent()
                val previous = connector.connections.single()
                var callbacks = 0

                val rejected = backgroundScope.async(main) {
                    client.execute(PlaybackCommand.Next) {
                        callbacks++
                        // Service teardown is authoritative even before Media3 delivers disconnect.
                        registry.detach(session)
                    }
                }
                runCurrent()

                assertEquals(PlaybackCommandError.ConnectionUnavailable, (rejected.await() as AppResult.Failure).error)
                assertEquals(1, callbacks)
                assertTrue(previous.commands.isEmpty())
                assertEquals(1, previous.closes)
                assertNull(registry.attachedSessionId)
                assertEquals(1, connector.calls)

                val replacement = PlaybackSessionId("replacement-after-detachment")
                val replacementSnapshot = replaceService(replacement)
                connector.sessionId = replacement
                val fresh = backgroundScope.async(main) { client.execute(PlaybackCommand.Previous) }
                runCurrent()

                assertEquals(AppResult.Success(Unit), fresh.await())
                assertEquals(2, connector.calls)
                assertTrue(previous.commands.isEmpty())
                assertEquals(1, previous.closes)
                val connection = connector.connections.last()
                assertEquals(listOf(PlaybackCommand.Previous), connection.commands)
                assertEquals(0, connection.closes)
                assertEquals(PlaybackConnection.Connected(replacementSnapshot), observer.values.last())

                observer.job.cancel()
                runCurrent()
                assertEquals(1, connection.closes)
                assertEquals(1, previous.closes)
            }
        }
    }

    @Test
    fun `a replacement session without its first snapshot rejects the previous command connection`() = runTest {
        withFixture {
            collect(client.observeState())
            runCurrent()
            val previous = connector.connections.single()
            val replacement = PlaybackSessionId("replacement-before-first-snapshot")

            val result = backgroundScope.async {
                client.execute(PlaybackCommand.Next) {
                    registry.attach(replacement) { null }
                    registry.startNotAllowed(replacement)
                }
            }
            runCurrent()

            assertEquals(PlaybackCommandError.ConnectionUnavailable, (result.await() as AppResult.Failure).error)
            assertTrue(previous.commands.isEmpty())
            assertEquals(1, previous.closes)
            assertNull(registry.snapshot.value)
            assertEquals(PlaybackConnectionError.StartNotAllowed, registry.failure.value)
        }
    }

    @Test
    fun `callback failure propagates without being classified as a connector rejection and releases its lease`() = runTest {
        withFixture {
            val original = PlaybackConnectException(
                PlaybackConnectionError.ConnectionFailed,
                DiagnosticConnectorFailure("Caller callback failed", "baseline"),
            )
            val result = backgroundScope.async {
                runCatching { client.execute(PlaybackCommand.Next) { throw original } }
            }
            runCurrent()

            assertSame(original, result.await().exceptionOrNull())
            assertTrue(connector.connections.single().commands.isEmpty())
            assertEquals(1, connector.connections.single().closes)

            val retry = backgroundScope.async { client.execute(PlaybackCommand.Previous) }
            runCurrent()
            assertEquals(AppResult.Success(Unit), retry.await())
            assertEquals(2, connector.calls)
            assertEquals(listOf(PlaybackCommand.Previous), connector.connections.last().commands)
        }
    }

    @Test
    fun `cancellation inside before dispatch does not send the command`() = runTest {
        withFixture {
            var callbacks = 0
            val result = backgroundScope.async {
                val caller = currentCoroutineContext().job
                client.execute(PlaybackCommand.Next) {
                    callbacks++
                    caller.cancel()
                }
            }
            runCurrent()

            assertTrue(result.isCancelled)
            assertEquals(1, callbacks)
            assertTrue(connector.connections.single().commands.isEmpty())
            assertEquals(1, connector.connections.single().closes)
        }
    }

    @Test
    fun `callback reentry cannot dispatch through a disconnected or replaced service`() = runTest {
        for (replaceSession in listOf(false, true)) {
            withFixture(HandlerLikeImmediateDispatcher(StandardTestDispatcher(testScheduler))) {
                val result = backgroundScope.async {
                    client.execute(PlaybackCommand.Next) {
                        if (replaceSession) replaceService(PlaybackSessionId("replacement-during-callback"))
                        else connector.connections.single().disconnect()
                    }
                }
                runCurrent()

                assertEquals(PlaybackCommandError.ConnectionUnavailable, (result.await() as AppResult.Failure).error)
                assertTrue(connector.connections.single().commands.isEmpty())
                assertEquals(1, connector.connections.single().closes)
            }
        }
    }

    @Test
    fun `state and progress collectors share one connection and last lease releases only the client`() = runTest {
        withFixture {
            val first = collect(client.observeState())
            val second = collect(client.observeState())
            val position = collect(client.observeProgress())
            runCurrent()
            assertEquals(1, connector.calls)
            assertEquals(PlaybackConnection.Connected(snapshot()), first.values.last())
            assertEquals(first.values.last(), second.values.last())

            first.job.cancel()
            second.job.cancel()
            runCurrent()
            assertEquals(0, connector.connections.single().closes)
            position.job.cancel()
            runCurrent()
            assertEquals(1, connector.connections.single().closes)
            assertTrue("Releasing UI cannot clear service state", registry.snapshot.value != null)
        }
    }

    @Test
    fun `execute works without prior observation and preserves both relative commands`() = runTest {
        withFixture {
            val first = backgroundScope.async { client.execute(PlaybackCommand.Next) }
            val second = backgroundScope.async { client.execute(PlaybackCommand.Next) }
            runCurrent()
            assertEquals(AppResult.Success(Unit), first.await())
            assertEquals(AppResult.Success(Unit), second.await())
            assertEquals(1, connector.calls)
            assertEquals(listOf(PlaybackCommand.Next, PlaybackCommand.Next), connector.connections.single().commands)
            assertEquals(1, connector.connections.single().closes)
        }
    }

    @Test
    fun `pause reaches owner while an initially empty queue has a pending replacement`() = runTest {
        withFixture {
            registry.publish(PlaybackSnapshot.create(sessionId = session), null)
            collect(client.observeState())
            runCurrent()
            val connection = connector.connections.single()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val replacement = PlaybackCommand.ReplaceQueue(PlaybackQueueRequest.create(listOf(TrackId("new")), 0))
            val pause = PlaybackCommand.SetPlayRequested(false)
            connection.executeAction = { command ->
                if (command is PlaybackCommand.ReplaceQueue) {
                    entered.complete(Unit)
                    release.await()
                    AppResult.Success(Unit)
                } else AppResult.Failure(PlaybackCommandError.CommandUnavailable)
            }
            val pending = backgroundScope.async { client.execute(replacement) }
            try {
                runCurrent()
                assertTrue(entered.isCompleted)
                assertFalse(pending.isCompleted)
                val result = backgroundScope.async { client.execute(pause) }
                runCurrent()
                assertEquals(PlaybackCommandError.CommandUnavailable, (result.await() as AppResult.Failure).error)
                assertEquals(listOf(replacement, pause), connection.commands)
            } finally {
                release.complete(Unit)
                pending.cancel()
            }
        }
    }

    @Test
    fun `unavailable next still reaches owner and preserves its rejection`() = runTest {
        withFixture {
            registry.publish(PlaybackSnapshot.create(sessionId = session), null)
            collect(client.observeState())
            runCurrent()
            val connection = connector.connections.single()
            val failure = AppResult.Failure(PlaybackCommandError.CommandUnavailable, originalMessage = "No next item")
            connection.executeAction = { failure }

            val result = backgroundScope.async { client.execute(PlaybackCommand.Next) }
            runCurrent()
            assertEquals(failure, result.await())
            assertEquals(listOf(PlaybackCommand.Next), connection.commands)
        }
    }

    @Test
    fun `cancelled command waiter does not cancel a connection still leased by an observer`() = runTest {
        withFixture {
            val release = CompletableDeferred<Unit>()
            connector.beforeConnect = { release.await() }
            val observer = collect(client.observeState())
            val command = backgroundScope.async { client.execute(PlaybackCommand.Next) }
            runCurrent()
            command.cancel()
            runCurrent()
            release.complete(Unit)
            runCurrent()
            assertEquals(1, connector.calls)
            assertTrue(observer.values.last() is PlaybackConnection.Connected)
            assertTrue(connector.connections.single().commands.isEmpty())
            assertEquals(0, connector.connections.single().closes)
        }
    }

    @Test
    fun `last lease cancels an unfinished connection and a later subscriber starts a fresh attempt`() = runTest {
        withFixture {
            var cancelled = false
            connector.beforeConnect = {
                try { awaitCancellation() }
                finally { cancelled = true }
            }
            val first = collect(client.observeState())
            runCurrent()
            assertEquals(1, connector.calls)
            first.job.cancel()
            runCurrent()
            assertTrue(cancelled)
            assertTrue(connector.connections.isEmpty())

            connector.beforeConnect = {}
            val second = collect(client.observeState())
            runCurrent()
            assertEquals(2, connector.calls)
            assertTrue(second.values.last() is PlaybackConnection.Connected)
            assertEquals(1, connector.connections.size)
        }
    }

    @Test
    fun `connection timeout is bounded and a later demand can connect without replaying the old command`() = runTest {
        withFixture {
            connector.beforeConnect = { awaitCancellation() }
            val observer = collect(client.observeState())
            val first = backgroundScope.async { client.execute(PlaybackCommand.Next) }
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(PlaybackCommandError.ConnectionUnavailable, (first.await() as AppResult.Failure).error)
            assertEquals(PlaybackConnection.Unavailable(PlaybackConnectionError.ConnectionFailed), observer.values.last())

            connector.beforeConnect = {}
            val second = backgroundScope.async { client.execute(PlaybackCommand.Previous) }
            runCurrent()
            assertEquals(AppResult.Success(Unit), second.await())
            assertEquals(2, connector.calls)
            assertEquals(listOf(PlaybackCommand.Previous), connector.connections.single().commands)
        }
    }

    @Test
    fun `disconnect is observable and reconnect happens only on new demand`() = runTest {
        withFixture {
            val observer = collect(client.observeState())
            runCurrent()
            connector.connections.single().disconnect()
            runCurrent()
            assertEquals(PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected), observer.values.last())
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(1, connector.calls)

            val command = backgroundScope.async { client.execute(PlaybackCommand.Next) }
            runCurrent()
            assertEquals(AppResult.Success(Unit), command.await())
            assertEquals(2, connector.calls)
            assertTrue(observer.values.last() is PlaybackConnection.Connected)
            assertEquals(listOf(PlaybackCommand.Next), connector.connections.last().commands)
        }
    }

    @Test
    fun `last release cannot overwrite a new connection acquired inside platform close`() = runTest {
        withFixture(HandlerLikeImmediateDispatcher(StandardTestDispatcher(testScheduler))) {
            val first = collect(client.observeState())
            runCurrent()
            lateinit var replacement: Collected<PlaybackConnection>
            connector.connections.single().beforeClose = {
                replacement = collect(client.observeState(), immediate = true)
            }
            first.job.cancel()
            runCurrent()

            assertEquals(2, connector.calls)
            assertTrue(replacement.values.last() is PlaybackConnection.Connected)
            assertFalse(replacement.values.any { it == PlaybackConnection.Disconnected })
        }
    }

    @Test
    fun `disconnect cannot overwrite new demand admitted during old platform close`() = runTest {
        withFixture(HandlerLikeImmediateDispatcher(StandardTestDispatcher(testScheduler))) {
            collect(client.observeState())
            runCurrent()
            lateinit var replacement: Collected<PlaybackConnection>
            val old = connector.connections.single()
            old.beforeClose = { replacement = collect(client.observeState(), immediate = true) }
            old.disconnect()
            runCurrent()

            assertEquals(2, connector.calls)
            assertTrue(replacement.values.last() is PlaybackConnection.Connected)
            assertFalse(replacement.values.any {
                it == PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected)
            })
        }
    }

    @Test
    fun `new service registry incarnation cannot be projected through an old live controller`() = runTest {
        withFixture {
            val state = collect(client.observeState())
            val progress = collect(client.observeProgress())
            runCurrent()
            val newer = PlaybackSessionId("new-session")
            val nextSnapshot = replaceService(newer)
            val before = samples
            runCurrent()

            assertEquals(PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected), state.values.last())
            assertTrue(state.values.none { it == PlaybackConnection.Connected(nextSnapshot) })
            assertEquals(null, progress.values.last())
            assertEquals(before, samples)
            assertEquals(1, connector.connections.first().closes)

            connector.sessionId = newer
            val command = backgroundScope.async { client.execute(PlaybackCommand.Next) }
            runCurrent()
            assertEquals(AppResult.Success(Unit), command.await())
            assertEquals(PlaybackConnection.Connected(nextSnapshot), state.values.last())
        }
    }

    @Test
    fun `control observation alone does not start progress polling`() = runTest {
        withFixture {
            advancing = true
            collect(client.observeState())
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(0, samples)
        }
    }

    @Test
    fun `two progress collectors share one timer and last progress lease stops polling while state stays connected`() = runTest {
        withFixture {
            advancing = true
            collect(client.observeState())
            val first = collect(client.observeProgress())
            val second = collect(client.observeProgress())
            runCurrent()
            val initial = samples
            advanceTimeBy(250)
            runCurrent()
            assertEquals(initial + 1, samples)
            first.job.cancel()
            runCurrent()
            val afterFirst = samples
            advanceTimeBy(250)
            runCurrent()
            assertEquals(afterFirst + 1, samples)
            second.job.cancel()
            runCurrent()
            val stopped = samples
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(stopped, samples)
            assertEquals(0, connector.connections.single().closes)
        }
    }

    @Test
    fun `pause and buffering publish immediate stationary anchors and stop the periodic timer`() = runTest {
        withFixture {
            advancing = true
            val observer = collect(client.observeProgress())
            runCurrent()
            advanceTimeBy(100)
            advancing = false
            publish(phase = PlaybackPhase.Ready, playRequested = false)
            runCurrent()
            assertFalse(observer.values.last()!!.advancing)
            assertEquals(100L, observer.values.last()!!.sampledAtMs)
            val paused = samples
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(paused, samples)

            publish(phase = PlaybackPhase.Buffering, playRequested = true)
            runCurrent()
            val buffering = samples
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(buffering, samples)
            advancing = true
            publish(phase = PlaybackPhase.Ready, playRequested = true)
            runCurrent()
            val resumed = samples
            advanceTimeBy(250)
            runCurrent()
            assertEquals(resumed + 1, samples)
        }
    }

    @Test
    fun `new progress subscriber receives a fresh sample without waiting for the next timer tick`() = runTest {
        withFixture {
            advancing = true
            collect(client.observeProgress())
            runCurrent()
            advanceTimeBy(100)
            val newer = collect(client.observeProgress())
            runCurrent()
            assertEquals(100L, newer.values.last()!!.sampledAtMs)
        }
    }

    @Test
    fun `slow progress consumer receives the newest anchor without a backlog of timer samples`() = runTest {
        withFixture {
            advancing = true
            val blocked = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val times = mutableListOf<Long>()
            val consumer = backgroundScope.launch {
                client.observeProgress().collect { value ->
                    if (value != null) {
                        times += value.sampledAtMs
                        if (!blocked.isCompleted) {
                            blocked.complete(Unit)
                            release.await()
                        }
                    }
                }
            }
            collectors += consumer
            try {
                runCurrent()
                assertTrue(blocked.isCompleted)
                advanceTimeBy(2_000)
                runCurrent()
                assertEquals(listOf(0L), times)

                release.complete(Unit)
                runCurrent()
                assertEquals(listOf(0L, 2_000L), times)
            } finally {
                release.complete(Unit)
                consumer.cancel()
            }
        }
    }

    @Test
    fun `already cancelled flow producers do not acquire a platform connection`() = runTest {
        withFixture {
            for (flow in listOf(client.observeState(), client.observeProgress())) {
                val observer = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { flow.collect {} }
                observer.cancel()
                runCurrent()
            }
            assertEquals(0, connector.calls)
        }
    }

    @Test
    fun `foreground refusal comes from the service registry and a new command can clear the connection error`() = runTest {
        withFixture {
            val observer = collect(client.observeState())
            runCurrent()
            registry.startNotAllowed(session)
            runCurrent()
            assertEquals(PlaybackConnection.Unavailable(PlaybackConnectionError.StartNotAllowed), observer.values.last())
            val command = backgroundScope.async { client.execute(PlaybackCommand.Next) }
            runCurrent()
            assertEquals(AppResult.Success(Unit), command.await())
            assertTrue(observer.values.last() is PlaybackConnection.Connected)
        }
    }

    private suspend fun TestScope.withFixture(
        main: CoroutineDispatcher = StandardTestDispatcher(testScheduler, "client-main"),
        block: suspend Fixture.() -> Unit,
    ) {
        val fixture = Fixture(this, main)
        try { fixture.block() }
        finally {
            fixture.collectors.forEach { it.cancel() }
            runCurrent()
            fixture.appJob.cancel()
            runCurrent()
        }
    }

    private class Fixture(private val scope: TestScope, val main: CoroutineDispatcher) {
        val appJob = SupervisorJob(scope.backgroundScope.coroutineContext.job)
        val registry = PlaybackServiceRegistry()
        val connector = FakeConnector()
        val client = SharedPlaybackClient(
            main, registry, connector, CoroutineScope(scope.backgroundScope.coroutineContext + appJob),
        )
        val collectors = mutableListOf<Job>()
        val session = PlaybackSessionId("session")
        private val entry = QueueEntry(QueueEntryId("entry"), MediaTrack(TrackId("track"), "Track", null, null, 10_000))
        private val token = PlaybackItemToken(session, entry.entryId, 0)
        var advancing = false
        var samples = 0
        private var phase = PlaybackPhase.Ready
        private var playRequested = true
        private var revision = 0L

        init {
            registry.attach(session) { sample() }
            registry.publish(snapshot(), anchor())
        }

        fun snapshot() = PlaybackSnapshot.create(
            sessionId = session,
            revision = revision,
            queue = PlaybackQueue.create(entries = listOf(entry), current = token),
            transport = PlaybackTransport(phase, playRequested),
            capabilities = PlaybackCapability.entries.filterNot { it == PlaybackCapability.Retry }.toSet(),
        )

        fun publish(phase: PlaybackPhase, playRequested: Boolean) {
            this.phase = phase
            this.playRequested = playRequested
            revision++
            registry.publish(snapshot(), anchor())
        }

        fun replaceService(id: PlaybackSessionId): PlaybackSnapshot {
            val nextToken = token.copy(sessionId = id)
            val nextSnapshot = PlaybackSnapshot.create(
                sessionId = id,
                queue = PlaybackQueue.create(entries = listOf(entry), current = nextToken),
                transport = PlaybackTransport(phase, playRequested),
                capabilities = PlaybackCapability.entries.filterNot { it == PlaybackCapability.Retry }.toSet(),
            )
            registry.attach(id) { samples++; anchor().copy(target = nextToken) }
            registry.publish(nextSnapshot, anchor().copy(target = nextToken))
            return nextSnapshot
        }

        fun <T> collect(flow: Flow<T>, immediate: Boolean = false): Collected<T> {
            val values = mutableListOf<T>()
            val job = if (immediate) scope.backgroundScope.launch(main, start = CoroutineStart.UNDISPATCHED) {
                flow.collect { values += it }
            } else scope.backgroundScope.launch { flow.collect { values += it } }
            collectors += job
            return Collected(job, values)
        }

        private fun sample(): PlaybackProgress {
            samples++
            return anchor()
        }

        private fun anchor() = PlaybackProgress(
            token, 0, scope.testScheduler.currentTime, 10_000, 10_000, scope.testScheduler.currentTime, advancing,
        )
    }

    private class Collected<T>(val job: Job, val values: List<T>)

    private class DiagnosticConnectorFailure(message: String, val diagnostic: String) : IllegalStateException(message)

    private class FakeConnector : PlaybackConnector {
        var calls = 0
        var sessionId = PlaybackSessionId("session")
        var beforeConnect: suspend () -> Unit = {}
        val connections = mutableListOf<FakeConnection>()
        override suspend fun connect(onDisconnected: () -> Unit): PlaybackClientConnection {
            calls++
            beforeConnect()
            return FakeConnection(onDisconnected, sessionId).also { connections += it }
        }
    }

    private class FakeConnection(
        private val onDisconnected: () -> Unit,
        override val sessionId: PlaybackSessionId,
    ) : PlaybackClientConnection {
        var closes = 0
        var beforeClose: () -> Unit = {}
        var executeAction: suspend (PlaybackCommand) -> EmptyResult<PlaybackCommandError> = { AppResult.Success(Unit) }
        val commands = mutableListOf<PlaybackCommand>()
        override suspend fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> {
            commands += command
            return executeAction(command)
        }
        fun disconnect() { onDisconnected() }
        override fun close() {
            closes++
            beforeClose()
            onDisconnected()
        }
    }

    /** A Main queue entry has no active Unconfined trampoline; nested immediate resumption is inline. */
    private class HandlerLikeImmediateDispatcher(private val queue: CoroutineDispatcher) : CoroutineDispatcher() {
        private val onMain = ThreadLocal.withInitial { false }
        override fun isDispatchNeeded(context: CoroutineContext): Boolean = onMain.get() != true
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.dispatch(context, Runnable {
                val previous = onMain.get()
                onMain.set(true)
                try { block.run() }
                finally { onMain.set(previous) }
            })
        }
    }
}
