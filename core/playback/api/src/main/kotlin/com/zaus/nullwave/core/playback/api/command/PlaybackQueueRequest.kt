package com.zaus.nullwave.core.playback.api.command

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.internal.snapshotList

/**
 * Captures the caller's order and start selection. Repeated track identities are intentional.
 * Use [create] to capture a protected collection snapshot; the constructor and generated copy are private.
 */
@ConsistentCopyVisibility
data class PlaybackQueueRequest private constructor(
    val trackIds: List<TrackId>,
    val startIndex: Int,
    val startPositionMs: Long,
    val playRequested: Boolean,
) {
    init {
        require(trackIds.isNotEmpty()) { "Use StopAndClear to clear playback, not an empty replacement." }
        require(startIndex in trackIds.indices) { "Start index must refer to the requested queue." }
        require(startPositionMs >= 0) { "Start position must not be negative." }
    }

    companion object {
        fun create(
            trackIds: List<TrackId>,
            startIndex: Int,
            startPositionMs: Long = 0,
            playRequested: Boolean = true,
        ): PlaybackQueueRequest = PlaybackQueueRequest(
            trackIds = trackIds.snapshotList(),
            startIndex = startIndex,
            startPositionMs = startPositionMs,
            playRequested = playRequested,
        )
    }
}
