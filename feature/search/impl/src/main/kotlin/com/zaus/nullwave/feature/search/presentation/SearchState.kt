package com.zaus.nullwave.feature.search.presentation

import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults

internal data class SearchState(
    val localeTag: String,
    val rawQuery: String = "",
    /** Last platform check/input gate, not authorization that bypasses repository access checks. */
    val access: SearchAccess = SearchAccess.Unchecked,
    val body: SearchBody = SearchBody.Idle,
    /** Playback failures do not replace the query/results and are never persisted. */
    val playbackNotice: SearchPlaybackNotice? = null,
    /** Access-only observation starts with the first admitted play intent, before acknowledgement. */
    val monitorPlayback: Boolean = false,
    /** A handled access denial cannot close freshly checked results again on rebind. */
    val lastPlaybackDenial: SearchPlaybackProblemKey? = null,
    val playingTrackId: TrackId? = null,
) : UiState {
    val playbackError: UiText? get() = playbackNotice?.message

    val query: LibrarySearchQuery? = LibrarySearchQuery.fromInput(rawQuery, localeTag)

    /** Complete source input. Body changes must not change the source's own input. */
    val request: LibrarySearchQuery?
        get() = query.takeIf { access == SearchAccess.Granted }
}

internal enum class SearchAccess { Unchecked, Granted, Required }

internal sealed interface SearchBody {
    data object Idle : SearchBody
    data object CheckingAccess : SearchBody
    data object AccessRequired : SearchBody
    /** Includes the debounce interval and the first read. */
    data object Loading : SearchBody
    data class Failed(val message: UiText) : SearchBody

    /** An empty successful snapshot is NoMatches, including during a failed refresh. */
    data class Ready(
        val results: LibrarySearchResults,
        val refresh: SearchRefresh = SearchRefresh.Idle,
    ) : SearchBody {
        val isEmpty: Boolean
            get() = results.tracks.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty()
    }
}

internal sealed interface SearchRefresh {
    data object Idle : SearchRefresh
    data object Running : SearchRefresh
    data class Failed(val message: UiText) : SearchRefresh
}
