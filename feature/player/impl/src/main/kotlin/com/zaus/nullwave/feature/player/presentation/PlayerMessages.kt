package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.feature.player.impl.R

/** Only localized classifications reach UI; diagnostic Throwable/message stay with the backend result. */
internal fun PlaybackCommandError.toPlayerMessage(): UiText? = when (this) {
    PlaybackCommandError.ConnectionUnavailable -> UiText.Resource(R.string.player_connection_failed)
    PlaybackCommandError.StartNotAllowed -> UiText.Resource(R.string.player_start_not_allowed)
    PlaybackCommandError.CommandUnavailable, PlaybackCommandError.StaleTarget -> UiText.Resource(R.string.player_command_unavailable)
    PlaybackCommandError.InvalidRequest -> UiText.Resource(R.string.player_invalid_request)
    PlaybackCommandError.Superseded -> null
    is PlaybackCommandError.SourceFailure -> UiText.Resource(when (error) {
        PlaybackSourceError.AccessDenied -> R.string.player_access_denied
        PlaybackSourceError.NotFound -> R.string.player_not_found
        PlaybackSourceError.Io -> R.string.player_read_failed
    })
}

internal fun PlaybackConnectionError.toPlayerMessage(): UiText = UiText.Resource(when (this) {
    PlaybackConnectionError.ConnectionFailed, PlaybackConnectionError.ServiceDisconnected -> R.string.player_connection_failed
    PlaybackConnectionError.StartNotAllowed -> R.string.player_start_not_allowed
})

internal fun PlaybackProblemCode.toPlayerMessage(): UiText = UiText.Resource(when (this) {
    PlaybackProblemCode.AccessDenied -> R.string.player_access_denied
    PlaybackProblemCode.NotFound -> R.string.player_not_found
    PlaybackProblemCode.Io -> R.string.player_read_failed
    PlaybackProblemCode.UnsupportedMedia -> R.string.player_unsupported
    PlaybackProblemCode.DecoderFailure -> R.string.player_decoder_failed
    PlaybackProblemCode.OutputUnavailable -> R.string.player_output_unavailable
    PlaybackProblemCode.Unknown -> R.string.player_playback_failed
})
