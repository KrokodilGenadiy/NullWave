package com.zaus.nullwave.core.playback.runtime

import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.result.AppResult

/** Bounded owner diagnostics, including commands whose UI caller stopped waiting. Never UI state. */
internal sealed interface PlaybackCommandFailure {
    data class Rejected(
        val command: PlaybackCommand,
        val result: AppResult.Failure<PlaybackCommandError>,
    ) : PlaybackCommandFailure

    data class Unexpected(val command: PlaybackCommand, val cause: Throwable) : PlaybackCommandFailure
}
