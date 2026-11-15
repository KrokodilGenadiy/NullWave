package com.zaus.nullwave.feature.library.data.source

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.platform.hasAudioAccess
import kotlinx.coroutines.CoroutineDispatcher

internal class AndroidMediaStoreAudioSource(
    private val context: Context,
    private val io: CoroutineDispatcher,
) : MediaStoreAudioSource {
    override fun hasReadAccess(): Boolean = context.hasAudioAccess()

    override fun observeChanges(onChanged: () -> Unit): AutoCloseable {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) = onChanged()
        }
        resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        return AutoCloseable { resolver.unregisterContentObserver(observer) }
    }

    override suspend fun readRows(albumId: AlbumId?): List<MediaStoreAudioRow> =
        queryCatalogRows(albumId, Build.VERSION.SDK_INT, io) { projection, selection, arguments, cancellation ->
            context.contentResolver.query(
                Media.getContentUri(MediaStore.VOLUME_EXTERNAL), projection, selection, arguments, null, cancellation,
            )
        }
}

internal suspend fun queryCatalogRows(
    albumId: AlbumId?,
    sdk: Int,
    io: CoroutineDispatcher,
    query: (projection: Array<String>, selection: String, args: Array<String>?, signal: CancellationSignal) -> Cursor?,
): List<MediaStoreAudioRow> = queryAudioRows(io) { signal ->
    val visible = audioSelection(sdk)
    val projection = catalogProjection(sdk)
    if (albumId == null) return@queryAudioRows query(projection, visible, null, signal)
    val detailsProjection = projection + Media.TRACK
    val onVolume = "($visible) AND ${Media.VOLUME_NAME} = ?"
    val volume = albumId.value.substringBefore(':')
    val providerId = albumId.value.substringAfter(':', "").toLongOrNull()
    if (providerId != null) {
        query(detailsProjection, "$onVolume AND ${Media.ALBUM_ID} = ?", arrayOf(volume, providerId.toString()), signal)
    } else {
        // A Download group can span provider albums, whose other members also contribute artwork.
        query(detailsProjection, onVolume, arrayOf(volume), signal)
    }
}
