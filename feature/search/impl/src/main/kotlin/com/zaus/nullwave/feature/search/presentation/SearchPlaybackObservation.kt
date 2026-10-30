package com.zaus.nullwave.feature.search.presentation

import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

internal data class SearchPlaybackUpdate(
    val connection: PlaybackConnection,
    val initialProblem: SearchPlaybackProblemKey?,
)

/**
 * Establishes the pre-dispatch baseline once per entry, through the command's own connection.
 * It owns no scope or audio work. Normal observation belongs to the MVI UI source, independently
 * of the command acknowledgement; a late source subscription still knows the original problem.
 */
internal class SearchPlaybackObservation(private val controller: PlaybackController) {
    private val initialProblem = CompletableDeferred<SearchPlaybackProblemKey?>()

    suspend fun execute(request: PlaybackQueueRequest): EmptyResult<PlaybackCommandError> =
        controller.execute(PlaybackCommand.ReplaceQueue(request)) { snapshot ->
            initialProblem.complete(snapshot?.transport?.problem?.let {
                SearchPlaybackProblemKey(snapshot.sessionId, it.id)
            })
        }

    fun observe(): Flow<SearchPlaybackUpdate> = flow {
        val baseline = initialProblem.await()
        emitAll(controller.observeState().map { SearchPlaybackUpdate(it, baseline) })
    }
}
