package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.impl.R

/**
 * A changed selected request discards snapshots; returning to a tab reads a fresh catalog.
 * Changing only an inactive tab's sort preserves the active snapshot and subscription.
 */
internal fun LibraryState.withInputs(next: LibraryState): LibraryState =
    if (catalogRequest == next.catalogRequest) next else {
        val gate = selectedBody as? LibraryBody.AccessRequired
        next.copy(
            body = gate ?: LibraryBody.Loading,
            albums = gate ?: LibraryBody.Loading,
            artists = gate ?: LibraryBody.Loading,
        )
    }

internal fun LibraryState.startReadingSelected(): LibraryState = when (selectedTab) {
    LibraryTab.Songs -> copy(body = body.startReading())
    LibraryTab.Albums -> copy(albums = albums.startReading())
    LibraryTab.Artists -> copy(artists = artists.startReading())
}

/** Audio access is shared across the catalog; no protected snapshot survives revocation. */
internal fun LibraryState.requireAccess(action: AccessAction): LibraryState {
    val gate = LibraryBody.AccessRequired(action)
    return copy(body = gate, albums = gate, artists = gate)
}

internal fun LibraryState.applyResult(
    result: AppResult<LibraryCatalogSnapshot, DataError>,
): LibraryState = when (result) {
    is AppResult.Success -> when (val snapshot = result.value) {
        is LibraryCatalogSnapshot.Songs -> copy(body = LibraryBody.Ready(snapshot.items))
        is LibraryCatalogSnapshot.Albums -> copy(albums = LibraryBody.Ready(snapshot.items))
        is LibraryCatalogSnapshot.Artists -> copy(artists = LibraryBody.Ready(snapshot.items))
    }
    is AppResult.Failure -> when (result.error) {
        DataError.AccessDenied -> requireAccess(
            (selectedBody as? LibraryBody.AccessRequired)?.action ?: AccessAction.CheckAgain,
        )
        DataError.Io -> when (selectedTab) {
            LibraryTab.Songs -> copy(body = body.readFailed())
            LibraryTab.Albums -> copy(albums = albums.readFailed())
            LibraryTab.Artists -> copy(artists = artists.readFailed())
        }
        DataError.NotFound, DataError.StorageFull -> throw IllegalStateException(
            "Library observation returned an unsupported read failure: ${result.error}", result.cause,
        )
    }
}

private fun <T> LibraryBody<T>.startReading(): LibraryBody<T> = when (this) {
    is LibraryBody.Ready -> copy(refresh = RefreshState.Running)
    else -> LibraryBody.Loading
}

private fun <T> LibraryBody<T>.readFailed(): LibraryBody<T> = if (this is LibraryBody.Ready) {
    copy(refresh = RefreshState.Failed(UiText.Resource(R.string.library_refresh_failed)))
} else {
    LibraryBody.Failed(UiText.Resource(R.string.library_load_failed))
}
