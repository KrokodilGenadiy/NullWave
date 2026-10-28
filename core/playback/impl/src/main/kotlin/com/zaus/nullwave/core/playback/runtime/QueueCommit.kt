package com.zaus.nullwave.core.playback.runtime

import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.preparation.PreparedQueue

/** A complete installation payload. It is not a confirmed engine snapshot or a live queue. */
@ConsistentCopyVisibility
internal data class QueueCommit private constructor(
    val prepared: PreparedQueue,
    val entries: List<QueueEntry>,
    val current: PlaybackItemToken,
) {
    companion object {
        fun create(prepared: PreparedQueue, identities: PlaybackIdentities): QueueCommit {
            val entries = prepared.items.map { QueueEntry(identities.newEntryId(), it.track) }
            check(entries.map { it.entryId }.toSet().size == entries.size) {
                "Playback identities must be unique for every queue occurrence."
            }
            val protectedEntries = object : AbstractList<QueueEntry>() {
                override val size: Int get() = entries.size
                override fun get(index: Int): QueueEntry = entries[index]
            }
            return QueueCommit(
                prepared,
                protectedEntries,
                identities.activate(entries[prepared.request.startIndex].entryId),
            )
        }
    }
}
