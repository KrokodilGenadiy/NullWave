package com.zaus.nullwave.core.playback.api.model

import com.zaus.nullwave.core.playback.api.internal.snapshotList

/**
 * Immutable queue snapshot. [entries] is the base order; [playOrder] is the engine's traversal order.
 * Unlike track identities, entry identities are unique. Factories validate programmer contracts;
 * a platform adapter must classify malformed external data before constructing snapshots.
 *
 * Use [create] to capture protected collection snapshots; the constructor and generated copy are private.
 */
@ConsistentCopyVisibility
data class PlaybackQueue private constructor(
    val entries: List<QueueEntry>,
    val playOrder: List<QueueEntryId>,
    val current: PlaybackItemToken?,
    val revision: Long,
    val repeatMode: RepeatMode,
    val shuffleEnabled: Boolean,
) {
    /** The current occurrence has a successor in this traversal, before any repeat wrap. */
    val hasUpcomingEntries: Boolean get() = playOrder.lastOrNull() != current?.entryId

    init {
        require(revision >= 0) { "Queue revision must not be negative." }
        val identities = entries.map { it.entryId }
        val identitySet = identities.toSet()
        require(identitySet.size == identities.size) { "Queue entry identities must be unique." }
        require(playOrder.size == identities.size && playOrder.toSet() == identitySet) {
            "Playback order must be a permutation of queue entry identities."
        }
        require(shuffleEnabled || playOrder == identities) { "Unshuffled playback follows the base order." }
        require(entries.isEmpty() == (current == null)) { "A nonempty committed queue requires a current item." }
        require(current == null || current.entryId in identitySet) { "Current item must belong to this queue." }
    }

    companion object {
        fun create(
            entries: List<QueueEntry> = emptyList(),
            playOrder: List<QueueEntryId> = entries.map { it.entryId },
            current: PlaybackItemToken? = null,
            revision: Long = 0,
            repeatMode: RepeatMode = RepeatMode.Off,
            shuffleEnabled: Boolean = false,
        ): PlaybackQueue = PlaybackQueue(
            entries = entries.snapshotList(),
            playOrder = playOrder.snapshotList(),
            current = current,
            revision = revision,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
        )
    }
}
