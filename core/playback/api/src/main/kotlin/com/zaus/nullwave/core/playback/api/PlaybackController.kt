package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
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
     * Confirms a current connection and clears a previous connection/start-denial diagnostic.
     * Owns only a temporary control lease: it never dispatches a playback command, prepares
     * media, clears the queue/engine problem or starts sound. Existing observers are notified
     * through [observeState]; Success confirms the handshake, not the first snapshot or a retained
     * UI lease. An already live connection may be reused rather than forcibly disconnected.
     *
     * Connection attempts are bounded. Expected failures preserve cause/originalMessage;
     * cancellation and programming failures propagate. Cancellation after diagnostic publication
     * does not roll that publication back. It never replays a previous command.
     */
    suspend fun reconnect(): EmptyResult<PlaybackConnectionError>

    /**
     * Success means dispatch through a live playback connection, not audible sound, universal
     * execution acknowledgement or durable persistence. The service owns work it has accepted.
     * ReplaceQueue additionally awaits preflight/whole-queue installation; SeekTo/Retry require
     * owner-side token checks. SelectQueueEntry requires the same live session and an installed
     * occurrence, checked together at execution. Selection preserves the owner's playRequested value.
     * RemoveQueueEntry also requires the occurrence to follow the current item in playback order.
     * MoveQueueEntry resolves its source and optional destination in the upcoming playback order.
     * Both edits preserve the current activation, transport and progress anchor.
     * Standard relative actions must not be replayed automatically after
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
