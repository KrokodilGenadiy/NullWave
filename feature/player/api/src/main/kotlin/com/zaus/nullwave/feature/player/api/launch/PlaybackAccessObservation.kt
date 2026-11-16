package com.zaus.nullwave.feature.player.api.launch

import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

internal data class PlaybackAccessUpdate(
    val denial: PlaybackProblemKey,
    val initialDenial: PlaybackProblemKey?,
)

/**
 * Establishes the pre-dispatch baseline once per screen, through the command's own connection.
 * Only later access denials reach the screen's gate; Player owns engine presentation.
 * This UI source owns no scope, progress lease or audio work and does not wait for an acknowledgement.
 */
internal class PlaybackAccessObservation(private val controller: PlaybackController) {
    private val initialDenial = CompletableDeferred<PlaybackProblemKey?>()

    fun publishedDenial(): PlaybackProblemKey? = controller.publishedSnapshot?.accessDenial()

    suspend fun execute(request: PlaybackQueueRequest): EmptyResult<PlaybackCommandError> =
        controller.execute(PlaybackCommand.ReplaceQueue(request)) { snapshot ->
            initialDenial.complete(snapshot?.accessDenial())
        }

    fun observe(): Flow<PlaybackAccessUpdate> = flow {
        val baseline = initialDenial.await()
        emitAll(controller.observeState().mapNotNull { connection ->
            val denial = (connection as? PlaybackConnection.Connected)?.snapshot?.accessDenial() ?: return@mapNotNull null
            // A delayed client snapshot may describe a problem the service already cleared or replaced.
            if (denial == publishedDenial()) PlaybackAccessUpdate(denial, baseline) else null
        })
    }
}

private fun PlaybackSnapshot.accessDenial(): PlaybackProblemKey? =
    transport.problem?.takeIf { it.code == PlaybackProblemCode.AccessDenied }?.let {
        PlaybackProblemKey(sessionId, it.id)
    }
