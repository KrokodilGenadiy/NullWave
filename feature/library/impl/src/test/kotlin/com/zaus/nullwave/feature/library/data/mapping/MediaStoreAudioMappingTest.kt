package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort
import com.zaus.nullwave.feature.library.data.source.audioRow
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreAudioMappingTest {
    @Test
    fun `artwork is volume scoped and shared by album without changing track identity`() = runTest {
        val first = audioRow(id = 1).copy(albumId = 8, sizeBytes = 6_000_000_000, dateModifiedSeconds = 200)
        val second = first.copy(id = 2, sizeBytes = 10)
        val card = first.copy(volume = "card")
        val tracks = listOf(first, second, card).toTracks(LibraryTrackQuery("en")).associateBy { it.id.value }
        assertEquals(6_000_000_000, tracks.getValue("external_primary:1").sizeBytes)
        assertEquals(tracks.getValue("external_primary:1").artwork, tracks.getValue("external_primary:2").artwork)
        assertNotEquals(tracks.getValue("external_primary:1").artwork, tracks.getValue("card:1").artwork)
    }

    @Test
    fun `album revisions ignore query order but invalidate when any member changes or disappears`() = runTest {
        val rows = listOf(audioRow(id = 1).copy(albumId = 8, dateModifiedSeconds = 900),
            audioRow(id = 2).copy(albumId = 8, dateModifiedSeconds = 100, sizeBytes = 10))
        val original = rows.artworkReferences()
        assertEquals(original, rows.reversed().artworkReferences())
        assertNotEquals(original, (rows.take(1) + rows[1].copy(dateModifiedSeconds = 101)).artworkReferences())
        assertNotEquals(original, (rows.take(1) + rows[1].copy(sizeBytes = 11)).artworkReferences())
        assertNotEquals(original, rows.take(1).artworkReferences())
    }

    @Test
    fun `unknown or invalid optional size and album are not invented`() = runTest {
        val track = listOf(audioRow().copy(sizeBytes = -1, albumId = -1)).toTracks(LibraryTrackQuery("en")).single()
        assertNull(track.sizeBytes)
        assertNull(track.artwork)
        assertEquals(0L, listOf(audioRow().copy(sizeBytes = 0)).toTracks(LibraryTrackQuery("en")).single().sizeBytes)
    }

    @Test
    fun `missing tags use display name and null metadata without discarding short audio`() = runTest {
        val rows = listOf(audioRow(title = " <unknown> ", artist = " ").copy(album = "<unknown>", durationMs = -1))
        val track = rows.toTracks(LibraryTrackQuery("en")).single()
        assertEquals("track.mp3", track.title)
        assertNull(track.artist)
        assertNull(track.album)
        assertNull(track.durationMs)
        assertEquals(500L, rows.map { it.copy(durationMs = 500) }.toTracks(LibraryTrackQuery("en")).single().durationMs)
    }

    @Test
    fun `volume is part of identity and equal titles have deterministic tie breaking`() = runTest {
        val rows = listOf(audioRow(volume = "card"), audioRow(volume = "external_primary"))
        val first = rows.toTracks(LibraryTrackQuery("en"))
        val second = rows.reversed().toTracks(LibraryTrackQuery("en", direction = SortDirection.Descending))
        assertNotEquals(first[0].id, first[1].id)
        assertEquals(first, second)
    }

    @Test
    fun `title sorting honors locale as well as direction`() = runTest {
        val rows = listOf(audioRow(id = 1, title = "ä"), audioRow(id = 2, title = "z"))
        assertEquals(listOf("ä", "z"), rows.toTracks(LibraryTrackQuery("de")).map { it.title })
        assertEquals(listOf("z", "ä"), rows.toTracks(LibraryTrackQuery("sv")).map { it.title })
        assertEquals(listOf("ä", "z"), rows.toTracks(LibraryTrackQuery("sv", direction = SortDirection.Descending)).map { it.title })
    }

    @Test
    fun `missing artists remain last in both sort directions`() = runTest {
        val rows = listOf(audioRow(id = 1, artist = null), audioRow(id = 2, artist = "B"), audioRow(id = 3, artist = "A"))
        assertEquals(listOf("A", "B", null), rows.toTracks(LibraryTrackQuery("en", LibraryTrackSort.Artist)).map { it.artist })
        assertEquals(listOf("B", "A", null), rows.toTracks(LibraryTrackQuery("en", LibraryTrackSort.Artist, SortDirection.Descending)).map { it.artist })
    }

    @Test
    fun `recently added uses numeric dates and missing dates stay last`() = runTest {
        val rows = listOf(audioRow(id = 1, added = 0), audioRow(id = 2, added = 200), audioRow(id = 3, added = 100))
        assertEquals(listOf("external_primary:3", "external_primary:2", "external_primary:1"),
            rows.toTracks(LibraryTrackQuery("en", LibraryTrackSort.RecentlyAdded)).map { it.id.value })
        assertEquals(listOf("external_primary:2", "external_primary:3", "external_primary:1"),
            rows.toTracks(LibraryTrackQuery("en", LibraryTrackSort.RecentlyAdded, SortDirection.Descending)).map { it.id.value })
    }

    @Test
    fun `unusable rows are failures rather than silently disappearing from a successful catalog`() = runTest {
        for (row in listOf(audioRow(volume = null), audioRow(id = -1), audioRow(title = null).copy(displayName = null))) {
            val problem = runCatching { listOf(row).toTracks(LibraryTrackQuery("en")) }.exceptionOrNull()
            assertTrue(problem is IOException)
        }
    }
}
