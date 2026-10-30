package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.impl.R

/** A backend may reuse a problem ID after a new service incarnation. */
internal data class LibraryPlaybackProblemKey(
    val sessionId: PlaybackSessionId,
    val problemId: PlaybackProblemId,
)

/** Only an engine notice belongs to an activation; a command refusal may leave it playing. */
internal sealed interface LibraryPlaybackNotice {
    val message: UiText

    data class Command(override val message: UiText) : LibraryPlaybackNotice
    data class Engine(
        override val message: UiText,
        val target: PlaybackItemToken?,
    ) : LibraryPlaybackNotice
}

/** Command messages are independent of catalog read errors; access denial closes the shared gate. */
internal fun LibraryState.withPlaybackResult(result: EmptyResult<PlaybackCommandError>): LibraryState = when (result) {
    // An engine failure can arrive before the command acknowledgement; do not erase it here.
    is AppResult.Success -> this
    is AppResult.Failure -> {
        val error = result.error
        val message = error.toLibraryPlaybackMessage()
        if (message == null) this else {
            val denied = error is PlaybackCommandError.SourceFailure && error.error == PlaybackSourceError.AccessDenied
            val next = if (denied) requireAccess(AccessAction.CheckAgain) else this
            next.copy(playbackNotice = LibraryPlaybackNotice.Command(message))
        }
    }
}

/** UI receives localized reasons only; original Throwable/message stay on the backend result. */
internal fun PlaybackCommandError.toLibraryPlaybackMessage(): UiText? = when (this) {
    PlaybackCommandError.ConnectionUnavailable -> UiText.Resource(R.string.library_playback_connection_failed)
    PlaybackCommandError.StartNotAllowed -> UiText.Resource(R.string.library_playback_start_not_allowed)
    PlaybackCommandError.CommandUnavailable, PlaybackCommandError.StaleTarget -> UiText.Resource(R.string.library_playback_unavailable)
    PlaybackCommandError.InvalidRequest -> UiText.Resource(R.string.library_playback_invalid_request)
    PlaybackCommandError.Superseded -> null
    is PlaybackCommandError.SourceFailure -> UiText.Resource(when (error) {
        PlaybackSourceError.AccessDenied -> R.string.library_playback_access_denied
        PlaybackSourceError.NotFound -> R.string.library_playback_not_found
        PlaybackSourceError.Io -> R.string.library_playback_read_failed
    })
}

/** Queue installation can succeed before the engine opens/decodes the current resource. */
internal fun LibraryState.withPlaybackConnection(update: LibraryPlaybackUpdate): LibraryState {
    val snapshot = (update.connection as? PlaybackConnection.Connected)?.snapshot ?: return this
    val problem = snapshot.transport.problem
    if (problem == null) {
        val notice = playbackNotice as? LibraryPlaybackNotice.Engine ?: return this
        return if (notice.target != snapshot.queue.current) copy(playbackNotice = null) else this
    }
    // The first pre-dispatch snapshot belongs to earlier playback, not this Library attempt.
    val key = LibraryPlaybackProblemKey(snapshot.sessionId, problem.id)
    if (key == update.initialProblem || key == lastPlaybackProblem) return this
    val next = if (problem.code == PlaybackProblemCode.AccessDenied) requireAccess(AccessAction.CheckAgain) else this
    return next.copy(
        lastPlaybackProblem = key,
        playbackNotice = LibraryPlaybackNotice.Engine(UiText.Resource(when (problem.code) {
            PlaybackProblemCode.AccessDenied -> R.string.library_playback_access_denied
            PlaybackProblemCode.NotFound -> R.string.library_playback_not_found
            PlaybackProblemCode.Io -> R.string.library_playback_read_failed
            PlaybackProblemCode.UnsupportedMedia -> R.string.library_playback_unsupported
            PlaybackProblemCode.DecoderFailure -> R.string.library_playback_decoder_failed
            PlaybackProblemCode.OutputUnavailable -> R.string.library_playback_output_unavailable
            PlaybackProblemCode.Unknown -> R.string.library_playback_failed
        }), problem.target ?: snapshot.queue.current),
    )
}
