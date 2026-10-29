package com.zaus.nullwave.core.playback.media3

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceFutureTest {
    @Test
    fun `cancelling the returned future does not roll back service work`() = runTest {
        val serviceJob = SupervisorJob(backgroundScope.coroutineContext.job)
        val scope = CoroutineScope(backgroundScope.coroutineContext + serviceJob + StandardTestDispatcher(testScheduler))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var writes = 0
        try {
            val future = scope.serviceFuture {
                entered.complete(Unit)
                release.await()
                writes++
                "completed"
            }
            runCurrent()
            assertTrue(entered.isCompleted)
            assertTrue(future.cancel(false))

            release.complete(Unit)
            runCurrent()

            assertTrue(future.isCancelled)
            assertEquals(1, writes)
            assertTrue(serviceJob.isActive)
            val following = scope.serviceFuture { "following" }
            runCurrent()
            assertTrue(following.isDone)
            assertEquals("following", following.get())
        } finally {
            release.complete(Unit)
            serviceJob.cancel()
            runCurrent()
        }
    }

    @Test
    fun `parent cancellation before the lazy body starts still completes the future`() = runTest {
        val serviceJob = SupervisorJob(backgroundScope.coroutineContext.job)
        val queued = StandardTestDispatcher(testScheduler)
        val cancelBeforeStart = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                serviceJob.cancel()
                queued.dispatch(context, block)
            }
        }
        val scope = CoroutineScope(backgroundScope.coroutineContext + serviceJob + cancelBeforeStart)
        var bodyStarted = false
        try {
            val future = scope.serviceFuture { bodyStarted = true; "never" }
            runCurrent()

            assertFalse(bodyStarted)
            assertTrue(futureFailure(future) is CancellationException)
        } finally {
            serviceJob.cancel()
            runCurrent()
        }
    }

    @Test
    fun `unexpected failure preserves the original cause without cancelling the service`() = runTest {
        val serviceJob = SupervisorJob(backgroundScope.coroutineContext.job)
        val scope = CoroutineScope(backgroundScope.coroutineContext + serviceJob + StandardTestDispatcher(testScheduler))
        val original = IllegalStateException("service action failed")
        try {
            val future = scope.serviceFuture<String> { throw original }
            runCurrent()

            val failure = futureFailure(future)
            assertEquals(original.javaClass, failure.javaClass)
            assertEquals(original.message, failure.message)
            assertTrue("Original failure must remain in the cause chain", generateSequence(failure) { it.cause }.any { it === original })
            assertTrue(serviceJob.isActive)
        } finally {
            serviceJob.cancel()
            runCurrent()
        }
    }

    @Test
    fun `service cancellation during an await completes the future without applying the action`() = runTest {
        val serviceJob = SupervisorJob(backgroundScope.coroutineContext.job)
        val scope = CoroutineScope(backgroundScope.coroutineContext + serviceJob + StandardTestDispatcher(testScheduler))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var writes = 0
        try {
            val future = scope.serviceFuture {
                entered.complete(Unit)
                release.await()
                writes++
            }
            runCurrent()
            assertTrue(entered.isCompleted)

            serviceJob.cancel()
            runCurrent()

            assertTrue(futureFailure(future) is CancellationException)
            assertEquals(0, writes)
            release.complete(Unit)
            runCurrent()
            assertEquals(0, writes)
        } finally {
            release.complete(Unit)
            serviceJob.cancel()
            runCurrent()
        }
    }

    private fun futureFailure(future: ListenableFuture<*>): Throwable {
        assertTrue("A cancelled service must not leave its future unresolved", future.isDone)
        return try {
            future.get()
            throw AssertionError("Expected a failed future")
        } catch (failure: ExecutionException) {
            failure.cause ?: throw AssertionError("ExecutionException must retain its cause", failure)
        } catch (failure: CancellationException) {
            failure
        }
    }
}
