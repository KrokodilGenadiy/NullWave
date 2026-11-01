package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
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

/** Local ReplaceQueue failures belong to their initiator; Player presents engine problems. */
internal data class LibraryPlaybackNotice(val message: UiText)

/** Command messages are independent of catalog read errors; access denial closes the shared gate. */
internal fun LibraryState.withPlaybackResult(result: EmptyResult<PlaybackCommandError>): LibraryState = when (result) {
    // The access observer may already have closed the catalog before this acknowledgement.
    is AppResult.Success -> this
    is AppResult.Failure -> {
        val error = result.error
        val message = error.toLibraryPlaybackMessage()
        if (message == null) this else {
            val denied = error is PlaybackCommandError.SourceFailure && error.error == PlaybackSourceError.AccessDenied
            val next = if (denied) requireAccess(AccessAction.CheckAgain) else this
            next.copy(playbackNotice = LibraryPlaybackNotice(message))
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

/** Engine denial closes the local catalog, without creating or replacing a command notice. */
internal fun LibraryState.withPlaybackAccess(update: LibraryPlaybackAccessUpdate): LibraryState {
    if (update.denial == update.initialDenial || update.denial == lastPlaybackDenial) return this
    return requireAccess(AccessAction.CheckAgain).copy(lastPlaybackDenial = update.denial)
}
