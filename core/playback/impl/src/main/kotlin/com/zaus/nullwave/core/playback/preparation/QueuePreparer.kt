package com.zaus.nullwave.core.playback.preparation

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Resolves a whole replacement without changing live playback. Each call owns its resource map;
 * the caller owns cancellation and later commit. The resolver remains responsible for I/O access.
 */
internal class QueuePreparer(
    private val resolver: PlaybackSourceResolver,
    private val default: CoroutineDispatcher,
) {
    suspend fun prepare(
        request: PlaybackQueueRequest,
    ): AppResult<PreparedQueue, PlaybackCommandError.SourceFailure> = withContext(default) {
        val context = currentCoroutineContext()
        val resolved = HashMap<TrackId, ResolvedPlaybackTrack>()
        val items = ArrayList<ResolvedPlaybackTrack>(request.trackIds.size)
        for (id in request.trackIds) {
            context.ensureActive()
            val cached = resolved[id]
            if (cached != null) {
                items.add(cached)
                continue
            }
            val result = resolver.resolveTrack(id)
            context.ensureActive()
            when (result) {
                is AppResult.Success -> {
                    check(result.value.track.id == id) { "PlaybackSourceResolver returned a different track identity." }
                    resolved[id] = result.value
                    items.add(result.value)
                }
                is AppResult.Failure -> return@withContext AppResult.Failure(
                    error = PlaybackCommandError.SourceFailure(id, result.error),
                    cause = result.cause,
                    originalMessage = result.originalMessage,
                )
            }
        }
        AppResult.Success(PreparedQueue.create(request, items))
    }
}
