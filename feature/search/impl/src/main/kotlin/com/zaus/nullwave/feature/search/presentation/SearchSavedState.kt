package com.zaus.nullwave.feature.search.presentation

import androidx.lifecycle.SavedStateHandle
import com.zaus.nullwave.core.mvi.persistence.MviSavedState
import com.zaus.nullwave.core.mvi.persistence.savedState
import kotlinx.serialization.Serializable

@Serializable
private data class SearchInputs(val rawQuery: String = "")

/** Restore text only; fresh defaults still require a new access check and use the new locale. */
internal fun searchSavedState(handle: SavedStateHandle): MviSavedState<SearchState> = savedState(
    handle = handle,
    key = "search.inputs",
    serializer = SearchInputs.serializer(),
    select = { SearchInputs(rawQuery) },
    restore = { inputs -> withInput(inputs.rawQuery) },
    legacy = { SearchInputs(string("search.query") ?: "") },
)
