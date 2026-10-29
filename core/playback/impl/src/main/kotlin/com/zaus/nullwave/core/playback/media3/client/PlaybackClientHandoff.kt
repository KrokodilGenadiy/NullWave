package com.zaus.nullwave.core.playback.media3.client

import com.google.common.util.concurrent.ListenableFuture
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.suspendCancellableCoroutine

/** A disconnect may classify a platform dispatch failure, never an already accepted owner failure. */
internal suspend fun <T> dispatchThenAwaitPlayback(
    isConnected: () -> Boolean,
    dispatch: () -> AppResult<T, PlaybackCommandError>,
    awaitReply: suspend (T) -> EmptyResult<PlaybackCommandError>,
): EmptyResult<PlaybackCommandError> {
    val sent = try { dispatch() }
    catch (failure: IllegalStateException) {
        if (isConnected()) throw failure
        return AppResult.Failure(PlaybackCommandError.ConnectionUnavailable, failure)
    }
    return when (sent) {
        is AppResult.Failure -> sent
        is AppResult.Success -> awaitReply(sent.value)
    }
}

/** Cancellation detaches this waiter; the accepted service future keeps running. */
internal suspend fun <T> ListenableFuture<T>.awaitPlaybackResult(): T = suspendCancellableCoroutine { continuation ->
    val waiting = AtomicReference(continuation)
    continuation.invokeOnCancellation { waiting.set(null) }
    addListener({
        val active = waiting.getAndSet(null) ?: return@addListener
        val result = try { Result.success(get()) }
        catch (failure: ExecutionException) { Result.failure(failure.cause ?: failure) }
        catch (failure: Throwable) { Result.failure(failure) }
        active.resumeWith(result)
    }, Executor(Runnable::run))
}
