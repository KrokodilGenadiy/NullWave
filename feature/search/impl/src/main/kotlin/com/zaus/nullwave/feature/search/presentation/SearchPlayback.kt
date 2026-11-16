package com.zaus.nullwave.feature.search.presentation

import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.feature.search.impl.R

/** UI receives localized reasons only; original Throwable/message stay on the backend result. */
internal fun PlaybackCommandError.toSearchPlaybackMessage(): UiText? = when (this) {
    PlaybackCommandError.ConnectionUnavailable -> UiText.Resource(R.string.search_playback_connection_failed)
    PlaybackCommandError.StartNotAllowed -> UiText.Resource(R.string.search_playback_start_not_allowed)
    PlaybackCommandError.CommandUnavailable, PlaybackCommandError.StaleTarget -> UiText.Resource(R.string.search_playback_unavailable)
    PlaybackCommandError.InvalidRequest -> UiText.Resource(R.string.search_playback_invalid_request)
    PlaybackCommandError.Superseded -> null
    is PlaybackCommandError.SourceFailure -> UiText.Resource(when (error) {
        PlaybackSourceError.AccessDenied -> R.string.search_playback_access_denied
        PlaybackSourceError.NotFound -> R.string.search_playback_not_found
        PlaybackSourceError.Io -> R.string.search_playback_read_failed
    })
}
