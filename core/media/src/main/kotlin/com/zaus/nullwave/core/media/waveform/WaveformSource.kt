package com.zaus.nullwave.core.media.waveform

import com.zaus.nullwave.core.media.model.track.TrackId

/** Reads the waveform of a complete audio file independently of playback position and queue order. */
fun interface WaveformSource {
    /**
     * Main-safe, cancellable read for [trackId]. Returns null when the source file is missing,
     * inaccessible, unreadable or unsupported. A silent file has a waveform with zero-valued peaks.
     * Implementations propagate caller cancellation and release resources when a read ends.
     */
    suspend fun load(trackId: TrackId): Waveform?
}
