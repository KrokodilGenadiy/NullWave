package com.zaus.nullwave.core.playback.runtime

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.playback.preparation.PreparedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class QueueCommitTest {
    @Test
    fun `commit gives repeated resources distinct occurrences and preserves captured selection`() {
        val prepared = prepared()
        var next = 0
        val identities = PlaybackIdentities(PlaybackSessionId("session")) { QueueEntryId("entry-${next++}") }

        val commit = QueueCommit.create(prepared, identities)

        assertSame(prepared, commit.prepared)
        assertEquals(listOf(TrackId("a"), TrackId("b"), TrackId("a")), commit.entries.map { it.track.id })
        assertEquals(listOf("entry-0", "entry-1", "entry-2"), commit.entries.map { it.entryId.value })
        assertSame(commit.entries[0].track, commit.entries[2].track)
        assertEquals(commit.entries[2].entryId, commit.current.entryId)
        assertEquals(PlaybackSessionId("session"), commit.current.sessionId)
        assertEquals(0L, commit.current.activation)
        assertEquals(Long.MAX_VALUE, commit.prepared.request.startPositionMs)
        assertFalse(commit.prepared.request.playRequested)
        assertEquals("opaque:a", commit.prepared.items[2].resourceUri)
    }

    @Test
    fun `later commit has fresh identities and leaves previous commit unchanged`() {
        var next = 0
        val identities = PlaybackIdentities(PlaybackSessionId("session")) { QueueEntryId("entry-${next++}") }
        val first = QueueCommit.create(prepared(), identities)
        val originalEntries = first.entries.toList()
        val originalToken = first.current

        val second = QueueCommit.create(prepared(), identities)

        assertEquals(originalEntries, first.entries)
        assertEquals(originalToken, first.current)
        assertEquals(emptySet<QueueEntryId>(), first.entries.map { it.entryId }.toSet().intersect(second.entries.map { it.entryId }.toSet()))
        assertEquals(first.current.activation + 1, second.current.activation)
        assertNotEquals(first.current, second.current)
    }

    @Test
    fun `commit entries and collection views reject mutation`() {
        val commit = QueueCommit.create(prepared(), PlaybackIdentities())
        val before = commit.entries.toList()

        mutationRejected { (commit.entries as MutableList<QueueEntry>).clear() }
        assertEquals(before, commit.entries)
        mutationRejected {
            val iterator = commit.entries.iterator() as MutableIterator<QueueEntry>
            iterator.next()
            iterator.remove()
        }
        assertEquals(before, commit.entries)
        mutationRejected { (commit.entries.subList(0, 1) as MutableList<QueueEntry>).clear() }
        assertEquals(before, commit.entries)
    }

    @Test
    fun `duplicate occurrence identity supplier cannot produce a commit`() {
        val identities = PlaybackIdentities(PlaybackSessionId("session")) { QueueEntryId("duplicate") }

        assertThrows(IllegalStateException::class.java) { QueueCommit.create(prepared(), identities) }
    }

    @Test
    fun `reactivating the same occurrence gets a new token within the same session`() {
        val identities = PlaybackIdentities(PlaybackSessionId("session"))
        val a = QueueEntryId("a")
        val b = QueueEntryId("b")

        val firstA = identities.activate(a)
        val middle = identities.activate(b)
        val secondA = identities.activate(a)

        assertEquals(listOf(0L, 1L, 2L), listOf(firstA, middle, secondA).map { it.activation })
        assertEquals(firstA.entryId, secondA.entryId)
        assertEquals(firstA.sessionId, secondA.sessionId)
        assertNotEquals(firstA, secondA)
    }

    @Test
    fun `new identity source does not reuse the previous service session`() {
        val first = PlaybackIdentities()
        val second = PlaybackIdentities()

        assertNotEquals(first.sessionId, second.sessionId)
        assertNotEquals(first.newEntryId(), second.newEntryId())
    }

    private fun prepared(): PreparedQueue {
        val a = resolved("a")
        val b = resolved("b")
        val request = PlaybackQueueRequest.create(
            listOf(a.track.id, b.track.id, a.track.id),
            startIndex = 2,
            startPositionMs = Long.MAX_VALUE,
            playRequested = false,
        )
        return PreparedQueue.create(request, listOf(a, b, a))
    }

    private fun resolved(value: String) = ResolvedPlaybackTrack(
        MediaTrack(TrackId(value), value, artist = null, album = null, durationMs = null),
        "opaque:$value",
    )

    private fun mutationRejected(mutation: () -> Unit) {
        try {
            mutation()
            fail("Committed entries must reject mutation")
        } catch (_: ClassCastException) {
            // Kotlin read-only implementations can reject the mutable interface itself.
        } catch (_: UnsupportedOperationException) {
            // JVM collection views can expose the mutable interface but reject writes.
        }
    }
}
