package com.zaus.nullwave.feature.library.data.waveform

import android.os.SystemClock

/** A selected track may finish in one call. WorkManager yields after a bounded slice. */
internal class WaveformDecodeSession(
    previous: WaveformCheckpoint?,
    private val save: (WaveformCheckpoint) -> Boolean,
    private val discard: () -> Unit = {},
    private val timeLimitMs: Long? = null,
    private val checkpointIntervalMs: Long = 30_000L,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
) {
    /** Only a later durable position makes another background slice useful immediately. */
    enum class SliceEnd { Advanced, NoProgress }

    private val startedAt = clockMs()
    private var lastAttemptAt = startedAt
    private var savedPositionUs = previous?.positionUs ?: 0L
    private var advanced = false
    var previous: WaveformCheckpoint? = previous
        private set
    var sliceEnd: SliceEnd? = null
        private set

    val canRestartFromBeginning: Boolean get() = timeLimitMs == null && previous != null

    fun checkpointFor(durationUs: Long): WaveformCheckpoint? {
        val checkpoint = previous ?: return null
        if (checkpoint.durationUs == durationUs) return checkpoint
        discardUnusableCheckpoint()
        return null
    }

    fun discardUnusableCheckpoint() {
        discard()
        previous = null
        savedPositionUs = 0L
        advanced = false
    }

    fun withinTimeLimit(): Boolean {
        if (timeLimitMs == null || clockMs() - startedAt < timeLimitMs) return true
        finishSlice()
        return false
    }

    private fun finishSlice() {
        sliceEnd = if (advanced) SliceEnd.Advanced else SliceEnd.NoProgress
    }

    /** Called after consuming a PCM packet. A checkpoint is useful only after increasing the covered timestamp. */
    fun afterPacket(positionUs: Long, durationUs: Long, peaks: PcmPeaks): Boolean {
        val now = clockMs()
        val endOfSlice = timeLimitMs != null && now - startedAt >= timeLimitMs
        if (!endOfSlice && now - lastAttemptAt < checkpointIntervalMs) return true
        lastAttemptAt = now
        if (positionUs > savedPositionUs && positionUs < durationUs) {
            val waveform = peaks.waveform()
            if (waveform != null && save(WaveformCheckpoint(durationUs, positionUs, waveform))) {
                savedPositionUs = positionUs
                advanced = true
            }
        }
        if (endOfSlice) finishSlice()
        return !endOfSlice
    }
}
