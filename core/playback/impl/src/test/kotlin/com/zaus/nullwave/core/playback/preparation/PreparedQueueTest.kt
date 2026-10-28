package com.zaus.nullwave.core.playback.preparation

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class PreparedQueueTest {
    private val first = resolved("a")
    private val second = resolved("b")
    private val request = PlaybackQueueRequest.create(listOf(first.track.id, second.track.id, first.track.id), 2)

    @Test
    fun `factory captures caller collection without losing repeated resources`() {
        val input = mutableListOf(first, second, first)
        val prepared = PreparedQueue.create(request, input)
        input.clear()
        assertSame(request, prepared.request)
        assertEquals(listOf(first, second, first), prepared.items)
        assertSame(prepared.items[0], prepared.items[2])
    }

    @Test
    fun `incomplete extra or reordered resources cannot form a prepared queue`() {
        for (items in listOf(
            emptyList(),
            listOf(first, second),
            listOf(second, first, first),
            listOf(first, second, resolved("c")),
            listOf(first, second, first, first),
        )) {
            assertThrows(IllegalArgumentException::class.java) { PreparedQueue.create(request, items) }
        }
    }

    @Test
    fun `published items and their collection views reject mutation`() {
        val prepared = PreparedQueue.create(request, listOf(first, second, first))
        val before = prepared.items.toList()
        mutationRejected { (prepared.items as MutableList<ResolvedPlaybackTrack>).clear() }
        assertEquals(before, prepared.items)
        mutationRejected {
            val iterator = prepared.items.iterator() as MutableIterator<ResolvedPlaybackTrack>
            iterator.next()
            iterator.remove()
        }
        assertEquals(before, prepared.items)
        mutationRejected { (prepared.items.subList(0, 1) as MutableList<ResolvedPlaybackTrack>).clear() }
        assertEquals(before, prepared.items)
    }

    private fun mutationRejected(mutation: () -> Unit) {
        try {
            mutation()
            fail("Prepared resources must reject mutation")
        } catch (_: ClassCastException) {
            // Kotlin read-only implementations may reject the mutable interface itself.
        } catch (_: UnsupportedOperationException) {
            // JVM read-only views may expose the mutable interface but reject writes.
        }
    }

    private fun resolved(value: String) = ResolvedPlaybackTrack(
        MediaTrack(TrackId(value), value, artist = null, album = null, durationMs = null),
        "opaque:$value",
    )
}
