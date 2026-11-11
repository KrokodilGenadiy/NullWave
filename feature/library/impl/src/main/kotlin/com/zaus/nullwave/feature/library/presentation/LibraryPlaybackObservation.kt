package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.core.media.model.track.TrackId
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

    /**
     * Highlight only: shares the Player host's control lease and never requests progress. Losing the
     * connection keeps the last confirmed track, as the MiniPlayer does; an empty queue clears it.
     */
    fun observeCurrentTrack(): Flow<TrackId?> = controller.observeState()
        .filterIsInstance<PlaybackConnection.Connected>()
        .distinctUntilChangedBy { it.snapshot.queue }
        .map { it.snapshot.currentTrackId() }
        .distinctUntilChanged()
}

private fun PlaybackSnapshot.currentTrackId(): TrackId? =
    queue.current?.let { current -> queue.entries.firstOrNull { it.entryId == current.entryId }?.track?.id }

private fun PlaybackSnapshot.accessDenial(): LibraryPlaybackProblemKey? =
    transport.problem?.takeIf { it.code == PlaybackProblemCode.AccessDenied }?.let {
        LibraryPlaybackProblemKey(sessionId, it.id)
    }
