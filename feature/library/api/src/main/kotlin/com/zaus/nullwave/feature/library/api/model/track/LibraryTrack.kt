package com.zaus.nullwave.feature.library.api.model.track

import com.zaus.nullwave.feature.library.api.model.ArtworkRef

/** Catalog metadata, independent of Android URIs, formatted UI text and playback state. */
data class LibraryTrack(
    val id: TrackId,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    /** Null means the provider does not know the size, not a zero-byte file. */
    val sizeBytes: Long? = null,
    val artwork: ArtworkRef? = null,
) {
    init {
        require(title.isNotBlank()) { "Track title must not be blank; use the file display name as fallback." }
        require(artist == null || artist.isNotBlank()) { "Unknown artist must be null." }
        require(album == null || album.isNotBlank()) { "Unknown album must be null." }
        require(durationMs == null || durationMs >= 0) { "Unknown duration must be null, not negative." }
        require(sizeBytes == null || sizeBytes >= 0) { "Unknown size must be null, not negative." }
    }
}
