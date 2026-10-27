package com.zaus.nullwave.core.playback.api.command

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError

/** Expected rejection; coroutine cancellation and programming errors are not converted to these. */
sealed interface PlaybackCommandError {
    data object ConnectionUnavailable : PlaybackCommandError
    data object StartNotAllowed : PlaybackCommandError
    data object CommandUnavailable : PlaybackCommandError
    data object InvalidRequest : PlaybackCommandError
    data object StaleTarget : PlaybackCommandError
    data object Superseded : PlaybackCommandError
    data class SourceFailure(val trackId: TrackId, val error: PlaybackSourceError) : PlaybackCommandError
}
