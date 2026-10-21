package com.zaus.nullwave.feature.library.api.model.search

import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist

/**
 * Complete results for one query from one logical source snapshot, ranked within each group.
 * Empty groups are valid: three empty lists mean no matches, never denied access or a read error.
 *
 * Entity IDs are unique within each group; equal names or IDs in different groups do not merge
 * entities. Album/artist counts describe full eligible membership before filtering by the query.
 * Unknown artists cannot match: the only searchable artist field is its known name.
 *
 * The producer guarantees snapshot consistency, matching and ordering. It must not mutate these
 * or nested lists after publication; read-only List is not a defensive copy of mutable storage.
 */
data class LibrarySearchResults(
    val tracks: List<LibraryTrackSearchHit>,
    val albums: List<LibraryAlbumSearchHit>,
    val artists: List<LibraryArtist>,
) {
    init {
        require(tracks.map { it.track.id }.distinct().size == tracks.size) {
            "Search tracks must have unique IDs."
        }
        require(albums.map { it.album.id }.distinct().size == albums.size) {
            "Search albums must have unique IDs."
        }
        require(artists.map { it.id }.distinct().size == artists.size) {
            "Search artists must have unique IDs."
        }
        require(artists.all { it.name?.isKnownSearchText() == true }) {
            "Search artists must have known, trimmed names."
        }
    }
}
