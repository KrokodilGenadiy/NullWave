package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.feature.library.api.model.LibraryQuery
import com.zaus.nullwave.feature.library.api.model.LibraryTrack

internal data class LibraryState(
    val query: LibraryQuery,
    val body: LibraryBody = LibraryBody.Loading,
) : UiState

internal sealed interface LibraryBody {
    data object Loading : LibraryBody
    data class AccessRequired(val action: AccessAction) : LibraryBody

    /** An empty list is the successful empty state, including during refresh or its failure. */
    data class Ready(
        val tracks: List<LibraryTrack>,
        val refresh: RefreshState = RefreshState.Idle,
    ) : LibraryBody

    data class Failed(val message: UiText) : LibraryBody
}

internal sealed interface RefreshState {
    data object Idle : RefreshState
    data object Running : RefreshState
    data class Failed(val message: UiText) : RefreshState
}
