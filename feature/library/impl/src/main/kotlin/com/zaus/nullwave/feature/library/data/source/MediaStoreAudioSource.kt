package com.zaus.nullwave.feature.library.data.source

/** Platform boundary. Observation is registered before reading; each handle owns its registration. */
internal interface MediaStoreAudioSource {
    fun hasReadAccess(): Boolean
    fun observeChanges(onChanged: () -> Unit): AutoCloseable
    suspend fun readRows(): List<MediaStoreAudioRow>
}
