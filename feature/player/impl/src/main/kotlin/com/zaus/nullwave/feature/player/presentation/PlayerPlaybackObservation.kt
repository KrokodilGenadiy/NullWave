package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.runningFold

internal data class PlayerPlaybackUpdate(val connection: PlaybackConnection, val progress: PlaybackProgress?)

/** Each UI collection owns its candidates; no detached scope, timer or durable position cache. */
internal fun PlaybackController.observePlayerPlayback(): Flow<PlayerPlaybackUpdate> = flow {
    // Cached metadata cannot authorize controls while the new binding revalidates the service.
    emit(PlayerPlaybackUpdate(PlaybackConnection.Connecting, null))
    val anchors = observeProgress().runningFold<PlaybackProgress?, PlaybackProgress?>(null) { previous, next ->
        if (previous != null && next != null && previous.target == next.target &&
            previous.positionRevision == next.positionRevision && next.sampledAtMs < previous.sampledAtMs
        ) previous else next
    }
    var lastRaw: PlaybackProgress? = null
    var effective: PlaybackProgress? = null
    var lastControl: PlaybackSnapshot? = null
    emitAll(combine(observeState(), anchors) { connection, raw ->
        if (raw !== lastRaw) {
            lastRaw = raw
            effective = raw
        }
        val snapshot = (connection as? PlaybackConnection.Connected)?.snapshot
        // A control-only resume must not reanimate the sample frozen by Pause/Buffering/suppression.
        // A future-generation anchor must not be frozen by the old generation's paused control.
        val freeze = if (snapshot != null) {
            raw?.matches(snapshot) == true && !snapshot.transport.isPlaying
        } else {
            lastControl?.let { raw?.matches(it) } == true
        }
        if (freeze && effective?.advancing == true) effective = effective?.copy(advancing = false)
        lastControl = snapshot
        PlayerPlaybackUpdate(connection, effective?.takeIf { snapshot != null && it.matches(snapshot) })
    })
}
