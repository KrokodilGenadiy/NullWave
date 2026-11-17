package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.feature.library.impl.R

/** UI receives localized reasons only; original Throwable/message stay on the backend result. */
internal fun PlaybackCommandError.toLibraryPlaybackMessage(): UiText? = when (this) {
    PlaybackCommandError.ConnectionUnavailable -> UiText.Resource(R.string.library_playback_connection_failed)
    PlaybackCommandError.StartNotAllowed -> UiText.Resource(R.string.library_playback_start_not_allowed)
    PlaybackCommandError.CommandUnavailable, PlaybackCommandError.StaleTarget -> UiText.Resource(R.string.library_playback_unavailable)
    PlaybackCommandError.InvalidRequest -> UiText.Resource(R.string.library_playback_invalid_request)
    PlaybackCommandError.Superseded -> null
    is PlaybackCommandError.SourceFailure -> UiText.Resource(when (error) {
        is PlaybackSourceError.AccessDenied -> R.string.library_playback_access_denied
        PlaybackSourceError.NotFound -> R.string.library_playback_not_found
        PlaybackSourceError.Io -> R.string.library_playback_read_failed
    })
}
