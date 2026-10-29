package com.zaus.nullwave.core.playback.media3.testing

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Releases every fixture resource without hiding the scenario's original failure. */
internal suspend fun <T> withPlaybackTestCleanup(
    cleanup: List<suspend () -> Unit>,
    block: suspend () -> T,
): T {
    var primary: Throwable? = null
    try {
        return block()
    } catch (failure: Throwable) {
        primary = failure
        throw failure
    } finally {
        var reported = primary
        withContext(NonCancellable) {
            for (release in cleanup) {
                try {
                    release()
                } catch (failure: Throwable) {
                    val earlier = reported
                    if (earlier == null) reported = failure
                    else if (failure !== earlier) earlier.addSuppressed(failure)
                }
            }
        }
        if (primary == null) reported?.let { throw it }
    }
}
