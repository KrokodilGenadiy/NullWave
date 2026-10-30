package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.flow.Flow

/**
 * Shared, main-safe playback client; collecting does not create an independent player or start sound.
 * The service owns accepted work and audio lifetime. UI observation/await cancellation is not a
 * rollback, and no client is allowed to release the service's player through this API.
 */
interface PlaybackController {
    /** Latest control snapshots. Collectors share a connection; losing it does not prove audio stopped. */
    fun observeState(): Flow<PlaybackConnection>

    /**
     * Latest position anchors, sharing one demand-driven sampler. New collectors receive a fresh
     * sample, discontinuities publish immediately, and no UI collectors means no UI polling.
     * Null means no confirmed current position. Pair with control by target and positionRevision.
     */
    fun observeProgress(): Flow<PlaybackProgress?>

    /**
     * Success means dispatch through a live playback connection, not audible sound, universal
     * execution acknowledgement or durable persistence. The service owns work it has accepted.
     * ReplaceQueue additionally awaits preflight/whole-queue installation; SeekTo/Retry require
     * owner-side token checks. Standard relative actions must not be replayed automatically after
     * cancellation/disconnection: their result may already have happened. Connection attempts are
     * bounded by the adapter. Expected failures retain cause/originalMessage in AppResult.Failure;
     * cancellation and unexpected programming failures propagate.
     *
     * [beforeDispatch] runs synchronously on Main once a live connection is confirmed, immediately
     * before transport dispatch. It receives the pre-dispatch control snapshot, or null if that
     * connection has not published one yet. It is not owner acceptance or command acknowledgement.
     * The callback must not suspend; its exceptions propagate and cancellation prevents dispatch.
     */
    suspend fun execute(
        command: PlaybackCommand,
        beforeDispatch: (PlaybackSnapshot?) -> Unit = {},
    ): EmptyResult<PlaybackCommandError>
}
