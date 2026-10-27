package com.zaus.nullwave.core.playback.api

/** A shared nonnegative monotonic millisecond clock. Never mix it with wall time or frame uptime. */
fun interface PlaybackClock {
    fun nowMs(): Long
}
