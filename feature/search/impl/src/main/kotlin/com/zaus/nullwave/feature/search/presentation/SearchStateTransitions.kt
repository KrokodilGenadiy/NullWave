package com.zaus.nullwave.feature.search.presentation

import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults
import com.zaus.nullwave.feature.search.impl.R

/** Equivalent spelling keeps the snapshot, subscription and debounce deadline. */
internal fun SearchState.withInput(rawQuery: String = this.rawQuery, localeTag: String = this.localeTag): SearchState {
    val next = copy(rawQuery = rawQuery, localeTag = localeTag)
    if (query == next.query) return next
    // A provider denial also gates subsequent input edits, until Root supplies a fresh check.
    // Only intents change source inputs; a source reducer records the denial in body.
    val nextAccess = if (body == SearchBody.AccessRequired) SearchAccess.Required else access
    return next.copy(access = nextAccess, body = when {
        next.query == null -> SearchBody.Idle
        nextAccess == SearchAccess.Unchecked -> SearchBody.CheckingAccess
        nextAccess == SearchAccess.Required -> SearchBody.AccessRequired
        else -> SearchBody.Loading
    })
}

internal fun SearchState.withAccess(granted: Boolean): SearchState = copy(
    access = if (granted) SearchAccess.Granted else SearchAccess.Required,
    body = when {
        query == null -> SearchBody.Idle
        !granted -> SearchBody.AccessRequired
        else -> body.startReading()
    },
)

internal fun SearchBody.startReading(): SearchBody = when (this) {
    is SearchBody.Ready -> copy(refresh = SearchRefresh.Running)
    else -> SearchBody.Loading
}

internal fun SearchState.applyResult(result: AppResult<LibrarySearchResults, DataError>): SearchState =
    copy(body = when (result) {
        is AppResult.Success -> SearchBody.Ready(result.value.snapshot())
        is AppResult.Failure -> when (result.error) {
            DataError.AccessDenied -> SearchBody.AccessRequired
            DataError.Io -> when (val current = body) {
                is SearchBody.Ready -> current.copy(
                    refresh = SearchRefresh.Failed(UiText.Resource(R.string.search_refresh_failed)),
                )
                else -> SearchBody.Failed(UiText.Resource(R.string.search_load_failed))
            }
            DataError.NotFound, DataError.StorageFull -> throw IllegalStateException(
                "Search observation returned an unsupported read failure: ${result.error}", result.cause,
            )
        }
    })

/** Own the published collections, including nested album explanations. Entities are immutable. */
private fun LibrarySearchResults.snapshot(): LibrarySearchResults = copy(
    tracks = tracks.toList(),
    albums = albums.map { it.copy(matchingArtistNames = it.matchingArtistNames.toList()) },
    artists = artists.toList(),
)
