package com.zaus.nullwave.core.playback.api.model

/**
 * Position anchor on the shared monotonic clock, independent of control/queue updates.
 * Newly revised duration/buffering may temporarily be behind position; rendering clamps as needed.
 */
data class PlaybackProgress(
    val target: PlaybackItemToken,
    val positionRevision: Long,
    val positionMs: Long,
    val durationMs: Long?,
    val bufferedPositionMs: Long?,
    val sampledAtMs: Long,
    val advancing: Boolean,
    val speed: Float = 1f,
) {
    init {
        require(positionRevision >= 0) { "Position revision must not be negative." }
        require(positionMs >= 0 && sampledAtMs >= 0) { "Position and sample time must not be negative." }
        require(durationMs == null || durationMs >= 0) { "Unknown duration must be null." }
        require(bufferedPositionMs == null || bufferedPositionMs >= 0) { "Unknown buffered position must be null." }
        require(speed.isFinite() && speed > 0f) { "Playback speed must be finite and positive." }
    }

    /** Only a matching control generation may display this anchor. Older sample times are filtered by the consumer. */
    fun matches(snapshot: PlaybackSnapshot): Boolean = target.sessionId == snapshot.sessionId &&
        target == snapshot.queue.current && positionRevision == snapshot.positionRevision

    /**
     * Pure UI interpolation, never a playback command. Clamp a backwards clock to zero elapsed and
     * bound extrapolation to [maxExtrapolationMs]; stopped anchors retain exact Long precision.
     */
    fun positionAt(nowMs: Long, maxExtrapolationMs: Long = 1_000): Long {
        require(nowMs >= 0 && maxExtrapolationMs >= 0) { "Clock time and extrapolation horizon must not be negative." }
        val limit = durationMs ?: Long.MAX_VALUE
        val elapsed = if (nowMs >= sampledAtMs) (nowMs - sampledAtMs).coerceAtMost(maxExtrapolationMs) else 0L
        val anchor = positionMs.coerceAtMost(limit)
        if (!advancing || elapsed == 0L) return anchor
        // Convert only the increment: large Long anchors must not lose precision through Double.
        val increment = (elapsed.toDouble() * speed.toDouble()).toLong()
        return anchor + increment.coerceAtMost(limit - anchor)
    }
}
