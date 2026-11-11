package com.zaus.nullwave.core.playback.api.command

/** A hint for controls; command execution must still check current availability at the owner. */
enum class PlaybackCapability(internal val requiresCurrentItem: Boolean = false) {
    ReplaceQueue,
    SetPlayRequested(true),
    SeekTo(true),
    Next(true),
    Previous(true),
    SetRepeat,
    SetShuffle,
    Retry(true),
    StopAndClear,
    SelectQueueEntry(true),
    RemoveQueueEntry(true),
}
