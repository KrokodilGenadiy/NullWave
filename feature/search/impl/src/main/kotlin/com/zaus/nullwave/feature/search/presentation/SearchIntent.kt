package com.zaus.nullwave.feature.search.presentation

import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId

internal sealed interface SearchIntent : UiIntent {
    data class QueryChanged(val value: String) : SearchIntent
    data object ClearQuery : SearchIntent
    data object Retry : SearchIntent
    /** A rendered snapshot, so provider updates cannot change the order of a clicked result. */
    data class PlayTrack(val trackId: TrackId, val displayedTracks: List<MediaTrack>) : SearchIntent
    data object DismissPlaybackError : SearchIntent
    data class LocaleChanged(val localeTag: String) : SearchIntent
    /** Fresh OS observation from Root on resume/result; this does not request permission. */
    data class AccessChecked(val granted: Boolean) : SearchIntent
}
