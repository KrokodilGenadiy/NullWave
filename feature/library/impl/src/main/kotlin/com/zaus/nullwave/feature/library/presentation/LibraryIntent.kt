package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.feature.library.api.model.LibrarySort
import com.zaus.nullwave.feature.library.api.model.SortDirection

internal sealed interface LibraryIntent : UiIntent {
    data object Refresh : LibraryIntent
    data object Retry : LibraryIntent
    data class SortChanged(val sort: LibrarySort, val direction: SortDirection) : LibraryIntent
    data class LocaleChanged(val localeTag: String) : LibraryIntent
    data class AccessChecked(val access: LibraryAccess) : LibraryIntent
}
