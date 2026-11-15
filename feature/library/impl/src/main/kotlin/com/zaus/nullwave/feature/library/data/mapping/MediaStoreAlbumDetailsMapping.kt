package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumDetails
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import java.io.IOException
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun List<MediaStoreAudioRow>.toAlbumDetails(
    albumId: AlbumId,
    localeTag: String,
): LibraryAlbumDetails? {
    val context = currentCoroutineContext()
    val members = filter { row ->
        context.ensureActive()
        row.catalogKey(MediaStoreAudioRow::albumKeyOnVolume) == albumId.value
    }
    if (members.isEmpty()) return null

    // A physical album can contain both loose downloads and ordinary catalog entries.
    val artworkKeys = members.mapTo(HashSet()) { it.artworkKey() }
    val artwork = filter {
        context.ensureActive()
        it.artworkKey() in artworkKeys
    }.artworkReferences()
    val ids = HashSet<Long>()
    val tracks = members.map { row ->
        context.ensureActive()
        if (!ids.add(row.id)) throw IOException("MediaStore returned duplicate audio identity")
        AlbumTrack(row.toTrack(artwork[row.artworkKey()]), row.trackNumber)
    }
    val collator = Collator.getInstance(Locale.forLanguageTag(localeTag))
    val order = compareBy<AlbumTrack> { it.disc }
        .thenBy { it.number }
        .thenBy(collator) { it.track.title }
        .thenBy { it.track.id.value }
    val ordered = tracks.sortedWith { a, b ->
        context.ensureActive()
        order.compare(a, b)
    }
    return LibraryAlbumDetails.create(members.toAlbum(albumId.value, artwork), ordered.map { it.track })
}

/** MediaStore packs disc and track into dxxx. A positive xxx alone belongs to disc one. */
private class AlbumTrack(val track: MediaTrack, encoded: Long?) {
    val disc = if (encoded != null && encoded > 0) (encoded / 1000).coerceAtLeast(1) else Long.MAX_VALUE
    val number = encoded?.rem(1000)?.takeIf { it > 0 } ?: Long.MAX_VALUE
}
