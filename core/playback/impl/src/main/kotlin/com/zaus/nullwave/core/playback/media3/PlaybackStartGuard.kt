package com.zaus.nullwave.core.playback.media3

import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult

/** Acquires foreground before a command can request audio focus, not after the engine starts. */
internal class PlaybackStartGuard(
    private val acquire: () -> AppResult<AutoCloseable, PlaybackCommandError>,
    private val onDenied: suspend () -> Unit,
) {
    suspend fun run(
        required: Boolean,
        action: suspend () -> EmptyResult<PlaybackCommandError>,
    ): EmptyResult<PlaybackCommandError> {
        if (!required) return action()
        return when (val admission = acquire()) {
            is AppResult.Failure -> {
                // Even a rejected later start must revoke an older pending autoplay request.
                onDenied()
                admission
            }
            is AppResult.Success -> admission.value.use { action() }
        }
    }
}
