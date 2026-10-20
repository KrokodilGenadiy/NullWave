package com.zaus.nullwave.feature.library.api.model.artist

import com.zaus.nullwave.feature.library.api.model.SortDirection

/**
 * Complete input for artist browsing, ordered by name using [localeTag].
 * Unknown names sort last in either direction; ties use [ArtistId.value] ascending.
 * Artist tags are treated as one source identity, never split on commas, ampersands or slashes.
 */
data class LibraryArtistQuery(
    val localeTag: String,
    val direction: SortDirection = SortDirection.Ascending,
) {
    init {
        require(localeTag.isNotBlank()) { "A locale tag is required for artist sorting." }
    }
}
