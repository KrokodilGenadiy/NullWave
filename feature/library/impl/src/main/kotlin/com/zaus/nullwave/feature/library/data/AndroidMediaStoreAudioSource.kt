package com.zaus.nullwave.feature.library.data

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.provider.MediaStore
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

    override suspend fun readRows(): List<MediaStoreAudioRow> = queryAudioRows(io) { cancellation ->
        context.contentResolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            audioProjection,
            audioSelection(Build.VERSION.SDK_INT),
            null,
            null,
            cancellation,
        )
    }
}
