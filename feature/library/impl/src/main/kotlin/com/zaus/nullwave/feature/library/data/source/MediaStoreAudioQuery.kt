package com.zaus.nullwave.feature.library.data.source

import android.database.Cursor
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore.Audio.Media
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

internal val audioProjection: Array<String>
    get() = arrayOf(
        Media._ID, Media.VOLUME_NAME, Media.TITLE, Media.DISPLAY_NAME,
        Media.ARTIST, Media.ALBUM, Media.DURATION, Media.DATE_ADDED,
        Media.ALBUM_ID, Media.SIZE, Media.DATE_MODIFIED, Media.ARTIST_ID,
    )

internal fun audioSelection(sdk: Int): String = if (sdk >= 30) {
    "${Media.IS_PENDING} = 0 AND ${Media.IS_TRASHED} = 0"
} else {
    "${Media.IS_PENDING} = 0"
}

internal fun catalogProjection(sdk: Int): Array<String> = if (sdk >= 30) {
    audioProjection + arrayOf(Media.GENERATION_MODIFIED, Media.RELATIVE_PATH, Media.ALBUM_ARTIST)
} else {
    audioProjection
}

/** Await cancellation signals the blocking provider before coroutineScope joins its worker. */
internal suspend fun queryAudioRows(
    io: CoroutineDispatcher,
    query: (CancellationSignal) -> Cursor?,
): List<MediaStoreAudioRow> = withMediaStoreCancellation(io) { signal ->
    val cursor = query(signal) ?: throw IOException("MediaStore audio query returned no cursor")
    cursor.use { it.readRows(signal) }
}

/** Keep cursor/descriptor ownership inside [block], including when returning across dispatchers. */
internal suspend fun <T> withMediaStoreCancellation(
    io: CoroutineDispatcher,
    block: suspend (CancellationSignal) -> T,
): T = coroutineScope {
    val cancellation = CancellationSignal()
    val worker = async(io) {
        try {
            cancellation.throwIfCanceled()
            block(cancellation)
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
    val albumId = getColumnIndexOrThrow(Media.ALBUM_ID)
    val size = getColumnIndexOrThrow(Media.SIZE)
    val modified = getColumnIndexOrThrow(Media.DATE_MODIFIED)
    val artistId = getColumnIndexOrThrow(Media.ARTIST_ID)
    val generation = getColumnIndex(Media.GENERATION_MODIFIED)
    val relativePath = getColumnIndex(Media.RELATIVE_PATH)
    val albumArtist = getColumnIndex(Media.ALBUM_ARTIST)
    // Download grouping needs both columns: without ALBUM_ARTIST (Android 10) every row would look
    // like it has no album artist, so the folder is left unread and rows keep their provider album.
    val track = getColumnIndex(Media.TRACK)
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
                albumId = if (isNull(albumId)) null else getLong(albumId),
                sizeBytes = if (isNull(size)) null else getLong(size),
                dateModifiedSeconds = if (isNull(modified)) null else getLong(modified),
                artistId = if (isNull(artistId)) null else getLong(artistId),
                generationModified = if (generation >= 0 && !isNull(generation)) getLong(generation) else null,
                relativePath = if (relativePath >= 0 && albumArtist >= 0) getString(relativePath) else null,
                albumArtist = if (albumArtist >= 0) getString(albumArtist) else null,
                trackNumber = if (track >= 0 && !isNull(track)) getLong(track) else null,
            ))
        }
    }
}
