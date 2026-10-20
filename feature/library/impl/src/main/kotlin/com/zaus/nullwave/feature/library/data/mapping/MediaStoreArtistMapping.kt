package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun List<MediaStoreAudioRow>.toArtists(query: LibraryArtistQuery): List<LibraryArtist> {
    val context = currentCoroutineContext()
    return catalogGroups { it.artistId }.map { (id, members) ->
        val albums = mutableSetOf<Long>()
        // All members share a volume; a compilation counts once for each represented artist.
        for (row in members) {
            context.ensureActive()
            row.albumId?.takeIf { it >= 0 }?.let(albums::add)
        }
        LibraryArtist(
            id = ArtistId(id),
            name = members.catalogLabel { it.artist },
            trackCount = members.size,
            albumCount = albums.size,
        )
    }.sortedCatalog(query.localeTag, query.direction, LibraryArtist::name) { it.id.value }
}
