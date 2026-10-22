package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun List<MediaStoreAudioRow>.toAlbums(query: LibraryAlbumQuery): List<LibraryAlbum> {
    val groups = catalogGroups { it.albumId }
    return groups.map { (id, members) ->
        members.toAlbum(id)
    }.sortedCatalog(query.localeTag, query.direction, LibraryAlbum::title) { it.id.value }
}

/** Receives one nonempty group from catalogGroups, before any search filtering. */
internal suspend fun List<MediaStoreAudioRow>.toAlbum(id: String): LibraryAlbum = LibraryAlbum(
    id = AlbumId(id),
    title = catalogLabel { it.album },
    artistCredit = artistCredit(),
    trackCount = size,
    artwork = albumArtwork(id),
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
