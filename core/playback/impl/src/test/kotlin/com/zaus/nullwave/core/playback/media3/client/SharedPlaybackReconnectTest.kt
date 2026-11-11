package com.zaus.nullwave.core.playback.media3.client

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
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
class SharedPlaybackReconnectTest {
    @Test
    fun `reconnect clears sticky start denial through the retained connection without transport or progress demand`() = runTest {
        withFixture(engineProblem = true) {
            val observer = observeState()
            runCurrent()
            val connection = connector.connections.single()
            registry.startNotAllowed(session)
            runCurrent()
            assertEquals(PlaybackConnection.Unavailable(PlaybackConnectionError.StartNotAllowed), observer.values.last())

            val result = backgroundScope.async { client.reconnect() }
            runCurrent()

            assertEquals(AppResult.Success(Unit), result.await())
            assertNull(registry.failure.value)
            assertEquals(PlaybackConnection.Connected(snapshot), observer.values.last())
            assertSame(snapshot, registry.snapshot.value)
            assertSame(snapshot.queue, registry.snapshot.value!!.queue)
            assertSame(snapshot.transport.problem, registry.snapshot.value!!.transport.problem)
            assertSame(anchor, registry.anchor.value)
            assertEquals(1, connector.calls)
            assertEquals(0, connection.closes)
            assertTrue(connection.commands.isEmpty())
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals("Reconnect must not acquire progress demand or sample the engine", 0, samples)

            observer.job.cancel()
            runCurrent()
            assertEquals(1, connection.closes)
            assertSame(snapshot, registry.snapshot.value)
        }
    }

    @Test
    fun `reconnect without observers releases only its temporary control lease`() = runTest {
        withFixture {
            registry.startNotAllowed(session)
            val result = backgroundScope.async { client.reconnect() }
            runCurrent()

            assertEquals(AppResult.Success(Unit), result.await())
            assertEquals(1, connector.calls)
            assertEquals(1, connector.connections.single().closes)
            assertTrue(connector.connections.single().commands.isEmpty())
            assertSame(snapshot, registry.snapshot.value)
            assertSame(anchor, registry.anchor.value)
            assertEquals(0, samples)
        }
    }

    @Test
    fun `confirmed connection without a first registry snapshot does not invent engine state`() = runTest {
        withFixture {
            registry.attach(session) { samples++; null }
            val result = backgroundScope.async { client.reconnect() }
            runCurrent()

            assertEquals(AppResult.Success(Unit), result.await())
            assertEquals(session, registry.attachedSessionId)
            assertNull(registry.snapshot.value)
            assertNull(registry.anchor.value)
            assertNull(registry.failure.value)
            assertTrue(connector.connections.single().commands.isEmpty())
            assertEquals(1, connector.connections.single().closes)
            assertEquals(0, samples)
        }
    }

    @Test
    fun `detachment before reconnect retires the retained connection without waiting for its disconnect callback`() = runTest {
        withFixture {
            val observer = observeState()
            runCurrent()
            val oldConnection = connector.connections.single()
            // Service teardown detaches before Media3 delivers the controller disconnect callback.
            registry.detach(session)

            val result = backgroundScope.async { client.reconnect() }
            runCurrent()

            assertEquals(PlaybackConnectionError.ServiceDisconnected, (result.await() as AppResult.Failure).error)
            assertEquals(PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected), observer.values.last())
            assertEquals(1, connector.calls)
            assertEquals(1, oldConnection.closes)
            assertTrue(oldConnection.commands.isEmpty())
            assertEquals(0, samples)

            val replacement = PlaybackSessionId("replacement-after-detachment")
            val replacementSnapshot = PlaybackSnapshot.create(replacement)
            registry.attach(replacement) { samples++; null }
            registry.publish(replacementSnapshot, null)
            registry.startNotAllowed(replacement)
            connector.sessionId = replacement
            val retry = backgroundScope.async { client.reconnect() }
            runCurrent()

            assertEquals(AppResult.Success(Unit), retry.await())
            assertEquals(2, connector.calls)
            val newConnection = connector.connections.last()
            assertEquals(replacement, newConnection.sessionId)
            assertEquals(0, newConnection.closes)
            assertEquals(PlaybackConnection.Connected(replacementSnapshot), observer.values.last())
            assertNull(registry.failure.value)
            assertTrue(newConnection.commands.isEmpty())
            assertEquals(0, samples)

            // The obsolete platform callback cannot retire the replacement's observer lease.
            oldConnection.disconnect()
            runCurrent()
            assertEquals(PlaybackConnection.Connected(replacementSnapshot), observer.values.last())
            assertEquals(0, newConnection.closes)
            observer.job.cancel()
            runCurrent()
            assertEquals(1, oldConnection.closes)
            assertEquals(1, newConnection.closes)
        }
    }

    @Test
    fun `detachment during reconnect handshake rejects the old session and releases the temporary lease`() = runTest {
        withFixture {
            val gate = CompletableDeferred<Unit>()
            connector.beforeConnect = { gate.await() }
            val result = backgroundScope.async { client.reconnect() }
            runCurrent()
            assertEquals(1, connector.calls)
            assertTrue(connector.connections.isEmpty())

            registry.detach(session)
            gate.complete(Unit)
            runCurrent()

            assertEquals(PlaybackConnectionError.ServiceDisconnected, (result.await() as AppResult.Failure).error)
            val oldConnection = connector.connections.single()
            assertEquals(1, oldConnection.closes)
            assertTrue(oldConnection.commands.isEmpty())
            assertNull(registry.attachedSessionId)
            assertNull(registry.snapshot.value)
            assertNull(registry.anchor.value)
            assertEquals(0, samples)

            val replacement = PlaybackSessionId("replacement-after-handshake")
            registry.attach(replacement) { samples++; null }
            connector.sessionId = replacement
            connector.beforeConnect = {}
            val retry = backgroundScope.async { client.reconnect() }
            runCurrent()

            assertEquals(AppResult.Success(Unit), retry.await())
            assertEquals(2, connector.calls)
            assertEquals(1, oldConnection.closes)
            assertEquals(1, connector.connections.last().closes)
            assertTrue(connector.connections.last().commands.isEmpty())
            assertEquals(replacement, registry.attachedSessionId)
            assertNull(registry.snapshot.value)
            assertEquals(0, samples)
        }
    }

    @Test
    fun `expected connection rejections retain the reason cause and suppressed diagnostics`() = runTest {
        for (reason in PlaybackConnectionError.entries) {
            withFixture {
                val original = SecurityException("Connection rejected: $reason")
                val cleanup = IllegalStateException("Release diagnostic")
                original.addSuppressed(cleanup)
                connector.beforeConnect = { throw PlaybackConnectException(reason, original) }
                val result = backgroundScope.async { client.reconnect() }
                runCurrent()

                val failure = result.await() as AppResult.Failure
                assertEquals(reason, failure.error)
                assertSame(original, failure.cause)
                assertEquals(listOf(cleanup), failure.cause!!.suppressed.toList())
                assertTrue(connector.connections.isEmpty())

                connector.beforeConnect = {}
                val retry = backgroundScope.async { client.reconnect() }
                runCurrent()
                assertEquals(AppResult.Success(Unit), retry.await())
                assertEquals(2, connector.calls)
                assertTrue(connector.connections.single().commands.isEmpty())
                assertEquals(1, connector.connections.single().closes)
            }
        }
    }

    @Test
    fun `connection timeout is bounded and does not prevent a later no audio reconnect`() = runTest {
        withFixture {
            val gate = CompletableDeferred<Unit>()
            connector.beforeConnect = { gate.await() }
            val result = backgroundScope.async { client.reconnect() }
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            val failure = result.await() as AppResult.Failure
            assertEquals(PlaybackConnectionError.ConnectionFailed, failure.error)
            assertTrue(failure.cause is TimeoutCancellationException)
            assertTrue(connector.connections.isEmpty())

            connector.beforeConnect = {}
            val retry = backgroundScope.async { client.reconnect() }
            runCurrent()
            assertEquals(AppResult.Success(Unit), retry.await())
            assertEquals(2, connector.calls)
            assertTrue(connector.connections.single().commands.isEmpty())
        }
    }

    @Test
    fun `connector programming failure escapes reconnect without keeping a lease`() = runTest {
        withFixture {
            val original = DiagnosticConnectorFailure("Broken service configuration", "manifest")
            val cleanup = IllegalStateException("Release failed")
            original.addSuppressed(cleanup)
            connector.beforeConnect = { throw original }
            val result = backgroundScope.async { runCatching { client.reconnect() } }
            runCurrent()

            val failure = result.await().exceptionOrNull()
            assertSame(original, failure)
            assertEquals(listOf(cleanup), failure!!.suppressed.toList())
            assertTrue(connector.connections.isEmpty())

            connector.beforeConnect = {}
            val retry = backgroundScope.async { client.reconnect() }
            runCurrent()
            assertEquals(AppResult.Success(Unit), retry.await())
            assertEquals(1, connector.connections.single().closes)
        }
    }

    @Test
    fun `cancellation before Main dispatch neither connects nor clears the service denial`() = runTest {
        withFixture {
            registry.startNotAllowed(session)
            val result = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { client.reconnect() }
            result.cancel()
            runCurrent()

            assertTrue(result.isCancelled)
            assertEquals(0, connector.calls)
            assertEquals(PlaybackConnectionError.StartNotAllowed, registry.failure.value)
            assertSame(snapshot, registry.snapshot.value)
        }
    }

    @Test
    fun `cancelled reconnect waiter cannot cancel a connection still leased by an observer`() = runTest {
        withFixture {
            val gate = CompletableDeferred<Unit>()
            connector.beforeConnect = { gate.await() }
            registry.startNotAllowed(session)
            val observer = observeState()
            val result = backgroundScope.async { client.reconnect() }
            runCurrent()

            result.cancel()
            runCurrent()
            assertTrue(result.isCancelled)
            assertEquals(PlaybackConnectionError.StartNotAllowed, registry.failure.value)
            assertFalse(gate.isCancelled)
            gate.complete(Unit)
            runCurrent()

            assertEquals(PlaybackConnection.Unavailable(PlaybackConnectionError.StartNotAllowed), observer.values.last())
            assertEquals(0, connector.connections.single().closes)
            assertTrue(connector.connections.single().commands.isEmpty())

            val retry = backgroundScope.async { client.reconnect() }
            runCurrent()
            assertEquals(AppResult.Success(Unit), retry.await())
            assertEquals(1, connector.calls)
        }
    }

    @Test
    fun `cancelled lone reconnect releases its connection attempt without clearing denial`() = runTest {
        withFixture {
            val gate = CompletableDeferred<Unit>()
            var connectionCancelled = false
            connector.beforeConnect = {
                try { gate.await() }
                finally { connectionCancelled = !gate.isCompleted }
            }
            registry.startNotAllowed(session)
            val result = backgroundScope.async { client.reconnect() }
            runCurrent()
            result.cancel()
            runCurrent()

            assertTrue(result.isCancelled)
            assertTrue(connectionCancelled)
            assertTrue(connector.connections.isEmpty())
            assertEquals(PlaybackConnectionError.StartNotAllowed, registry.failure.value)
            assertEquals(0, samples)
        }
    }

    @Test
    fun `caller cancellation during immediate denial clearing cannot return success`() = runTest {
        withFixture(immediateMain()) {
            observeState()
            runCurrent()
            registry.startNotAllowed(session)
            runCurrent()
            var returnedNormally = false
            val result = backgroundScope.async(main, start = CoroutineStart.LAZY) {
                client.reconnect()
                returnedNormally = true
            }
            var callbacks = 0
            onFailureCleared { callbacks++; result.cancel() }

            result.start()
            runCurrent()

            assertEquals(1, callbacks)
            assertTrue(result.isCancelled)
            assertFalse("Same-Main reconnect must throw cancellation before returning to its caller", returnedNormally)
            assertEquals(0, connector.connections.single().closes)
            assertTrue(connector.connections.single().commands.isEmpty())
        }
    }

    @Test
    fun `last lease close cancellation cannot return normally to the same Main caller`() = runTest {
        withFixture(immediateMain()) {
            var callbacks = 0
            var returnedNormally = false
            val result = backgroundScope.async(main, start = CoroutineStart.LAZY) {
                client.reconnect()
                returnedNormally = true
            }
            connector.onConnected = { connection ->
                connection.beforeClose = { callbacks++; result.cancel() }
            }

            result.start()
            runCurrent()

            assertEquals(1, callbacks)
            assertTrue(result.isCancelled)
            assertFalse("Release must not deliver an outcome after cancelling the same-Main caller", returnedNormally)
            assertEquals(1, connector.connections.single().closes)
            assertTrue(connector.connections.single().commands.isEmpty())
            assertSame(snapshot, registry.snapshot.value)
        }
    }

    @Test
    fun `initial stale connection close cancellation wins over a normal failure return`() = runTest {
        withFixture(immediateMain()) {
            observeState()
            runCurrent()
            val connection = connector.connections.single()
            val replacement = PlaybackSessionId("replacement-before-validation")
            registry.attach(replacement) { samples++; null }
            registry.startNotAllowed(replacement)
            runCurrent()
            var callbacks = 0
            var returnedNormally = false
            val result = backgroundScope.async(main, start = CoroutineStart.LAZY) {
                client.reconnect()
                returnedNormally = true
            }
            connection.beforeClose = { callbacks++; result.cancel() }

            result.start()
            runCurrent()

            assertEquals(1, callbacks)
            assertTrue(result.isCancelled)
            assertFalse("Cancellation during initial validation must beat typed ServiceDisconnected", returnedNormally)
            assertEquals(1, connection.closes)
            assertEquals(PlaybackConnectionError.StartNotAllowed, registry.failure.value)
            assertTrue(connection.commands.isEmpty())
        }
    }

    @Test
    fun `known session detachment during immediate denial clearing rejects the old connection`() = runTest {
        withFixture(immediateMain()) {
            observeState()
            runCurrent()
            val connection = connector.connections.single()
            registry.startNotAllowed(session)
            runCurrent()
            var callbacks = 0
            onFailureCleared { callbacks++; registry.detach(session) }

            val result = backgroundScope.async(main) { client.reconnect() }
            runCurrent()

            assertEquals(1, callbacks)
            assertEquals(PlaybackConnectionError.ServiceDisconnected, (result.await() as AppResult.Failure).error)
            assertEquals(1, connection.closes)
            assertNull(registry.snapshot.value)
            assertNull(registry.failure.value)
            assertTrue(connection.commands.isEmpty())
        }
    }

    @Test
    fun `immediate disconnect while clearing denial cannot return success`() = runTest {
        withFixture(immediateMain()) {
            observeState()
            runCurrent()
            val connection = connector.connections.single()
            registry.startNotAllowed(session)
            runCurrent()
            var callbacks = 0
            onFailureCleared { callbacks++; connection.disconnect() }

            val result = backgroundScope.async { client.reconnect() }
            runCurrent()

            assertEquals(1, callbacks)
            assertEquals(PlaybackConnectionError.ServiceDisconnected, (result.await() as AppResult.Failure).error)
            assertEquals(1, connection.closes)
            assertTrue(connection.commands.isEmpty())
        }
    }

    @Test
    fun `immediate service replacement while clearing denial cannot clear the new session refusal`() = runTest {
        for (publishReplacement in listOf(false, true)) {
            withFixture(immediateMain()) {
                observeState()
                runCurrent()
                val connection = connector.connections.single()
                registry.startNotAllowed(session)
                runCurrent()
                val replacement = PlaybackSessionId("replacement-after-clear")
                var callbacks = 0
                onFailureCleared {
                    callbacks++
                    registry.attach(replacement) { samples++; null }
                    if (publishReplacement) registry.publish(PlaybackSnapshot.create(replacement), null)
                    registry.startNotAllowed(replacement)
                }

                val result = backgroundScope.async { client.reconnect() }
                runCurrent()

                assertEquals(1, callbacks)
                assertEquals(PlaybackConnectionError.ServiceDisconnected, (result.await() as AppResult.Failure).error)
                assertEquals(PlaybackConnectionError.StartNotAllowed, registry.failure.value)
                if (publishReplacement) assertEquals(replacement, registry.snapshot.value!!.sessionId)
                else assertNull(registry.snapshot.value)
                assertTrue(connection.commands.isEmpty())
                assertEquals(1, connection.closes)
            }
        }
    }

    @Test
    fun `replacement during connection wait keeps the new refusal before any old clear`() = runTest {
        withFixture {
            val gate = CompletableDeferred<Unit>()
            connector.beforeConnect = { gate.await() }
            registry.startNotAllowed(session)
            val result = backgroundScope.async { client.reconnect() }
            runCurrent()
            val replacement = PlaybackSessionId("replacement-before-confirmation")
            registry.attach(replacement) { samples++; null }
            registry.startNotAllowed(replacement)
            gate.complete(Unit)
            runCurrent()

            assertEquals(PlaybackConnectionError.ServiceDisconnected, (result.await() as AppResult.Failure).error)
            assertEquals(PlaybackConnectionError.StartNotAllowed, registry.failure.value)
            assertNull(registry.snapshot.value)
            assertTrue(connector.connections.single().commands.isEmpty())
            assertEquals(1, connector.connections.single().closes)
        }
    }

    @Test
    fun `a new same session start refusal during immediate clear is not reported as restored`() = runTest {
        withFixture(immediateMain()) {
            observeState()
            runCurrent()
            registry.startNotAllowed(session)
            runCurrent()
            var callbacks = 0
            onFailureCleared { callbacks++; registry.startNotAllowed(session) }

            val result = backgroundScope.async { client.reconnect() }
            runCurrent()

            assertEquals(1, callbacks)
            assertEquals(PlaybackConnectionError.StartNotAllowed, (result.await() as AppResult.Failure).error)
            assertEquals(PlaybackConnectionError.StartNotAllowed, registry.failure.value)
            assertEquals(0, connector.connections.single().closes)
            assertTrue(connector.connections.single().commands.isEmpty())
        }
    }

    // A diagnostic field prevents coroutine debug stack recovery from copying a standard exception.
    private class DiagnosticConnectorFailure(message: String, val diagnostic: String) : IllegalArgumentException(message)

    private fun TestScope.immediateMain() = HandlerLikeImmediateDispatcher(StandardTestDispatcher(testScheduler))

    private suspend fun TestScope.withFixture(
        main: CoroutineDispatcher = StandardTestDispatcher(testScheduler, "reconnect-main"),
        engineProblem: Boolean = false,
        block: suspend Fixture.() -> Unit,
    ) {
        val fixture = Fixture(this, main, engineProblem)
        try { fixture.block() }
        finally {
            fixture.collectors.forEach { it.cancel() }
            runCurrent()
            fixture.appJob.cancel()
            runCurrent()
        }
    }

    private class Fixture(private val scope: TestScope, val main: CoroutineDispatcher, engineProblem: Boolean) {
        val appJob = SupervisorJob(scope.backgroundScope.coroutineContext.job)
        val registry = PlaybackServiceRegistry()
        val connector = FakeConnector()
        val client = SharedPlaybackClient(
            main, registry, connector, CoroutineScope(scope.backgroundScope.coroutineContext + appJob),
            connectionTimeoutMs = 1_000,
        )
        val collectors = mutableListOf<Job>()
        val session = PlaybackSessionId("session")
        private val entry = QueueEntry(QueueEntryId("entry"), MediaTrack(TrackId("track"), "Track", null, null, 10_000))
        private val token = PlaybackItemToken(session, entry.entryId, 0)
        val anchor = PlaybackProgress(token, 0, 2_000, 10_000, 4_000, 0, false)
        val snapshot = PlaybackSnapshot.create(
            sessionId = session,
            queue = PlaybackQueue.create(entries = listOf(entry), current = token),
            transport = if (engineProblem) PlaybackTransport(problem = PlaybackProblem(
                PlaybackProblemId("engine-problem"), PlaybackProblemCode.DecoderFailure, token,
            )) else PlaybackTransport(phase = PlaybackPhase.Ready, playRequested = true),
            capabilities = if (engineProblem) setOf(PlaybackCapability.Retry) else emptySet(),
        )
        var samples = 0

        init {
            registry.attach(session) { samples++; anchor }
            registry.publish(snapshot, anchor)
        }

        fun observeState(): Collected<PlaybackConnection> {
            val values = mutableListOf<PlaybackConnection>()
            val job = scope.backgroundScope.launch { client.observeState().collect { values += it } }
            collectors += job
            return Collected(job, values)
        }

        fun onFailureCleared(action: () -> Unit) {
            check(registry.failure.value != null)
            collectors += scope.backgroundScope.launch(main, start = CoroutineStart.UNDISPATCHED) {
                var refused = true
                registry.failure.collect { failure ->
                    if (failure != null) refused = true
                    else if (refused) {
                        refused = false
                        action()
                    }
                }
            }
        }
    }

    private class Collected<T>(val job: Job, val values: List<T>)

    private class FakeConnector : PlaybackConnector {
        var calls = 0
        var sessionId = PlaybackSessionId("session")
        var beforeConnect: suspend () -> Unit = {}
        var onConnected: (FakeConnection) -> Unit = {}
        val connections = mutableListOf<FakeConnection>()
        override suspend fun connect(onDisconnected: () -> Unit): PlaybackClientConnection {
            calls++
            beforeConnect()
            return FakeConnection(sessionId, onDisconnected).also {
                connections += it
                onConnected(it)
            }
        }
    }

    private class FakeConnection(
        override val sessionId: PlaybackSessionId,
        private val onDisconnected: () -> Unit,
    ) : PlaybackClientConnection {
        val commands = mutableListOf<PlaybackCommand>()
        var closes = 0
        var beforeClose: () -> Unit = {}
        override suspend fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> {
            commands += command
            return AppResult.Success(Unit)
        }
        fun disconnect() = onDisconnected()
        override fun close() {
            closes++
            beforeClose()
            onDisconnected()
        }
    }

    /** Matches Main.immediate reentry without the Unconfined dispatcher's trampoline. */
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
