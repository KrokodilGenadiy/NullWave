package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.core.result.map
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.api.repository.LibraryCatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The selected tab and its complete query form one MVI source input. */
internal sealed interface LibraryCatalogRequest {
    data class Songs(val query: LibraryTrackQuery) : LibraryCatalogRequest
    data class Albums(val query: LibraryAlbumQuery) : LibraryCatalogRequest
    data class Artists(val query: LibraryArtistQuery) : LibraryCatalogRequest
}

internal val LibraryState.catalogRequest: LibraryCatalogRequest
    get() = when (selectedTab) {
        LibraryTab.Songs -> LibraryCatalogRequest.Songs(query)
        LibraryTab.Albums -> LibraryCatalogRequest.Albums(albumQuery)
        LibraryTab.Artists -> LibraryCatalogRequest.Artists(artistQuery)
    }

/** Typed payloads keep tab routing safe without casting repository lists. */
internal sealed interface LibraryCatalogSnapshot {
    data class Songs(val items: List<MediaTrack>) : LibraryCatalogSnapshot
    data class Albums(val items: List<LibraryAlbum>) : LibraryCatalogSnapshot
    data class Artists(val items: List<LibraryArtist>) : LibraryCatalogSnapshot
}

internal fun LibraryCatalogRepository.observe(
    request: LibraryCatalogRequest,
): Flow<AppResult<LibraryCatalogSnapshot, DataError>> = when (request) {
    is LibraryCatalogRequest.Songs -> observeTracks(request.query).map { result ->
        result.map { LibraryCatalogSnapshot.Songs(it.toList()) }
    }
    is LibraryCatalogRequest.Albums -> observeAlbums(request.query).map { result ->
        result.map { LibraryCatalogSnapshot.Albums(it.toList()) }
    }
    is LibraryCatalogRequest.Artists -> observeArtists(request.query).map { result ->
        result.map { LibraryCatalogSnapshot.Artists(it.toList()) }
    }
}
