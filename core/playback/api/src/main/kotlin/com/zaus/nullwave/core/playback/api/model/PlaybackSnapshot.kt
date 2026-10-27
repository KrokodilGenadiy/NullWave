package com.zaus.nullwave.core.playback.api.model

import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.internal.snapshotSet

/**
 * Control state only: periodic position samples must not rebuild the queue or its revisions.
 * Use [create] to capture protected capabilities; the constructor and generated copy are private.
 */
@ConsistentCopyVisibility
data class PlaybackSnapshot private constructor(
    val sessionId: PlaybackSessionId,
    val revision: Long,
    val queue: PlaybackQueue,
    val transport: PlaybackTransport,
    val positionRevision: Long,
    val capabilities: Set<PlaybackCapability>,
) {
    init {
        require(revision >= 0 && positionRevision >= 0) { "Playback revisions must not be negative." }
        require(queue.current == null || queue.current.sessionId == sessionId) { "Current item belongs to another session." }
        require(transport.problem?.target == null || transport.problem.target == queue.current) {
            "Playback problem must refer to the current activation."
        }
        if (queue.entries.isEmpty()) {
            require(transport.phase == PlaybackPhase.Idle && !transport.playRequested &&
                transport.suppression == PlaybackSuppression.None && transport.problem == null) {
                "An empty queue has idle transport without playback intent, suppression or problem."
            }
            require(capabilities.none { it.requiresCurrentItem }) { "An empty queue cannot offer item commands." }
        }
        require(PlaybackCapability.Retry !in capabilities || transport.problem != null) {
            "Retry requires an active playback problem."
        }
    }

    companion object {
        fun create(
            sessionId: PlaybackSessionId,
            revision: Long = 0,
            queue: PlaybackQueue = PlaybackQueue.create(),
            transport: PlaybackTransport = PlaybackTransport(),
            positionRevision: Long = 0,
            capabilities: Set<PlaybackCapability> = emptySet(),
        ): PlaybackSnapshot = PlaybackSnapshot(
            sessionId = sessionId,
            revision = revision,
            queue = queue,
            transport = transport,
            positionRevision = positionRevision,
            capabilities = capabilities.snapshotSet(),
        )
    }
}
