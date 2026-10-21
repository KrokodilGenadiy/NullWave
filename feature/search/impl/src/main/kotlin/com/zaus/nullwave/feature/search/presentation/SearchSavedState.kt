package com.zaus.nullwave.feature.search.presentation

import androidx.lifecycle.SavedStateHandle

private const val QUERY_KEY = "search.query"

/** Restore only editable text. Locale is fresh; access, results and errors are not persisted. */
internal fun SavedStateHandle.restoreSearchState(localeTag: String): SearchState {
    val rawQuery = get<Any?>(QUERY_KEY) as? String ?: ""
    return SearchState(localeTag).withInput(rawQuery)
}

internal fun SavedStateHandle.saveSearchQuery(state: SearchState) {
    this[QUERY_KEY] = state.rawQuery
}
