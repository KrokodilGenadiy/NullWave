package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.data.source.audioRow
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreCatalogMappingTest {
    private val albumQuery = LibraryAlbumQuery("en")
    private val artistQuery = LibraryArtistQuery("en")

    @Test
    fun `equal names and numeric IDs do not merge entities across volumes`() = runTest {
        val rows = listOf(
            audioRow(id = 1),
            audioRow(id = 2),
            audioRow(id = 1, volume = "card"),
            audioRow(id = 3).copy(albumId = 9, artistId = 5),
        )
        val albums = rows.toAlbums(albumQuery).associateBy { it.id.value }
        val artists = rows.toArtists(artistQuery).associateBy { it.id.value }
        assertEquals(setOf("external_primary:8", "external_primary:9", "card:8"), albums.keys)
        assertEquals(setOf("external_primary:4", "external_primary:5", "card:4"), artists.keys)
        assertEquals(2, albums.getValue("external_primary:8").trackCount)
        assertEquals(1, albums.getValue("card:8").trackCount)
        assertEquals(2, artists.getValue("external_primary:4").trackCount)
        assertEquals(1, artists.getValue("card:4").trackCount)
        assertNotEquals(albums.getValue("card:8").artwork, albums.getValue("external_primary:8").artwork)
    }

    @Test
    fun `missing or invalid membership omits groups but keeps recordings in Songs`() = runTest {
        val rows = listOf(
            audioRow(id = 1).copy(albumId = null, artistId = null, durationMs = 500),
            audioRow(id = 2).copy(albumId = -1, artistId = -1, durationMs = 0),
        )
        assertTrue(rows.toAlbums(albumQuery).isEmpty())
        assertTrue(rows.toArtists(artistQuery).isEmpty())
        assertEquals(2, rows.toTracks(LibraryTrackQuery("en")).size)
    }

    @Test
    fun `zero and large IDs stay valid and do not truncate to int`() = runTest {
        val rows = listOf(
            audioRow(id = 1).copy(albumId = 0, artistId = 0),
            audioRow(id = 2).copy(albumId = 5_000_000_001, artistId = 6_000_000_001),
        )
        assertEquals(setOf("external_primary:0", "external_primary:5000000001"),
            rows.toAlbums(albumQuery).map { it.id.value }.toSet())
        assertEquals(setOf("external_primary:0", "external_primary:6000000001"),
            rows.toArtists(artistQuery).map { it.id.value }.toSet())
    }

    @Test
    fun `unknown labels keep valid identity and normalize provider sentinel`() = runTest {
        val rows = listOf(audioRow(artist = " <UnKnOwN> ").copy(album = " \t"))
        val album = rows.toAlbums(albumQuery).single()
        val artist = rows.toArtists(artistQuery).single()
        assertNull(album.title)
        assertEquals(AlbumArtistCredit.Unknown, album.artistCredit)
        assertNull(artist.name)
        assertEquals(1, album.trackCount)
        assertEquals(1, artist.trackCount)
        assertEquals(1, artist.albumCount)
    }

    @Test
    fun `credit distinguishes single artist compilation and partial unknown membership`() = runTest {
        val first = audioRow()
        val known = listOf(first, first.copy(id = 2))
        assertEquals(AlbumArtistCredit.Named("Nullwave"), known.toAlbums(albumQuery).single().artistCredit)
        // Equal display names do not make two artist identities the same artist.
        val compilation = listOf(first, first.copy(id = 2, artistId = 5))
        assertEquals(AlbumArtistCredit.Various, compilation.toAlbums(albumQuery).single().artistCredit)
        for (unknown in listOf(first.copy(id = 2, artistId = null), first.copy(id = 2, artist = null))) {
            assertEquals(AlbumArtistCredit.Various, listOf(first, unknown).toAlbums(albumQuery).single().artistCredit)
            assertEquals(AlbumArtistCredit.Unknown, listOf(unknown).toAlbums(albumQuery).single().artistCredit)
        }
    }

    @Test
    fun `artist counts distinct albums including compilations and keeps tracks without an album`() = runTest {
        val rows = listOf(
            audioRow(id = 1),
            audioRow(id = 2),
            audioRow(id = 3).copy(albumId = 9),
            audioRow(id = 4).copy(albumId = null),
            audioRow(id = 5).copy(albumId = -1),
            audioRow(id = 6, artist = "Guest").copy(artistId = 5),
        )
        val artists = rows.toArtists(artistQuery).associateBy { it.id.value }
        assertEquals(5, artists.getValue("external_primary:4").trackCount)
        assertEquals(2, artists.getValue("external_primary:4").albumCount)
        assertEquals(1, artists.getValue("external_primary:5").trackCount)
        assertEquals(1, artists.getValue("external_primary:5").albumCount)
        assertEquals(3, rows.toAlbums(albumQuery).single { it.id.value == "external_primary:8" }.trackCount)
        assertEquals(0, rows.filter { it.albumId == null }.toArtists(artistQuery).single().albumCount)
    }

    @Test
    fun `representative metadata is trimmed known and stable across cursor order`() = runTest {
        val rows = listOf(
            audioRow(id = 1, artist = "<unknown>").copy(album = null),
            audioRow(id = 3, artist = "Z").copy(album = "Z"),
            audioRow(id = 2, artist = " AC/DC & Friends, Inc. ").copy(album = " Signals "),
        )
        val albums = rows.toAlbums(albumQuery)
        val artists = rows.toArtists(artistQuery)
        assertEquals("Signals", albums.single().title)
        assertEquals("AC/DC & Friends, Inc.", artists.single().name)
        assertEquals(3, artists.single().trackCount)
        assertEquals(albums, rows.reversed().toAlbums(albumQuery))
        assertEquals(artists, rows.reversed().toArtists(artistQuery))
        val named = rows.drop(1)
        assertEquals(AlbumArtistCredit.Named("AC/DC & Friends, Inc."), named.toAlbums(albumQuery).single().artistCredit)
    }

    @Test
    fun `locale changes ordering for both groupings without changing group identity`() = runTest {
        val rows = listOf(
            audioRow(id = 1, artist = "ä").copy(album = "ä"),
            audioRow(id = 2, artist = "z").copy(albumId = 9, artistId = 5, album = "z"),
        )
        for ((locale, expected) in listOf("de" to listOf("ä", "z"), "sv" to listOf("z", "ä"))) {
            assertEquals(expected, rows.toAlbums(albumQuery.copy(localeTag = locale)).map { it.title })
            assertEquals(expected, rows.toArtists(artistQuery.copy(localeTag = locale)).map { it.name })
        }
    }

    @Test
    fun `unknowns stay last and equal names use ascending identity in either direction`() = runTest {
        val rows = listOf(
            audioRow(id = 1, artist = null).copy(albumId = 0, artistId = 0, album = null),
            audioRow(id = 2, artist = "B").copy(albumId = 2, artistId = 2, album = "B"),
            audioRow(id = 3, artist = "A").copy(albumId = 3, artistId = 3, album = "A"),
            audioRow(id = 4, artist = "A").copy(albumId = 1, artistId = 1, album = "A"),
        )
        for (direction in SortDirection.entries) {
            val ids = if (direction == SortDirection.Ascending) listOf(1, 3, 2, 0) else listOf(2, 1, 3, 0)
            val expected = ids.map { "external_primary:$it" }
            assertEquals(expected, rows.toAlbums(albumQuery.copy(direction = direction)).map { it.id.value })
            assertEquals(expected, rows.reversed().toArtists(artistQuery.copy(direction = direction)).map { it.id.value })
        }
    }

    @Test
    fun `album artwork equals Songs artwork and changes when a member changes`() = runTest {
        val rows = listOf(audioRow(id = 1), audioRow(id = 2).copy(sizeBytes = 100))
        val album = rows.toAlbums(albumQuery).single()
        assertEquals(rows.toTracks(LibraryTrackQuery("en")).first().artwork, album.artwork)
        assertEquals(album.artwork, rows.reversed().toAlbums(albumQuery).single().artwork)
        assertNotEquals(album.artwork, rows.take(1).toAlbums(albumQuery).single().artwork)
        val edited = rows.take(1) + rows.last().copy(dateModifiedSeconds = 900)
        assertNotEquals(album.artwork, edited.toAlbums(albumQuery).single().artwork)
        assertEquals(2, album.trackCount) // Published values are not modified by the next mapping.
    }

    @Test
    fun `malformed audio identity fails group mapping even without group membership`() = runTest {
        for (row in listOf(audioRow(volume = null), audioRow(volume = " "), audioRow(id = -1))) {
            val rows = listOf(row.copy(albumId = null, artistId = null))
            assertTrue(runCatching { rows.toAlbums(albumQuery) }.exceptionOrNull() is IOException)
            assertTrue(runCatching { rows.toArtists(artistQuery) }.exceptionOrNull() is IOException)
        }
    }

    @Test
    fun `mapping loops observe cancellation while inspecting rows`() = runTest {
        // Cancel inside iteration, after withContext has entered, to exercise mapper checkpoints.
        suspend fun checkMapping(albums: Boolean) {
            val job = Job(currentCoroutineContext()[Job])
            try {
                val rows = object : AbstractList<MediaStoreAudioRow>() {
                    override val size = 2
                    override fun get(index: Int) = audioRow(id = index.toLong()).also { job.cancel() }
                }
                val failure = runCatching {
                    withContext(job) {
                        if (albums) rows.toAlbums(albumQuery) else rows.toArtists(artistQuery)
                        error("Mapper ignored cancellation")
                    }
                }.exceptionOrNull()
                assertTrue(failure is CancellationException)
            } finally {
                job.cancel()
            }
        }
        checkMapping(albums = true)
        checkMapping(albums = false)
    }
}
