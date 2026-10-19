package com.zaus.nullwave.feature.library.api.model

enum class LibrarySort { Title, Artist, RecentlyAdded }

enum class SortDirection { Ascending, Descending }

/** Complete value input; locale-dependent sorting must not read hidden UI configuration. */
data class LibraryQuery(
    val localeTag: String,
    val sort: LibrarySort = LibrarySort.Title,
    val direction: SortDirection = SortDirection.Ascending,
) {
    init {
        require(localeTag.isNotBlank()) { "A locale tag is required for catalog sorting." }
    }
}
