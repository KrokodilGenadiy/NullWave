package com.zaus.nullwave.feature.library.data

/** Platform boundary. Observation is registered before reading; each handle owns its registration. */
internal interface MediaStoreAudioSource {
    fun hasReadAccess(): Boolean
    fun observeChanges(onChanged: () -> Unit): AutoCloseable
    suspend fun readRows(): List<MediaStoreAudioRow>
}

/** Provider data remains private to the feature, including the field used only for sorting. */
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
)
