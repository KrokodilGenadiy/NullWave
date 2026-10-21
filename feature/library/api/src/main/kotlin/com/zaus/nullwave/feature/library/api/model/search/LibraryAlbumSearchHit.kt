package com.zaus.nullwave.feature.library.api.model.search

import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum

/**
 * A complete catalog album, with names explaining a match through its participants.
 *
 * Each name must cover all query tokens missing from the album title on its own. If the title
 * covers every token, [matchingArtistNames] is empty. These are artist tags, not fabricated IDs
 * or the localized Various artists label. Membership, credit, counts and artwork remain intact.
 *
 * Names are cleaned original text, unique by exact spelling, ordered by query locale with an
 * ordinal tie-breaker. The producer enforces matching/ordering and must never mutate this list
 * after publication; Kotlin's read-only List does not make a mutable backing list immutable.
 */
data class LibraryAlbumSearchHit(
    val album: LibraryAlbum,
    val matchingArtistNames: List<String>,
) {
    init {
        require(matchingArtistNames.all { it.isKnownSearchText() }) {
            "Matching artist names must be known, nonblank and trimmed."
        }
        require(matchingArtistNames.distinct().size == matchingArtistNames.size) {
            "Matching artist names must be unique by original text."
        }
    }
}
