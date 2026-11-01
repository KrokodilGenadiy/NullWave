package com.zaus.nullwave.feature.library.testing

import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Catalog fixtures never connect to the service or start device audio. */
internal object IdlePlaybackController : PlaybackController {
    override fun observeState(): Flow<PlaybackConnection> = flowOf(PlaybackConnection.Disconnected)
    override fun observeProgress(): Flow<PlaybackProgress?> = flowOf(null)
    override suspend fun reconnect(): EmptyResult<PlaybackConnectionError> = AppResult.Success(Unit)

    override suspend fun execute(command: PlaybackCommand, beforeDispatch: (PlaybackSnapshot?) -> Unit): EmptyResult<PlaybackCommandError> {
        beforeDispatch(null)
        return AppResult.Success(Unit)
    }
}
