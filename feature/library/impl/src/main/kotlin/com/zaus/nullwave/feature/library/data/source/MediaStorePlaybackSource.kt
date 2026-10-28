package com.zaus.nullwave.feature.library.data.source

/** One-shot platform boundary; resolving a track does not register a catalog observer. */
internal interface MediaStorePlaybackSource {
    fun hasReadAccess(): Boolean
    suspend fun readTrack(address: MediaStoreTrackAddress): MediaStorePlaybackData?
}

/** Raw source snapshot, including complete album membership for the shared artwork revision. */
internal data class MediaStorePlaybackData(
    val row: MediaStoreAudioRow,
    val albumRows: List<MediaStoreAudioRow>,
    val resourceUri: String,
)
