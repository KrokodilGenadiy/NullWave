package com.zaus.nullwave.feature.library.api.model.album

/**
 * Display credit derived from the album's eligible tracks, not a navigation target or artist ID.
 * Names are normalized source metadata; localization of Unknown/Various belongs to the UI.
 * This does not claim to be the ALBUM_ARTIST tag or an official release credit.
 */
sealed interface AlbumArtistCredit {
    /** No member has an identified, named artist. */
    data object Unknown : AlbumArtistCredit

    /** Every member belongs to one identified artist with this name. */
    data class Named(val name: String) : AlbumArtistCredit {
        init {
            require(name.isNotBlank()) { "A known artist credit must not be blank." }
        }
    }

    /** Multiple artists, or a mix of identified and unknown artists; never pick the first track. */
    data object Various : AlbumArtistCredit
}
