package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort

internal sealed interface LibraryIntent : UiIntent {
    data object Refresh : LibraryIntent
    data object Retry : LibraryIntent
    data class SortChanged(val sort: LibraryTrackSort, val direction: SortDirection) : LibraryIntent
    data class TabSelected(val tab: LibraryTab) : LibraryIntent
    data class AlbumSortChanged(val direction: SortDirection) : LibraryIntent
    data class ArtistSortChanged(val direction: SortDirection) : LibraryIntent
    data class LocaleChanged(val localeTag: String) : LibraryIntent
    data class AccessChecked(val access: LibraryAccess) : LibraryIntent
}
