package com.zaus.nullwave.feature.library.data.source

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import com.zaus.nullwave.feature.library.platform.hasAudioAccess
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class AndroidMediaStorePlaybackSource(
    private val context: Context,
    private val io: CoroutineDispatcher,
) : MediaStorePlaybackSource {
    override fun hasReadAccess(): Boolean = context.hasAudioAccess()

    override suspend fun readTrack(address: MediaStoreTrackAddress): MediaStorePlaybackData? =
        readPlaybackAudio(address, io, Build.VERSION.SDK_INT) { selection, arguments, cancellation ->
            context.contentResolver.query(
                Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                audioProjection,
                selection,
                arguments,
                null,
                cancellation,
            )
        }
}

/** Targeted SQL on the merged external collection also handles a detached volume as no rows. */
internal suspend fun readPlaybackAudio(
    address: MediaStoreTrackAddress,
    io: CoroutineDispatcher,
    sdk: Int,
    query: (selection: String, arguments: Array<String>, cancellation: CancellationSignal) -> Cursor?,
): MediaStorePlaybackData? {
    val visible = audioSelection(sdk)
    val targetRows = queryAudioRows(io) { cancellation ->
        query(
            "($visible) AND ${Media._ID} = ? AND ${Media.VOLUME_NAME} = ?",
            arrayOf(address.id.toString(), address.volume),
            cancellation,
        )
    }
    currentCoroutineContext().ensureActive()
    if (targetRows.isEmpty()) return null
    val target = targetRows.singleOrNull()
        ?: throw IOException("MediaStore returned multiple rows for one track")
    if (target.id != address.id || target.volume != address.volume) {
        throw IOException("MediaStore returned a different track identity")
    }
    val albumId = target.albumId?.takeIf { it >= 0 }
    val members = if (albumId == null) emptyList() else queryAudioRows(io) { cancellation ->
        query(
            "($visible) AND ${Media.ALBUM_ID} = ? AND ${Media.VOLUME_NAME} = ?",
            arrayOf(albumId.toString(), address.volume),
            cancellation,
        )
    }
    val identities = HashSet<Long>()
    var refreshedTarget: MediaStoreAudioRow? = null
    val coroutine = currentCoroutineContext()
    for (member in members) {
        coroutine.ensureActive()
        if (member.volume != address.volume || member.albumId != albumId || member.id < 0 ||
            !identities.add(member.id)) {
            throw IOException("MediaStore returned inconsistent album membership")
        }
        if (member.id == address.id) refreshedTarget = member
    }
    if (albumId != null && refreshedTarget == null) {
        throw IOException("MediaStore track changed album membership during resolution")
    }
    coroutine.ensureActive()
    return MediaStorePlaybackData(
        row = refreshedTarget ?: target,
        albumRows = members,
        resourceUri = ContentUris.withAppendedId(Media.getContentUri(address.volume), address.id).toString(),
    )
}
