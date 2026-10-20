package com.zaus.nullwave.feature.library.api.model.track

import com.zaus.nullwave.feature.library.api.model.SortDirection

/** Complete value input; locale-dependent sorting must not read hidden UI configuration. */
data class LibraryTrackQuery(
    val localeTag: String,
    val sort: LibraryTrackSort = LibraryTrackSort.Title,
    val direction: SortDirection = SortDirection.Ascending,
) {
    init {
        require(localeTag.isNotBlank()) { "A locale tag is required for catalog sorting." }
    }
}
