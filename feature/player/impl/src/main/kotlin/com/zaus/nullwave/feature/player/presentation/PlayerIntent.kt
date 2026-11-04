package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId

internal sealed interface PlayerIntent : UiIntent {
    data class SetPlayRequested(val value: Boolean) : PlayerIntent
    data object Next : PlayerIntent
    data object Previous : PlayerIntent
    /** Capture the item at gesture start; never retarget a completed drag to another activation. */
    data class SeekTo(val target: PlaybackItemToken, val positionMs: Long) : PlayerIntent
    data object Reconnect : PlayerIntent
    data class RetryProblem(val target: PlaybackItemToken, val problemId: PlaybackProblemId) : PlayerIntent
    data class DismissProblem(val key: PlayerProblemKey) : PlayerIntent
    data class DismissCommandNotice(val id: PlayerCommandNoticeId) : PlayerIntent
}
