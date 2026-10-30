package com.zaus.nullwave.core.playback.media3.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the first ordinary failure; cancellation cannot hide a later release failure. */
internal class PlaybackCleanupFailures(initial: Throwable? = null) {
    var failure: Throwable? = initial
        private set

    fun attempt(release: () -> Unit) {
        try {
            release()
        } catch (failure: Throwable) {
            record(failure)
        }
    }

    suspend fun attemptSuspending(release: suspend () -> Unit) {
        try {
            release()
        } catch (failure: Throwable) {
            record(failure)
        }
    }

    fun throwIfAny() { failure?.let { throw it } }

    private fun record(next: Throwable) {
        val earlier = failure
        when {
            earlier == null -> failure = next
            earlier === next -> Unit
            earlier is CancellationException && next !is CancellationException -> {
                next.addSuppressed(earlier)
                failure = next
            }
            else -> earlier.addSuppressed(next)
        }
    }
}

/**
 * Stops platform callbacks before revoking lifetime, then waits for owner admission before release.
 * Cleanup failures are reported together by the completion coroutine, including when owner suspends.
 */
internal fun closePlaybackResources(
    scope: CoroutineScope,
    main: CoroutineDispatcher,
    beforeCancellation: List<() -> Unit>,
    closeOwner: suspend () -> Unit,
    releases: List<() -> Unit>,
): Job {
    val failures = PlaybackCleanupFailures()
    beforeCancellation.forEach(failures::attempt)
    failures.attempt { scope.cancel() }
    // The cancelled service cannot cancel this cleanup. On Main it starts without another dispatch.
    return scope.launch(main + NonCancellable, start = CoroutineStart.UNDISPATCHED) {
        failures.attemptSuspending(closeOwner)
        releases.forEach(failures::attempt)
        failures.throwIfAny()
    }
}
