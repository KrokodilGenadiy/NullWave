package com.zaus.nullwave.core.playback.api.source

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.result.AppResult

/**
 * Consumer-owned source port. The catalog feature supplies its implementation; core never depends
 * on a feature. Resolving is main-safe and checks fresh access/identity, without assuming the file
 * remains openable. Expected failures preserve diagnostics; caller cancellation must propagate.
 * A successful result must retain the requested track identity; the playback owner verifies this
 * correlation before committing a prepared queue, independently of URI validation.
 */
interface PlaybackSourceResolver {
    suspend fun resolveTrack(id: TrackId): AppResult<ResolvedPlaybackTrack, PlaybackSourceError>
}

enum class PlaybackSourceError { AccessDenied, NotFound, Io }

/**
 * Fresh metadata and a resource URI for the internal audio path, not a path or a parsed TrackId.
 * The platform boundary validates supported schemes and escaping before construction. This value
 * must not be put in UI/SavedState snapshots, and does not guarantee permission at playback time.
 */
data class ResolvedPlaybackTrack(val track: MediaTrack, val resourceUri: String) {
    init { require(resourceUri.isNotBlank()) { "An audio resource URI must not be blank." } }
}
