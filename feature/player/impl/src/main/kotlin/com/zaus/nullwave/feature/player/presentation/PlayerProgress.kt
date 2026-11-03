package com.zaus.nullwave.feature.player.presentation

/** Display-only fraction. Missing/zero engine duration stays unknown; catalog metadata is not a clock. */
internal fun PlayerState.progressFractionAt(nowMs: Long): Float? {
    val duration = progress?.durationMs?.takeIf { it > 0 } ?: return null
    val position = positionAt(nowMs) ?: return null
    return (position.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
}
