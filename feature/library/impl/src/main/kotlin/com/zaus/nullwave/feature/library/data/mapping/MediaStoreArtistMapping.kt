package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun List<MediaStoreAudioRow>.toArtists(query: LibraryArtistQuery): List<LibraryArtist> {
    return catalogGroups { it.artistId }.map { (id, members) ->
        members.toArtist(id)
    }.sortedCatalog(query.localeTag, query.direction, LibraryArtist::name) { it.id.value }
}

/** All members share a volume; a compilation counts once for each represented artist. */
internal suspend fun List<MediaStoreAudioRow>.toArtist(id: String): LibraryArtist {
    val context = currentCoroutineContext()
    val albums = mutableSetOf<Long>()
    for (row in this) {
        context.ensureActive()
        row.albumId?.takeIf { it >= 0 }?.let(albums::add)
    }
    return LibraryArtist(
        id = ArtistId(id),
        name = catalogLabel { it.artist },
        trackCount = size,
        albumCount = albums.size,
    )
}
