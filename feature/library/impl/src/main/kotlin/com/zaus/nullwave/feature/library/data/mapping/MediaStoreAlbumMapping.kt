package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.core.media.model.ArtworkRef
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun List<MediaStoreAudioRow>.toAlbums(query: LibraryAlbumQuery): List<LibraryAlbum> {
    val groups = catalogGroups(MediaStoreAudioRow::albumKeyOnVolume)
    val artwork = artworkReferences()
    return groups.map { (id, members) ->
        members.toAlbum(id, artwork)
    }.sortedCatalog(query.localeTag, query.direction, LibraryAlbum::title) { it.id.value }
}

/** Only loose audio in the public Download folder shares this catalog identity. */
internal fun MediaStoreAudioRow.albumKeyOnVolume(): String? {
    val providerId = albumId?.takeIf { it >= 0 } ?: return null
    if (relativePath?.trimEnd('/') == "Download" &&
        album.metadataOrNull().equals("Download", ignoreCase = true) &&
        albumArtist.metadataOrNull() == null
    ) return "folder/Download"
    return providerId.toString()
}

/** Pick a stable member's provider thumbnail, even when the group spans several provider albums. */
internal suspend fun List<MediaStoreAudioRow>.toAlbum(
    id: String,
    artwork: Map<String, ArtworkRef>,
): LibraryAlbum = LibraryAlbum(
    id = AlbumId(id),
    title = catalogLabel { it.album },
    artistCredit = artistCredit(),
    trackCount = size,
    artwork = artwork[minBy { it.id }.artworkKey()],
)

private suspend fun List<MediaStoreAudioRow>.artistCredit(): AlbumArtistCredit {
    val context = currentCoroutineContext()
    val namedArtists = mutableSetOf<Long>()
    var hasUnknown = false
    for (row in this) {
        context.ensureActive()
        val id = row.artistId?.takeIf { it >= 0 }
        if (id == null || row.artist.metadataOrNull() == null) {
            hasUnknown = true
        } else {
            namedArtists.add(id)
        }
    }
    return when {
        namedArtists.isEmpty() -> AlbumArtistCredit.Unknown
        hasUnknown || namedArtists.size > 1 -> AlbumArtistCredit.Various
        else -> AlbumArtistCredit.Named(checkNotNull(catalogLabel { it.artist }))
    }
}
