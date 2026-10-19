package com.zaus.nullwave.feature.library.data

import com.zaus.nullwave.feature.library.api.model.LibraryQuery
import com.zaus.nullwave.feature.library.api.model.LibrarySort
import com.zaus.nullwave.feature.library.api.model.SortDirection
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreAudioMappingTest {
    @Test
    fun `missing tags use display name and null metadata without discarding short audio`() = runTest {
        val rows = listOf(audioRow(title = " <unknown> ", artist = " ").copy(album = "<unknown>", durationMs = -1))
        val track = rows.toTracks(LibraryQuery("en")).single()
        assertEquals("track.mp3", track.title)
        assertNull(track.artist)
        assertNull(track.album)
        assertNull(track.durationMs)
        assertEquals(500L, rows.map { it.copy(durationMs = 500) }.toTracks(LibraryQuery("en")).single().durationMs)
    }

    @Test
    fun `volume is part of identity and equal titles have deterministic tie breaking`() = runTest {
        val rows = listOf(audioRow(volume = "card"), audioRow(volume = "external_primary"))
        val first = rows.toTracks(LibraryQuery("en"))
        val second = rows.reversed().toTracks(LibraryQuery("en", direction = SortDirection.Descending))
        assertNotEquals(first[0].id, first[1].id)
        assertEquals(first, second)
    }

    @Test
    fun `title sorting honors locale as well as direction`() = runTest {
        val rows = listOf(audioRow(id = 1, title = "ä"), audioRow(id = 2, title = "z"))
        assertEquals(listOf("ä", "z"), rows.toTracks(LibraryQuery("de")).map { it.title })
        assertEquals(listOf("z", "ä"), rows.toTracks(LibraryQuery("sv")).map { it.title })
        assertEquals(listOf("ä", "z"), rows.toTracks(LibraryQuery("sv", direction = SortDirection.Descending)).map { it.title })
    }

    @Test
    fun `missing artists remain last in both sort directions`() = runTest {
        val rows = listOf(audioRow(id = 1, artist = null), audioRow(id = 2, artist = "B"), audioRow(id = 3, artist = "A"))
        assertEquals(listOf("A", "B", null), rows.toTracks(LibraryQuery("en", LibrarySort.Artist)).map { it.artist })
        assertEquals(listOf("B", "A", null), rows.toTracks(LibraryQuery("en", LibrarySort.Artist, SortDirection.Descending)).map { it.artist })
    }

    @Test
    fun `recently added uses numeric dates and missing dates stay last`() = runTest {
        val rows = listOf(audioRow(id = 1, added = 0), audioRow(id = 2, added = 200), audioRow(id = 3, added = 100))
        assertEquals(listOf("external_primary:3", "external_primary:2", "external_primary:1"),
            rows.toTracks(LibraryQuery("en", LibrarySort.RecentlyAdded)).map { it.id.value })
        assertEquals(listOf("external_primary:2", "external_primary:3", "external_primary:1"),
            rows.toTracks(LibraryQuery("en", LibrarySort.RecentlyAdded, SortDirection.Descending)).map { it.id.value })
    }

    @Test
    fun `unusable rows are failures rather than silently disappearing from a successful catalog`() = runTest {
        for (row in listOf(audioRow(volume = null), audioRow(id = -1), audioRow(title = null).copy(displayName = null))) {
            val problem = runCatching { listOf(row).toTracks(LibraryQuery("en")) }.exceptionOrNull()
            assertTrue(problem is IOException)
        }
    }
}
