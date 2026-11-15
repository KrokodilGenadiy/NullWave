package com.zaus.nullwave.feature.library.api.model.album

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LibraryAlbumDetailsTest {
    private val album = LibraryAlbum(AlbumId("primary:7"), "Signals", AlbumArtistCredit.Various, trackCount = 2)
    private val first = MediaTrack(TrackId("primary:9"), "Intro", "A", "Signals", 10_000)
    private val second = first.copy(id = TrackId("primary:3"), artist = "B")

    @Test
    fun `preserves the supplied order of distinct tracks with the same title`() {
        val input = mutableListOf(first, second)
        val details = LibraryAlbumDetails.create(album, input)

        input.reverse()
        assertThrows(UnsupportedOperationException::class.java) {
            (details.tracks as MutableList<MediaTrack>).clear()
        }
        assertEquals(listOf(first, second), details.tracks)
        assertEquals(album, details.album)
    }

    @Test
    fun `rejects empty and partial membership`() {
        assertThrows(IllegalArgumentException::class.java) { LibraryAlbumDetails.create(album, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { LibraryAlbumDetails.create(album, listOf(first)) }
    }

    @Test
    fun `rejects repeated track identity even with differing metadata`() {
        val renamed = first.copy(title = "New title")
        assertThrows(IllegalArgumentException::class.java) {
            LibraryAlbumDetails.create(album, listOf(first, renamed))
        }
    }
}
