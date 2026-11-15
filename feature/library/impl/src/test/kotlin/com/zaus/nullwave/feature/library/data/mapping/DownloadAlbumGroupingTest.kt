package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.data.search.toSearchResults
import com.zaus.nullwave.feature.library.data.source.audioRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadAlbumGroupingTest {
    private val download = audioRow().copy(album = "Download", relativePath = "Download/")
    private val albumQuery = LibraryAlbumQuery("en")

    @Test
    fun `eight plus one downloads share one browsable and searchable album but keep their track artwork`() = runTest {
        val rows = (1L..9L).map { id -> download.copy(id = id, albumId = if (id == 1L) 37 else 52) }
        val album = rows.toAlbums(albumQuery).single()
        assertEquals(9, album.trackCount)
        assertEquals("Download", album.title)
        assertEquals(album, rows.reversed().toAlbums(albumQuery).single())
        assertEquals(album.id, rows.drop(1).toAlbums(albumQuery).single().id)

        val results = rows.toSearchResults(checkNotNull(LibrarySearchQuery.fromInput("download", "en")))
        assertEquals(album, results.albums.single().album)
        val tracks = rows.toTracks(LibraryTrackQuery("en"))
        assertEquals(tracks.toSet(), results.tracks.map { it.track }.toSet())
        assertEquals(9, tracks.size)
        assertEquals(setOf("external_primary:37", "external_primary:52"), tracks.map { it.artwork?.key }.toSet())
        assertEquals(tracks.single { it.id.value == "external_primary:1" }.artwork, album.artwork)

        val artist = rows.toArtists(LibraryArtistQuery("en")).single()
        assertEquals(1, artist.albumCount)
        val artistResults = rows.toSearchResults(checkNotNull(LibrarySearchQuery.fromInput("nullwave", "en")))
        assertEquals(artist, artistResults.artists.single())
        assertEquals(album, artistResults.albums.single().album)
    }

    @Test
    fun `matching titles alone never merge other albums or volumes`() = runTest {
        val otherRows = listOf(
            download.copy(id = 2, albumId = 2, relativePath = "Music/Download/"),
            download.copy(id = 3, albumId = 3, relativePath = null),
            download.copy(id = 4, albumId = 4, albumArtist = "First artist"),
            download.copy(id = 5, albumId = 5, albumArtist = "Second artist"),
            download.copy(id = 6, albumId = 6, album = "Another album"),
        )
        val rows = otherRows + download + download.copy(volume = "card") +
            download.copy(id = 7, albumId = null)
        val albums = rows.toAlbums(albumQuery)
        assertEquals(7, albums.size)
        assertEquals(List(7) { 1 }, albums.map { it.trackCount })
        for (row in otherRows) {
            assertEquals(true, albums.any { it.id.value == "external_primary:${row.albumId}" })
        }
        assertEquals(albums.filter { it.title == "Download" }.toSet(), rows.toSearchResults(
            checkNotNull(LibrarySearchQuery.fromInput("download", "en")),
        ).albums.map { it.album }.toSet())
    }
}
