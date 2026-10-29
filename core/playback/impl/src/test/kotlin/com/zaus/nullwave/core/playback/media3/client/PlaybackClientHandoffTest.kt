package com.zaus.nullwave.core.playback.media3.client

import com.google.common.util.concurrent.SettableFuture
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackClientHandoffTest {
    @Test
    fun `owner failure stays unexpected when disconnect arrives before its acknowledgement`() = runTest {
        var connected = true
        val acknowledgement = SettableFuture.create<Unit>()
        val ownerReply = CompletableDeferred<EmptyResult<PlaybackCommandError>>()
        val original = DiagnosticFailure("Install failed", "install:42")
        val cleanup = IllegalArgumentException("Cleanup failed")
        original.addSuppressed(cleanup)
        val call = backgroundScope.async {
            runCatching {
                dispatchThenAwaitPlayback(
                    isConnected = { connected },
                    dispatch = { AppResult.Success(acknowledgement) },
                    awaitReply = { delivered ->
                        delivered.awaitPlaybackResult()
                        ownerReply.await()
                    },
                )
            }
        }
        runCurrent()

        ownerReply.completeExceptionally(original)
        connected = false
        acknowledgement.set(Unit)
        runCurrent()

        val actual = call.await().exceptionOrNull()
        assertSame(original, actual)
        assertEquals("install:42", (actual as DiagnosticFailure).diagnostic)
        assertEquals(listOf(cleanup), actual.suppressed.toList())
    }

    @Test
    fun `disconnected synchronous platform failure returns its original diagnostic`() = runTest {
        val original = IllegalStateException("Controller disconnected during send")
        val outcome = dispatchThenAwaitPlayback<Unit>(
            isConnected = { false },
            dispatch = { throw original },
            awaitReply = { error("Nothing was handed off") },
        )

        assertEquals(PlaybackCommandError.ConnectionUnavailable, (outcome as AppResult.Failure).error)
        assertSame(original, outcome.cause)
    }

    @Test
    fun `connected synchronous platform failure still propagates unchanged`() = runTest {
        val original = IllegalStateException("Invalid connected dispatch")
        val actual = runCatching {
            dispatchThenAwaitPlayback<Unit>(
                isConnected = { true },
                dispatch = { throw original },
                awaitReply = { error("Nothing was handed off") },
            )
        }.exceptionOrNull()

        assertSame(original, actual)
    }

    @Test
    fun `rejected dispatch never waits for an owner reply`() = runTest {
        val rejection = AppResult.Failure(PlaybackCommandError.CommandUnavailable)
        val outcome = dispatchThenAwaitPlayback<Unit>(
            isConnected = { true },
            dispatch = { rejection },
            awaitReply = { error("Rejected command has no owner reply") },
        )

        assertSame(rejection, outcome)
    }

    // The diagnostic field prevents coroutines' debug stacktrace recovery from copying this error.
    private class DiagnosticFailure(message: String, val diagnostic: String) : IllegalStateException(message)
}
