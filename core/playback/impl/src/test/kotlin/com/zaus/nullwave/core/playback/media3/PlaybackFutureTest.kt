package com.zaus.nullwave.core.playback.media3

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The client may stop waiting after handoff; cancelling its coroutine must not revoke service work. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackFutureTest {
    @Test
    fun `cancelled waiter leaves handed-off future active and another waiter can receive its result`() = runTest {
        val future = SettableFuture.create<String>()
        val first = backgroundScope.async { future.awaitPlaybackResult() }
        runCurrent()
        first.cancel()
        runCurrent()
        assertFalse(future.isCancelled)
        assertFalse(future.isDone)

        val second = backgroundScope.async { future.awaitPlaybackResult() }
        runCurrent()
        future.set("service completed")
        runCurrent()
        assertTrue(first.isCancelled)
        assertEquals("service completed", second.await())
        assertFalse(future.isCancelled)
    }

    @Test
    fun `future execution wrapper does not replace the original failure`() = runTest {
        val original = DiagnosticFailure("service failure", "decoder:17")
        val cleanup = IllegalArgumentException("cleanup failure")
        original.addSuppressed(cleanup)
        val future = SettableFuture.create<String>().apply { setException(original) }
        val actual = runCatching { future.awaitPlaybackResult() }.exceptionOrNull()
            ?: throw AssertionError("Expected the service exception")
        assertSame(original, actual)
        assertEquals("decoder:17", (actual as DiagnosticFailure).diagnostic)
        assertEquals(listOf(cleanup), actual.suppressed.toList())
    }

    @Test
    fun `cancelled waiter detaches before either a successful or failed future completes`() = runTest {
        for (fails in listOf(false, true)) {
            val service = SettableFuture.create<String>()
            val future = ReadCountingFuture(service)
            val caller = backgroundScope.async { future.awaitPlaybackResult() }
            runCurrent()
            caller.cancel()
            runCurrent()

            if (fails) service.setException(IllegalStateException("Late service failure"))
            else service.set("Late service success")
            runCurrent()

            assertEquals("A detached listener must not resolve a result for its cancelled caller", 0, future.reads)
            assertFalse(service.isCancelled)
            assertTrue(caller.isCancelled)
        }
    }

    @Test
    fun `cancellation racing completion does not cancel service or resume caller twice`() = runTest {
        val service = SettableFuture.create<String>()
        val future = ReadCountingFuture(service)
        val caller = backgroundScope.async { future.awaitPlaybackResult() }
        runCurrent()
        // Completion has already taken the waiter, but cancellation wins before resumeWith.
        future.beforeRead = { caller.cancel() }
        service.set("Accepted service result")
        runCurrent()

        assertTrue(caller.isCancelled)
        assertFalse(service.isCancelled)
        assertEquals(1, future.reads)
        future.beforeRead = {}
        assertEquals("Accepted service result", future.awaitPlaybackResult())
        assertEquals(2, future.reads)
    }

    private class ReadCountingFuture<T>(private val delegate: ListenableFuture<T>) : ListenableFuture<T> by delegate {
        var reads = 0
        var beforeRead: () -> Unit = {}

        override fun get(): T {
            reads++
            beforeRead()
            return delegate.get()
        }

        override fun get(timeout: Long, unit: TimeUnit): T {
            reads++
            beforeRead()
            return delegate.get(timeout, unit)
        }
    }

    // The diagnostic field prevents coroutines' debug stacktrace recovery from copying this error.
    private class DiagnosticFailure(message: String, val diagnostic: String) : IllegalStateException(message)
}
