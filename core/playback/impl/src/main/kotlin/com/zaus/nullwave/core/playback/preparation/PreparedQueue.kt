package com.zaus.nullwave.core.playback.preparation

import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack

/**
 * Complete resources in the caller's occurrence order. Selection and playback intent belong to
 * [request]; service/session identities are assigned only by the later playback owner at commit.
 */
@ConsistentCopyVisibility
internal data class PreparedQueue private constructor(
    val request: PlaybackQueueRequest,
    val items: List<ResolvedPlaybackTrack>,
) {
    companion object {
        fun create(request: PlaybackQueueRequest, items: List<ResolvedPlaybackTrack>): PreparedQueue {
            val snapshot = items.toList()
            require(snapshot.size == request.trackIds.size) { "Every requested occurrence must be prepared." }
            require(snapshot.indices.all { snapshot[it].track.id == request.trackIds[it] }) {
                "Prepared resources must retain the requested track order."
            }
            val protectedItems = object : AbstractList<ResolvedPlaybackTrack>() {
                override val size: Int get() = snapshot.size
                override fun get(index: Int): ResolvedPlaybackTrack = snapshot[index]
            }
            return PreparedQueue(request, protectedItems)
        }
    }
}
