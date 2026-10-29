package com.zaus.nullwave.core.playback.media3.client

import com.google.common.util.concurrent.ListenableFuture
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError

/** Owns the SDK future until a validated connection takes over its controller. Called on Main. */
internal suspend fun <T> awaitPlaybackConnection(
    future: ListenableFuture<T>,
    release: (ListenableFuture<T>) -> Unit,
    connection: (T) -> PlaybackClientConnection,
): PlaybackClientConnection {
    try {
        val controller = try {
            future.awaitPlaybackResult()
        } catch (failure: SecurityException) {
            // Media3 reports rejected/unreachable sessions through the connection future.
            throw PlaybackConnectException(PlaybackConnectionError.ConnectionFailed, failure)
        }
        // Our handshake and configuration failures must not become expected SDK rejections.
        return connection(controller)
    } catch (failure: Throwable) {
        // Cancellation also releases a controller that completes after its last UI lease ends.
        try {
            release(future)
        } catch (cleanup: Throwable) {
            // The public typed result exposes this original cause rather than its adapter wrapper.
            val diagnostic = if (failure is PlaybackConnectException) failure.cause ?: failure else failure
            if (cleanup !== failure && cleanup !== diagnostic) diagnostic.addSuppressed(cleanup)
        }
        throw failure
    }
}
