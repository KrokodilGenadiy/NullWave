package com.zaus.nullwave.feature.library.ui

import com.zaus.nullwave.feature.library.api.ui.libraryMonogram
import com.zaus.nullwave.feature.library.api.ui.librarySubtitle
import com.zaus.nullwave.feature.library.api.ui.libraryDuration
import com.zaus.nullwave.feature.library.api.ui.libraryAlbumCredit
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrack
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.track.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LibraryFormattingTest {
    @Test
    fun `album credit distinguishes missing tags from compilation credits without translating names`() {
        assertEquals("Unknown", libraryAlbumCredit(AlbumArtistCredit.Unknown, "Unknown", "Various"))
        assertEquals("Various", libraryAlbumCredit(AlbumArtistCredit.Various, "Unknown", "Various"))
        assertEquals("Сигнал", libraryAlbumCredit(AlbumArtistCredit.Named("Сигнал"), "Unknown", "Various"))
    }

    @Test
    fun `subtitle uses the available tags without dangling separators`() {
        assertEquals("Artist · Album", librarySubtitle("Artist", "Album", "Unknown"))
        assertEquals("Artist", librarySubtitle("Artist", null, "Unknown"))
        assertEquals("Album", librarySubtitle(null, "Album", "Unknown"))
        assertEquals("Unknown", librarySubtitle(null, null, "Unknown"))
    }

    @Test
    fun `monogram supports single words Cyrillic and supplementary code points`() {
        assertEquals("NC", libraryMonogram("  Night   City  "))
        assertEquals("НГ", libraryMonogram("Ночной город"))
        assertEquals("TR", libraryMonogram("Transmission"))
        assertEquals("Я", libraryMonogram("я"))
        assertEquals("🎵Н", libraryMonogram("🎵Ночь"))
    }

    @Test
    fun `total distinguishes complete empty partial and overflowing metadata`() {
        val track = LibraryTrack(TrackId("test:1"), "Title", null, null, null)
        assertEquals(0L, libraryTotalBytes(emptyList()))
        assertEquals(6_000_000_001L, libraryTotalBytes(listOf(track.copy(sizeBytes = 6_000_000_000), track.copy(sizeBytes = 1))))
        assertNull(libraryTotalBytes(listOf(track.copy(sizeBytes = 500), track)))
        assertNull(libraryTotalBytes(listOf(track.copy(sizeBytes = Long.MAX_VALUE), track.copy(sizeBytes = 1))))
        assertThrows(IllegalArgumentException::class.java) { track.copy(sizeBytes = -1) }
    }

    @Test
    fun `unknown duration differs from a known zero`() {
        assertNull(libraryDuration(null))
        assertEquals("0:00", libraryDuration(0))
    }

    @Test
    fun `duration floors milliseconds and pads seconds and minutes`() {
        assertEquals("0:59", libraryDuration(59_999))
        assertEquals("1:00", libraryDuration(60_000))
        assertEquals("59:59", libraryDuration(3_599_999))
        assertEquals("1:00:01", libraryDuration(3_601_000))
    }

    @Test
    fun `long recordings do not wrap at a day or overflow`() {
        assertEquals("25:01:01", libraryDuration(90_061_000))
        assertEquals("2562047788015:12:55", libraryDuration(Long.MAX_VALUE))
    }

    @Test
    fun `negative known duration is a contract violation`() {
        assertThrows(IllegalArgumentException::class.java) { libraryDuration(-1) }
    }
}
