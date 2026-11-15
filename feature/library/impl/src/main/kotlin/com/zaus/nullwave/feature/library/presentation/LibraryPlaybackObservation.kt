package com.zaus.nullwave.feature.library.presentation

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

internal data class LibraryPlaybackAccessUpdate(
    val denial: LibraryPlaybackProblemKey,
    val initialDenial: LibraryPlaybackProblemKey?,
)

/**
 * Establishes the pre-dispatch baseline once per entry, through the command's own connection.
 * Only later access denials reach the local catalog gate; Player owns engine presentation.
 * This UI source owns no scope, progress lease or audio work and does not wait for an acknowledgement.
 */
internal class LibraryPlaybackObservation(private val controller: PlaybackController) {
    private val initialDenial = CompletableDeferred<LibraryPlaybackProblemKey?>()

    fun publishedDenial(): LibraryPlaybackProblemKey? = controller.publishedSnapshot?.accessDenial()

    suspend fun execute(request: PlaybackQueueRequest): EmptyResult<PlaybackCommandError> =
        controller.execute(PlaybackCommand.ReplaceQueue(request)) { snapshot ->
            initialDenial.complete(snapshot?.accessDenial())
        }

    fun observe(): Flow<LibraryPlaybackAccessUpdate> = flow {
        val baseline = initialDenial.await()
        emitAll(controller.observeState().mapNotNull { connection ->
            (connection as? PlaybackConnection.Connected)?.snapshot?.accessDenial()?.let {
                LibraryPlaybackAccessUpdate(it, baseline)
            }
        })
    }
}

private fun PlaybackSnapshot.accessDenial(): LibraryPlaybackProblemKey? =
    transport.problem?.takeIf { it.code == PlaybackProblemCode.AccessDenied }?.let {
        LibraryPlaybackProblemKey(sessionId, it.id)
    }
