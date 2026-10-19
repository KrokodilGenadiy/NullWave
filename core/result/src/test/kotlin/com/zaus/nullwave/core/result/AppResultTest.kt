package com.zaus.nullwave.core.result

import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AppResultTest {
    @Test
    fun `both branches widen covariantly without losing the original result`() {
        val success = AppResult.Success("track")
        val failure = AppResult.Failure(DataError.NotFound)
        val results: List<AppResult<Any, Any>> = listOf(success, failure)

        assertSame(success, results[0])
        assertSame(failure, results[1])
        assertEquals("track", results[0].fold({ it }, { error("Unexpected failure") }))
        assertEquals(DataError.NotFound, results[1].fold({ error("Unexpected success") }, { it.error }))
    }

    @Test
    fun `success mapping transforms only the success branch`() {
        val success: AppResult<Int, DataError> = AppResult.Success(2)
        val failure: AppResult<Int, DataError> = AppResult.Failure(DataError.Io)
        var calls = 0

        val transformed = success.map { calls++; "track-$it" }
        val unchanged = failure.map { calls++; "track-$it" }

        assertEquals(AppResult.Success("track-2"), transformed)
        assertSame(failure, unchanged)
        assertEquals(1, calls)
    }

    @Test
    fun `failure retains original throwable stack and cause chain`() {
        val root = IllegalStateException("device unavailable")
        val original = IOException("Cannot read track", root)
        val suppressed = IOException("Close failed")
        original.addSuppressed(suppressed)
        val stack = original.stackTrace.toList()

        val failure = AppResult.Failure(DataError.Io, original)

        assertSame(original, failure.cause)
        assertSame(root, failure.cause?.cause)
        assertSame(suppressed, failure.cause?.suppressed?.single())
        assertEquals(stack, failure.cause?.stackTrace?.toList())
        assertEquals("Cannot read track", failure.originalMessage)
        assertSame(failure, failure.map { error("Failure must not run a success mapper") })
    }

    @Test
    fun `diagnostics allow missing exception message and explicit messages without exception`() {
        val withoutDiagnostics = AppResult.Failure(DataError.NotFound)
        val withoutMessage = AppResult.Failure(DataError.Io, IOException())
        val withDetails = AppResult.Failure(DataError.AccessDenied, originalMessage = "Provider denied URI")

        assertNull(withoutDiagnostics.cause)
        assertNull(withoutDiagnostics.originalMessage)
        assertNull(withoutMessage.originalMessage)
        assertNull(withDetails.cause)
        assertEquals("Provider denied URI", withDetails.originalMessage)
    }

    @Test
    fun `error mapping preserves captured message even when throwable message changes`() {
        val original = object : IOException() {
            var diagnostic = "original detail"
            override val message: String get() = diagnostic
        }
        val failure = AppResult.Failure(DataError.Io, original)
        original.diagnostic = "later detail"

        val transformed = failure.mapError { "storage:$it" } as AppResult.Failure<String>

        assertEquals("storage:Io", transformed.error)
        assertSame(original, transformed.cause)
        assertEquals("original detail", transformed.originalMessage)
    }

    @Test
    fun `error mapping preserves explicit message override including null`() {
        val original = IOException("exception detail")
        val overridden = AppResult.Failure(DataError.Io, original, "adapter detail")
        val explicitNull = AppResult.Failure(DataError.Io, original, null)

        val mappedOverride = overridden.mapError { DataError.NotFound } as AppResult.Failure<DataError>
        val mappedNull = explicitNull.mapError { DataError.NotFound } as AppResult.Failure<DataError>

        assertSame(original, mappedOverride.cause)
        assertEquals("adapter detail", mappedOverride.originalMessage)
        assertSame(original, mappedNull.cause)
        assertNull(mappedNull.originalMessage)
    }

    @Test
    fun `error mapping never invokes its mapper for success`() {
        val success: AppResult<String, DataError> = AppResult.Success("track")

        val unchanged = success.mapError { error("Success must not run an error mapper") }

        assertSame(success, unchanged)
    }

    @Test
    fun `branch observers preserve identity and expose diagnostics`() {
        val success: AppResult<Int, DataError> = AppResult.Success(7)
        val cause = IOException("Original detail")
        val failure: AppResult<Int, DataError> = AppResult.Failure(DataError.Io, cause)
        var successCalls = 0
        var failureCalls = 0

        val observedSuccess = success
            .onSuccess { successCalls++; assertEquals(7, it) }
            .onFailure { error("Success must not run failure observer") }
        val observedFailure = failure
            .onSuccess { error("Failure must not run success observer") }
            .onFailure {
                failureCalls++
                assertSame(failure, it)
                assertSame(cause, it.cause)
                assertEquals("Original detail", it.originalMessage)
            }

        assertSame(success, observedSuccess)
        assertSame(failure, observedFailure)
        assertEquals(1, successCalls)
        assertEquals(1, failureCalls)
    }

    @Test
    fun `fold selects one branch and gives the failure callback all diagnostics`() {
        val success: AppResult<Int, DataError> = AppResult.Success(3)
        val failure: AppResult<Int, DataError> = AppResult.Failure(
            DataError.NotFound,
            originalMessage = "Missing content URI",
        )

        assertEquals("3 tracks", success.fold({ "$it tracks" }, { error("Unexpected failure") }))
        assertEquals("Missing content URI", failure.fold({ error("Unexpected success") }, {
            assertSame(failure, it)
            it.originalMessage
        }))
    }

    @Test
    fun `empty result discards the successful payload and preserves a failure instance`() {
        val success: AppResult<String, DataError> = AppResult.Success("stored row")
        val failure: AppResult<String, DataError> = AppResult.Failure(DataError.StorageFull)

        val empty: EmptyResult<DataError> = success.asEmptyResult()

        assertEquals(AppResult.Success(Unit), empty)
        assertSame(failure, failure.asEmptyResult())
    }

    @Test
    fun `helpers propagate callback programming errors without wrapping or classifying them`() {
        val original = IllegalStateException("Broken callback")
        callbackInvocations(original).forEach { invoke ->
            assertSame(original, assertThrows(IllegalStateException::class.java) { invoke() })
        }
    }

    @Test
    fun `helpers propagate cancellation without wrapping or classifying it`() {
        val cancellation = CancellationException("Operation replaced")
        callbackInvocations(cancellation).forEach { invoke ->
            assertSame(cancellation, assertThrows(CancellationException::class.java) { invoke() })
        }
    }

    private fun callbackInvocations(exception: Exception): List<() -> Any?> {
        val success: AppResult<Int, DataError> = AppResult.Success(1)
        val failure: AppResult<Int, DataError> = AppResult.Failure(DataError.Io)
        return listOf(
            { success.map { throw exception } },
            { failure.mapError { throw exception } },
            { success.onSuccess { throw exception } },
            { failure.onFailure { throw exception } },
            { success.fold({ throw exception }, { error("Unexpected failure") }) },
            { failure.fold({ error("Unexpected success") }, { throw exception }) },
        )
    }
}
