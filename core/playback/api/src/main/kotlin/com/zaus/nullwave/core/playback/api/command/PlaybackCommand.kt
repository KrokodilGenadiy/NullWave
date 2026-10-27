package com.zaus.nullwave.core.playback.api.command

import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.RepeatMode

/** Desired values avoid toggle races. Next/Previous are relative ordered actions, not latest state. */
sealed interface PlaybackCommand {
    data class ReplaceQueue(val request: PlaybackQueueRequest) : PlaybackCommand
    data class SetPlayRequested(val value: Boolean) : PlaybackCommand
    data class SeekTo(val target: PlaybackItemToken, val positionMs: Long) : PlaybackCommand {
        init { require(positionMs >= 0) { "Seek position must not be negative." } }
    }
    data object Next : PlaybackCommand
    data object Previous : PlaybackCommand
    data class SetRepeat(val mode: RepeatMode) : PlaybackCommand
    data class SetShuffle(val enabled: Boolean) : PlaybackCommand
    data class Retry(val target: PlaybackItemToken, val problemId: PlaybackProblemId) : PlaybackCommand
    data object StopAndClear : PlaybackCommand
}
