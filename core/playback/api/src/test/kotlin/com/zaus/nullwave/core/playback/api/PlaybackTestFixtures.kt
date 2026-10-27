package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

internal val session = PlaybackSessionId("session-1")
internal val firstEntryId = QueueEntryId("entry-1")
internal val firstToken = PlaybackItemToken(session, firstEntryId, activation = 1)

internal fun mediaTrack(id: String = "volume:1") = MediaTrack(
    id = TrackId(id),
    title = "Track $id",
    artist = null,
    album = null,
    durationMs = null,
)

internal fun queueEntry(id: String = "entry-1", trackId: String = "volume:1") =
    QueueEntry(QueueEntryId(id), mediaTrack(trackId))

internal fun currentQueue(token: PlaybackItemToken = firstToken) = PlaybackQueue.create(
    entries = listOf(QueueEntry(token.entryId, mediaTrack())),
    current = token,
)

internal fun <T> assertReadOnlyList(values: List<T>, extra: T) {
    val before = values.toList()
    assertMutationRejected { (values as MutableList<T>).add(extra) }
    assertEquals(before, values)
    if (values.isNotEmpty()) {
        assertMutationRejected {
            val iterator = values.iterator() as MutableIterator<T>
            iterator.next()
            iterator.remove()
        }
        assertEquals(before, values)
        assertMutationRejected { (values.subList(0, 1) as MutableList<T>).clear() }
        assertEquals(before, values)
    }
}

internal fun <T> assertReadOnlySet(values: Set<T>, extra: T) {
    val before = values.toSet()
    assertMutationRejected { (values as MutableSet<T>).add(extra) }
    assertEquals(before, values)
    if (values.isNotEmpty()) {
        assertMutationRejected {
            val iterator = values.iterator() as MutableIterator<T>
            iterator.next()
            iterator.remove()
        }
        assertEquals(before, values)
    }
}

private fun assertMutationRejected(mutation: () -> Unit) {
    try {
        mutation()
        fail("Published collection must reject mutation")
    } catch (_: ClassCastException) {
        // A Kotlin read-only collection may reject the mutable interface itself.
    } catch (_: UnsupportedOperationException) {
        // A JVM unmodifiable wrapper may expose the interface but reject its operation.
    }
}
