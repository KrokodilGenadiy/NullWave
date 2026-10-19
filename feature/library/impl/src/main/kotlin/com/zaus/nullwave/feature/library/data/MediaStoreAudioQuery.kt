package com.zaus.nullwave.feature.library.data

import android.database.Cursor
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore.Audio.Media
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

internal val audioProjection: Array<String>
    get() = arrayOf(
        Media._ID, Media.VOLUME_NAME, Media.TITLE, Media.DISPLAY_NAME,
        Media.ARTIST, Media.ALBUM, Media.DURATION, Media.DATE_ADDED,
    )

internal fun audioSelection(sdk: Int): String = if (sdk >= 30) {
    "${Media.IS_PENDING} = 0 AND ${Media.IS_TRASHED} = 0"
} else {
    "${Media.IS_PENDING} = 0"
}

/** Await cancellation signals the blocking provider before coroutineScope joins its worker. */
internal suspend fun queryAudioRows(
    io: CoroutineDispatcher,
    query: (CancellationSignal) -> Cursor?,
): List<MediaStoreAudioRow> = coroutineScope {
    val cancellation = CancellationSignal()
    val worker = async(io) {
        try {
            cancellation.throwIfCanceled()
            val cursor = query(cancellation) ?: throw IOException("MediaStore audio query returned no cursor")
            cursor.use { it.readRows(cancellation) }
        } catch (failure: OperationCanceledException) {
            currentCoroutineContext().ensureActive()
            throw failure
        }
    }
    try {
        worker.await()
    } finally {
        if (!currentCoroutineContext().isActive) cancellation.cancel()
    }
}

private fun Cursor.readRows(cancellation: CancellationSignal): List<MediaStoreAudioRow> {
    val id = getColumnIndexOrThrow(Media._ID)
    val volume = getColumnIndexOrThrow(Media.VOLUME_NAME)
    val title = getColumnIndexOrThrow(Media.TITLE)
    val displayName = getColumnIndexOrThrow(Media.DISPLAY_NAME)
    val artist = getColumnIndexOrThrow(Media.ARTIST)
    val album = getColumnIndexOrThrow(Media.ALBUM)
    val duration = getColumnIndexOrThrow(Media.DURATION)
    val added = getColumnIndexOrThrow(Media.DATE_ADDED)
    return buildList {
        while (true) {
            cancellation.throwIfCanceled()
            if (!moveToNext()) break
            if (isNull(id)) throw IOException("MediaStore audio row has no identity")
            add(MediaStoreAudioRow(
                id = getLong(id),
                volume = getString(volume),
                title = getString(title),
                displayName = getString(displayName),
                artist = getString(artist),
                album = getString(album),
                durationMs = if (isNull(duration)) null else getLong(duration),
                dateAddedSeconds = if (isNull(added)) null else getLong(added),
            ))
        }
    }
}
