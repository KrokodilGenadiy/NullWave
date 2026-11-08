package com.zaus.nullwave.core.media.waveform

import java.util.Collections

/** Peak amplitude in successive equal-duration intervals across the complete track, from silence (0) to full scale (1). */
@ConsistentCopyVisibility
data class Waveform private constructor(val peaks: List<Float>) {
    companion object {
        fun of(peaks: List<Float>): Waveform {
            val snapshot = ArrayList(peaks)
            require(snapshot.isNotEmpty()) { "A waveform needs at least one peak." }
            require(snapshot.all { it in 0f..1f }) { "Waveform peaks must be finite and normalized." }
            return Waveform(Collections.unmodifiableList(snapshot))
        }
    }
}
