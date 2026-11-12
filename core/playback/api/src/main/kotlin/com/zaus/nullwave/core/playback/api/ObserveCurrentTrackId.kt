package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/**
 * Emits the confirmed current track ID, including while paused. An empty queue emits null.
 * Connection loss emits nothing, so a bound screen can keep its last confirmed highlight.
 */
fun PlaybackController.observeCurrentTrackId(): Flow<TrackId?> = observeState()
    .filterIsInstance<PlaybackConnection.Connected>()
    .map { it.snapshot.queue }
    .distinctUntilChanged()
    .map { queue ->
        queue.current?.let { current -> queue.entries.firstOrNull { it.entryId == current.entryId }?.track?.id }
    }
    .distinctUntilChanged()
