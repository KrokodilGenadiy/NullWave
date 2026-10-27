package com.zaus.nullwave.core.media.model

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaTrackTest {
    private val track = MediaTrack(TrackId("source:1"), "Transmission", null, null, null)

    @Test
    fun `unknown metadata stays absent without invented labels or artwork`() {
        assertNull(track.artist)
        assertNull(track.album)
        assertNull(track.durationMs)
        assertNull(track.sizeBytes)
        assertNull(track.artwork)
    }

    @Test
    fun `known zero duration and size remain distinct from unknown`() {
        val zero = track.copy(durationMs = 0, sizeBytes = 0)
        assertEquals(0L, zero.durationMs)
        assertEquals(0L, zero.sizeBytes)
        assertNotEquals(track, zero)
    }

    @Test
    fun `identical metadata does not merge separate source identities`() {
        val other = track.copy(id = TrackId("second-source:1"))
        assertEquals(track.title, other.title)
        assertNotEquals(track.id, other.id)
        assertNotEquals(track, other)
    }

    @Test
    fun `valid source metadata is preserved rather than normalized again`() {
        val artwork = ArtworkRef("source:cover", "revision:1")
        val reported = track.copy(
            title = "  Original title  ",
            artist = "Artist & Guest",
            album = "Альбом",
            durationMs = Long.MAX_VALUE,
            sizeBytes = Long.MAX_VALUE,
            artwork = artwork,
        )
        assertEquals("  Original title  ", reported.title)
        assertEquals("Artist & Guest", reported.artist)
        assertEquals("Альбом", reported.album)
        assertEquals(Long.MAX_VALUE, reported.durationMs)
        assertEquals(Long.MAX_VALUE, reported.sizeBytes)
        assertEquals(artwork, reported.artwork)
        assertEquals(track.id, reported.id)
    }

    @Test
    fun `blank required title and known tag labels are rejected through constructor and copy`() {
        for (blank in listOf("", " ", "\t\n")) {
            assertThrows(IllegalArgumentException::class.java) {
                MediaTrack(track.id, blank, null, null, null)
            }
            assertThrows(IllegalArgumentException::class.java) { track.copy(title = blank) }
            assertThrows(IllegalArgumentException::class.java) { track.copy(artist = blank) }
            assertThrows(IllegalArgumentException::class.java) { track.copy(album = blank) }
        }
    }

    @Test
    fun `negative duration and size cannot masquerade as unknown metadata`() {
        for (negative in listOf(-1L, Long.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                MediaTrack(track.id, track.title, null, null, negative)
            }
            assertThrows(IllegalArgumentException::class.java) { track.copy(durationMs = negative) }
            assertThrows(IllegalArgumentException::class.java) { track.copy(sizeBytes = negative) }
        }
    }

    @Test
    fun `metadata replacement retains identity without mutating the original snapshot`() {
        val replacement = track.copy(artist = "Artist", durationMs = 123_456)
        assertEquals(track.id, replacement.id)
        assertNull(track.artist)
        assertNull(track.durationMs)
        assertEquals("Artist", replacement.artist)
        assertEquals(123_456L, replacement.durationMs)
    }
}
