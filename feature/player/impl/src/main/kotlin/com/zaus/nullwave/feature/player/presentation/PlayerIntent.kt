package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode

internal sealed interface PlayerIntent : UiIntent {
    data class SetPlayRequested(val value: Boolean) : PlayerIntent
    data object Next : PlayerIntent
    data object Previous : PlayerIntent
    /** Both IDs come from the queue row as displayed, including when the same track occurs twice. */
    data class SelectQueueEntry(val sessionId: PlaybackSessionId, val entryId: QueueEntryId) : PlayerIntent
    /** Remove the displayed occurrence only while it is still upcoming in this session. */
    data class RemoveQueueEntry(val sessionId: PlaybackSessionId, val entryId: QueueEntryId) : PlayerIntent
    /** Keep the immutable queue shown when the user chose this insertion point. */
    data class MoveQueueEntry(
        val displayedQueue: PlaybackQueue,
        val entryId: QueueEntryId,
        val beforeEntryId: QueueEntryId?,
    ) : PlayerIntent
    /** Capture the item at gesture start; never retarget a completed drag to another activation. */
    data class SeekTo(val target: PlaybackItemToken, val positionMs: Long) : PlayerIntent
    /** Session modes use explicit desired values; the engine owns their confirmed state. */
    data class SetShuffle(val enabled: Boolean) : PlayerIntent
    data class SetRepeat(val mode: RepeatMode) : PlayerIntent
    data object Reconnect : PlayerIntent
    data class RetryProblem(val target: PlaybackItemToken, val problemId: PlaybackProblemId) : PlayerIntent
    data class DismissProblem(val key: PlayerProblemKey) : PlayerIntent
    data class DismissCommandNotice(val id: PlayerCommandNoticeId) : PlayerIntent
}
