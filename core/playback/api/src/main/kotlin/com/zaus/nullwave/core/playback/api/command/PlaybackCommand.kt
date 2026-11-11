package com.zaus.nullwave.core.playback.api.command

import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.result.AppResult

/** Desired values avoid toggle races. Next/Previous are relative ordered actions, not latest state. */
sealed interface PlaybackCommand {
    data class ReplaceQueue(val request: PlaybackQueueRequest) : PlaybackCommand
    data class SetPlayRequested(val value: Boolean) : PlaybackCommand
    data class SeekTo(val target: PlaybackItemToken, val positionMs: Long) : PlaybackCommand {
        init { require(positionMs >= 0) { "Seek position must not be negative." } }
    }

    /**
     * Selects an occurrence in the installed queue, preserving the owner's play/pause intent.
     * A different entry starts at its beginning. Selecting the current entry preserves its position.
     */
    data class SelectQueueEntry(val sessionId: PlaybackSessionId, val entryId: QueueEntryId) : PlaybackCommand {
        /**
         * Resolve against the owner's live snapshot immediately before selecting the entry.
         * A session mismatch or a missing occurrence returns [PlaybackCommandError.StaleTarget].
         * The index is in the base timeline, regardless of shuffle order or current activation.
         */
        fun resolveBaseIndex(snapshot: PlaybackSnapshot): AppResult<Int, PlaybackCommandError> {
            if (snapshot.sessionId != sessionId) return AppResult.Failure(PlaybackCommandError.StaleTarget)
            val index = snapshot.queue.entries.indexOfFirst { it.entryId == entryId }
            return if (index >= 0) AppResult.Success(index) else AppResult.Failure(PlaybackCommandError.StaleTarget)
        }
    }

    /**
     * Removes one upcoming occurrence. The current activation, transport and position continue
     * uninterrupted. The remaining entries keep their relative order in both timelines.
     */
    data class RemoveQueueEntry(val sessionId: PlaybackSessionId, val entryId: QueueEntryId) : PlaybackCommand {
        /** Resolve against the live snapshot immediately before editing the owner's base timeline. */
        fun resolveBaseIndex(snapshot: PlaybackSnapshot): AppResult<Int, PlaybackCommandError> {
            val queue = snapshot.queue
            val current = queue.current
            if (current == null || snapshot.sessionId != sessionId) {
                return AppResult.Failure(PlaybackCommandError.StaleTarget)
            }

            val orderIndex = queue.playOrder.indexOf(entryId)
            if (orderIndex <= queue.playOrder.indexOf(current.entryId)) {
                return AppResult.Failure(PlaybackCommandError.StaleTarget)
            }

            val baseIndex = if (queue.shuffleEnabled) queue.entries.indexOfFirst { it.entryId == entryId } else orderIndex
            return AppResult.Success(baseIndex)
        }
    }

    /** Move an upcoming occurrence immediately before another upcoming occurrence, or to the end. */
    data class MoveQueueEntry(
        val sessionId: PlaybackSessionId,
        val entryId: QueueEntryId,
        val beforeEntryId: QueueEntryId?,
    ) : PlaybackCommand {
        /** Check both occurrences against the live traversal without constructing a reordered queue. */
        fun hasLiveTargets(snapshot: PlaybackSnapshot): Boolean = resolveIndices(snapshot) != null

        /**
         * Compute the desired queue from the owner's live snapshot. With shuffle enabled the edit
         * affects only traversal order. An already satisfied move returns the original queue.
         */
        fun resolveQueue(snapshot: PlaybackSnapshot): AppResult<PlaybackQueue, PlaybackCommandError> {
            val (fromIndex, toIndex) = resolveIndices(snapshot) ?: return AppResult.Failure(PlaybackCommandError.StaleTarget)
            return AppResult.Success(snapshot.queue.moveInPlayOrder(fromIndex, toIndex))
        }

        private fun resolveIndices(snapshot: PlaybackSnapshot): Pair<Int, Int>? {
            val queue = snapshot.queue
            val current = queue.current ?: return null
            if (snapshot.sessionId != sessionId) return null

            val order = queue.playOrder
            val currentIndex = order.indexOf(current.entryId)
            val fromIndex = order.indexOf(entryId)
            val beforeIndex = beforeEntryId?.let(order::indexOf) ?: order.size
            if (fromIndex <= currentIndex || beforeIndex <= currentIndex) return null

            // Removing the source shifts a later anchor (including the end) one slot to the left.
            val toIndex = if (fromIndex < beforeIndex) beforeIndex - 1 else beforeIndex
            return fromIndex to toIndex
        }
    }

    data object Next : PlaybackCommand
    data object Previous : PlaybackCommand
    data class SetRepeat(val mode: RepeatMode) : PlaybackCommand
    data class SetShuffle(val enabled: Boolean) : PlaybackCommand
    data class Retry(val target: PlaybackItemToken, val problemId: PlaybackProblemId) : PlaybackCommand
    data object StopAndClear : PlaybackCommand
}
