package contract

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot

fun publicFactoriesAndComponents(): String {
    val request = PlaybackQueueRequest.create(listOf(TrackId("track")), startIndex = 0)
    val queue = PlaybackQueue.create()
    val snapshot = PlaybackSnapshot.create(PlaybackSessionId("session"), queue = queue)
    val (trackIds, startIndex, startPositionMs, playRequested) = request
    val (entries, playOrder, current, queueRevision, repeatMode, shuffleEnabled) = queue
    val (sessionId, revision, capturedQueue, transport, positionRevision, capabilities) = snapshot
    return listOf(
        trackIds, startIndex, startPositionMs, playRequested,
        entries, playOrder, current, queueRevision, repeatMode, shuffleEnabled,
        sessionId, revision, capturedQueue, transport, positionRevision, capabilities,
        snapshot.queue, snapshot.capabilities, request.trackIds, queue.entries, queue.playOrder,
    ).joinToString()
}
