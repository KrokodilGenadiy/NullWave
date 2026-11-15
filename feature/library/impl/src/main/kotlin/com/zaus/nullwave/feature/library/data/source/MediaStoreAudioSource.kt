package com.zaus.nullwave.feature.library.data.source

import com.zaus.nullwave.feature.library.api.model.album.AlbumId

/** Platform boundary. Observation is registered before reading; each handle owns its registration. */
internal interface MediaStoreAudioSource {
    fun hasReadAccess(): Boolean
    fun observeChanges(onChanged: () -> Unit): AutoCloseable
    /** Album reads include the physical album members needed for consistent artwork revisions. */
    suspend fun readRows(albumId: AlbumId? = null): List<MediaStoreAudioRow>
}
