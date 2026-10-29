package com.zaus.nullwave.core.playback.media3.testing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTestCleanupTest {
    @Test
    fun bodyFailureKeepsEveryCleanupFailureAndRunsRemainingSteps(): Unit = runBlocking {
        val primary = DiagnosticFailure("Scenario failed", "scenario")
        val first = DiagnosticFailure("Service cleanup failed", "service")
        val second = DiagnosticFailure("Activity cleanup failed", "activity")
        val steps = mutableListOf<String>()

        val actual = runCatching {
            withPlaybackTestCleanup(
                cleanup = listOf(
                    { steps += "service"; throw first },
                    { steps += "activity"; throw second },
                    { steps += "reset" },
                ),
            ) { throw primary }
        }.exceptionOrNull()

        assertSame(primary, actual)
        assertEquals(listOf(first, second), actual!!.suppressed.toList())
        assertEquals(listOf("service", "activity", "reset"), steps)
    }

    @Test
    fun successfulBodyReportsFirstCleanupFailureWithoutSkippingLaterSteps(): Unit = runBlocking {
        val first = DiagnosticFailure("Service cleanup failed", "service")
        val second = DiagnosticFailure("Activity cleanup failed", "activity")
        val steps = mutableListOf<String>()

        val actual = runCatching {
            withPlaybackTestCleanup(
                cleanup = listOf(
                    { steps += "service"; throw first },
                    { steps += "activity"; throw second },
                    { steps += "reset" },
                ),
            ) { "passed" }
        }.exceptionOrNull()

        assertSame(first, actual)
        assertEquals(listOf(second), actual!!.suppressed.toList())
        assertEquals(listOf("service", "activity", "reset"), steps)
    }

    @Test
    fun successfulCleanupPreservesTheBodyResultAndOrder(): Unit = runBlocking {
        val steps = mutableListOf<String>()
        val expected = Any()

        val actual = withPlaybackTestCleanup(
            cleanup = listOf({ steps += "first" }, { steps += "second" }),
        ) {
            steps += "body"
            expected
        }

        assertSame(expected, actual)
        assertEquals(listOf("body", "first", "second"), steps)
    }

    @Test
    fun cancellationStillRunsSuspendingCleanupAndPreservesItsDiagnostic(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val steps = mutableListOf<String>()
        val cleanupFailure = DiagnosticFailure("Cleanup failed", "cleanup")
        var caught: Throwable? = null
        val caller = async {
            try {
                withPlaybackTestCleanup(
                    cleanup = listOf(
                        { yield(); steps += "release"; throw cleanupFailure },
                        { yield(); steps += "reset" },
                    ),
                ) {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            } catch (failure: Throwable) {
                caught = failure
                throw failure
            }
        }
        entered.await()

        caller.cancel(CancellationException("UI test cancelled"))
        caller.join()

        assertTrue(caller.isCancelled)
        assertTrue(caught is CancellationException)
        assertEquals("UI test cancelled", caught!!.message)
        assertEquals(listOf(cleanupFailure), caught.suppressed.toList())
        assertEquals(listOf("release", "reset"), steps)
    }

    private class DiagnosticFailure(message: String, val diagnostic: String) : IllegalStateException(message)
}
