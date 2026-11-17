package com.zaus.nullwave.core.playback.api.model

import java.util.concurrent.atomic.AtomicLong

/** Orders a resolver failure or a newly published engine problem against a permission check in this process. */
class PlaybackFailureOccurrence internal constructor() {
    val sequence: Long = counter.incrementAndGet()

    companion object {
        private val counter = AtomicLong()

        /** Capture when Android reports access, before the resulting UI intent can be queued. */
        val latestSequence: Long get() = counter.get()
    }
}
