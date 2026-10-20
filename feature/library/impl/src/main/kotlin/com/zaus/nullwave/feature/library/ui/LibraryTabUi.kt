package com.zaus.nullwave.feature.library.ui

import androidx.annotation.StringRes
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.library.presentation.LibraryState
import com.zaus.nullwave.feature.library.presentation.LibraryTab

// Presentation order follows the artboard; it is independent of enum ordinals and saved names.
internal val LibraryVisibleTabs = listOf(LibraryTab.Songs, LibraryTab.Artists, LibraryTab.Albums)

@StringRes
internal fun LibraryTab.tabLabel(): Int = when (this) {
    LibraryTab.Songs -> R.string.library_tab_songs
    LibraryTab.Artists -> R.string.library_tab_artists
    LibraryTab.Albums -> R.string.library_tab_albums
}

internal val LibraryState.sortDirection: SortDirection
    get() = when (selectedTab) {
        LibraryTab.Songs -> query.direction
        LibraryTab.Artists -> artistQuery.direction
        LibraryTab.Albums -> albumQuery.direction
    }

@StringRes
internal fun LibraryState.sortLabel(): Int = when (selectedTab) {
    LibraryTab.Songs -> query.sort.label()
    LibraryTab.Artists -> R.string.library_sort_name
    LibraryTab.Albums -> R.string.library_sort_title
}

internal fun LibraryState.sortIntent(direction: SortDirection): LibraryIntent = when (selectedTab) {
    LibraryTab.Songs -> LibraryIntent.SortChanged(query.sort, direction)
    LibraryTab.Artists -> LibraryIntent.ArtistSortChanged(direction)
    LibraryTab.Albums -> LibraryIntent.AlbumSortChanged(direction)
}
