package com.zaus.nullwave.core.playback.api.model

/** Opaque identity of one live service/session incarnation; never restored as live work. */
@JvmInline
value class PlaybackSessionId(val value: String) {
    init { require(value.isNotBlank()) { "Playback session identity must not be blank." } }
}

/** Identity of one queue occurrence. Repeated tracks must have different entry identities. */
@JvmInline
value class QueueEntryId(val value: String) {
    init { require(value.isNotBlank()) { "Queue entry identity must not be blank." } }
}

/** Identity of an expected failure, allowing a stale retry to be rejected. */
@JvmInline
value class PlaybackProblemId(val value: String) {
    init { require(value.isNotBlank()) { "Playback problem identity must not be blank." } }
}

/**
 * Identifies an activation, including A -> B -> A and repeat of the same queue occurrence.
 * A normal seek changes positionRevision, not this activation.
 */
data class PlaybackItemToken(
    val sessionId: PlaybackSessionId,
    val entryId: QueueEntryId,
    val activation: Long,
) {
    init { require(activation >= 0) { "Playback activation must not be negative." } }
}
