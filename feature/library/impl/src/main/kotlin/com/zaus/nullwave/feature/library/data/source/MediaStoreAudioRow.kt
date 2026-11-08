package com.zaus.nullwave.feature.library.data.source

/** Raw provider columns, kept behind the repository boundary. Nullable IDs are not zero IDs. */
internal data class MediaStoreAudioRow(
    val id: Long,
    val volume: String?,
    val title: String?,
    val displayName: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val dateAddedSeconds: Long?,
    val albumId: Long? = null,
    val sizeBytes: Long? = null,
    val dateModifiedSeconds: Long? = null,
    val artistId: Long? = null,
    val generationModified: Long? = null,
)
