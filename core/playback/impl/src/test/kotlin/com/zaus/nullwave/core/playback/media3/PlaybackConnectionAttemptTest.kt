package com.zaus.nullwave.core.playback.media3

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.media3.client.PlaybackClientConnection
import com.zaus.nullwave.core.playback.media3.client.PlaybackConnectException
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackConnectionAttemptTest {
    @Test
    fun `successful handshake transfers the connection without releasing its resource`() = runTest {
        val attempt = Attempt()
        val call = backgroundScope.async { attempt.connect() }
        runCurrent()

        attempt.future.set(attempt.resource)
        runCurrent()

        assertSame(attempt.connection, call.await())
        assertEquals(1, attempt.factoryCalls)
        assertEquals(0, attempt.releases)
        assertEquals(0, attempt.connection.closes)
    }

    @Test
    fun `SDK security rejection is typed and retains its original diagnostic`() = runTest {
        val attempt = Attempt()
        val original = DiagnosticSecurityException("Session rejected the connection", "sdk:rejected")
        val call = backgroundScope.async { runCatching { attempt.connect() } }
        runCurrent()

        attempt.future.setException(original)
        runCurrent()

        val actual = call.await().exceptionOrNull() as PlaybackConnectException
        assertEquals(PlaybackConnectionError.ConnectionFailed, actual.reason)
        assertSame(original, actual.cause)
        assertEquals("Session rejected the connection", actual.cause?.message)
        assertEquals(1, attempt.releases)
        assertEquals(0, attempt.factoryCalls)
    }

    @Test
    fun `SDK programming failures propagate instead of becoming connection rejection`() = runTest {
        val failures = listOf(
            DiagnosticArgumentException("Wrong service declaration", "sdk:argument"),
            DiagnosticStateException("Wrong application thread", "sdk:state"),
            DiagnosticNullPointerException("Missing configuration", "sdk:null"),
        )
        for (original in failures) {
            val attempt = Attempt()
            val call = backgroundScope.async { runCatching { attempt.connect() } }
            runCurrent()

            attempt.future.setException(original)
            runCurrent()

            assertSame(original, call.await().exceptionOrNull())
            assertEquals(1, attempt.releases)
            assertEquals(0, attempt.factoryCalls)
        }
    }

    @Test
    fun `handshake programming failure releases the connected resource and propagates`() = runTest {
        val attempt = Attempt()
        val original = DiagnosticStateException("Invalid handshake configuration", "handshake:state")
        attempt.future.set(attempt.resource)

        val actual = runCatching { attempt.connect { throw original } }.exceptionOrNull()

        assertSame(original, actual)
        assertEquals(1, attempt.factoryCalls)
        assertEquals(1, attempt.releases)
    }

    @Test
    fun `security failure in handshake is not mistaken for an SDK rejection`() = runTest {
        val attempt = Attempt()
        val original = DiagnosticSecurityException("Unexpected handshake policy", "handshake:security")
        attempt.future.set(attempt.resource)

        val actual = runCatching { attempt.connect { throw original } }.exceptionOrNull()

        assertSame(original, actual)
        assertEquals(1, attempt.factoryCalls)
        assertEquals(1, attempt.releases)
    }

    @Test
    fun `explicit handshake rejection keeps its typed failure and cause`() = runTest {
        val attempt = Attempt()
        val cause = DiagnosticArgumentException("Missing session identity", "handshake:identity")
        val rejection = PlaybackConnectException(PlaybackConnectionError.ConnectionFailed, cause)
        attempt.future.set(attempt.resource)

        val actual = runCatching { attempt.connect { throw rejection } }.exceptionOrNull()

        assertSame(rejection, actual)
        assertSame(cause, actual?.cause)
        assertEquals(1, attempt.releases)
    }

    @Test
    fun `cancellation releases the pending attempt without constructing a connection`() = runTest {
        val attempt = Attempt()
        attempt.cancelOnRelease = true
        var actual: Throwable? = null
        val call = backgroundScope.launch {
            try { attempt.connect() }
            catch (failure: Throwable) {
                actual = failure
                throw failure
            }
        }
        runCurrent()

        call.cancel(CancellationException("UI connection lease ended"))
        call.join()

        assertTrue(actual is CancellationException)
        assertEquals("UI connection lease ended", actual?.message)
        assertEquals(1, attempt.releases)
        assertTrue(attempt.future.isCancelled)
        assertEquals(0, attempt.factoryCalls)
    }

    @Test
    fun `cleanup failure is suppressed under the original programming failure`() = runTest {
        val attempt = Attempt()
        val original = DiagnosticStateException("Invalid connection", "sdk:primary")
        val earlier = IllegalArgumentException("Earlier diagnostic")
        val cleanup = IllegalStateException("Release failed")
        original.addSuppressed(earlier)
        attempt.releaseFailure = cleanup
        attempt.future.setException(original)

        val actual = runCatching { attempt.connect() }.exceptionOrNull()

        assertSame(original, actual)
        assertEquals(listOf(earlier, cleanup), actual?.suppressed?.toList())
        assertEquals(1, attempt.releases)
    }

    @Test
    fun `cleanup failure does not replace a typed SDK rejection`() = runTest {
        val attempt = Attempt()
        val original = DiagnosticSecurityException("Rejected session", "sdk:primary")
        val cleanup = IllegalStateException("Release failed")
        attempt.releaseFailure = cleanup
        attempt.future.setException(original)

        val actual = runCatching { attempt.connect() }.exceptionOrNull() as PlaybackConnectException

        assertEquals(PlaybackConnectionError.ConnectionFailed, actual.reason)
        assertSame(original, actual.cause)
        assertEquals(listOf(cleanup), actual.cause?.suppressed?.toList())
        assertEquals(1, attempt.releases)
    }

    @Test
    fun `handshake rejection cleanup stays with the cause exposed to command callers`() = runTest {
        val attempt = Attempt()
        val original = DiagnosticArgumentException("Missing session identity", "handshake:primary")
        val cleanup = IllegalStateException("Release failed")
        val rejection = PlaybackConnectException(PlaybackConnectionError.ConnectionFailed, original)
        attempt.releaseFailure = cleanup
        attempt.future.set(attempt.resource)

        val actual = runCatching { attempt.connect { throw rejection } }.exceptionOrNull()

        assertSame(rejection, actual)
        assertSame(original, actual?.cause)
        assertEquals(listOf(cleanup), actual?.cause?.suppressed?.toList())
        assertEquals(1, attempt.releases)
    }

    private class Attempt {
        val resource = Any()
        val future = SettableFuture.create<Any>()
        val connection = Connection()
        var releases = 0
        var factoryCalls = 0
        var cancelOnRelease = false
        var releaseFailure: Throwable? = null

        suspend fun connect(factory: () -> PlaybackClientConnection = { connection }): PlaybackClientConnection =
            awaitPlaybackConnection(future, ::release) { received ->
                assertSame(resource, received)
                factoryCalls++
                factory()
            }

        private fun release(received: ListenableFuture<Any>) {
            assertSame(future, received)
            releases++
            if (cancelOnRelease) received.cancel(false)
            releaseFailure?.let { throw it }
        }
    }

    private class Connection : PlaybackClientConnection {
        override val sessionId = PlaybackSessionId("connection-test")
        var closes = 0
        override suspend fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> =
            AppResult.Success(Unit)
        override fun close() { closes++ }
    }

    // Extra diagnostic fields keep coroutine stacktrace recovery from copying the original failures.
    private class DiagnosticArgumentException(message: String, val diagnostic: String) : IllegalArgumentException(message)
    private class DiagnosticStateException(message: String, val diagnostic: String) : IllegalStateException(message)
    private class DiagnosticNullPointerException(message: String, val diagnostic: String) : NullPointerException(message)
    private class DiagnosticSecurityException(message: String, val diagnostic: String) : SecurityException(message)
}
