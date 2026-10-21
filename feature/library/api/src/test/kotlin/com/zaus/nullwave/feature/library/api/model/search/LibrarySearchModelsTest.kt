package com.zaus.nullwave.feature.library.api.model.search

import com.zaus.nullwave.feature.library.api.model.ArtworkRef
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrack
import com.zaus.nullwave.feature.library.api.model.track.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class LibrarySearchModelsTest {
    private val track = LibraryTrack(
        id = TrackId("primary:1"),
        title = "Morning",
        artist = "Ёлка",
        album = "Transmission",
        durationMs = 30_000,
    )
    private val album = LibraryAlbum(
        id = AlbumId("primary:1"),
        title = "Transmission",
        artistCredit = AlbumArtistCredit.Various,
        trackCount = 12,
        artwork = ArtworkRef("thumbnail:1", "revision"),
    )
    private val artist = LibraryArtist(ArtistId("primary:1"), "Ёлка", trackCount = 12, albumCount = 3)
    private val trackHit = LibraryTrackSearchHit(track, "Session_07.m4a")
    private val albumHit = LibraryAlbumSearchHit(album, listOf("Ёлка"))
    private val results = LibrarySearchResults(listOf(trackHit), listOf(albumHit), listOf(artist))

    @Test
    fun `empty groups represent no matches and may be independently empty`() {
        val empty = LibrarySearchResults(emptyList(), emptyList(), emptyList())
        assertEquals(0, empty.tracks.size + empty.albums.size + empty.artists.size)
        assertEquals(listOf(trackHit), empty.copy(tracks = listOf(trackHit)).tracks)
        assertEquals(listOf(albumHit), empty.copy(albums = listOf(albumHit)).albums)
        assertEquals(listOf(artist), empty.copy(artists = listOf(artist)).artists)
    }

    @Test
    fun `file name explains matching without replacing the track title`() {
        assertSame(track, trackHit.track)
        assertEquals("Morning", trackHit.track.title)
        assertEquals("Session_07.m4a", trackHit.fileName)
        assertNull(trackHit.copy(fileName = null).fileName)
        assertEquals("Ёлка  LIVE.m4a", trackHit.copy(fileName = "Ёлка  LIVE.m4a").fileName)
    }

    @Test
    fun `blank sentinel and untrimmed filenames are rejected on construction and copy`() {
        for (name in listOf("", " \t", "\u00A0", "<unknown>", "<UNKNOWN>", " file.m4a", "file.m4a\n")) {
            assertThrows(IllegalArgumentException::class.java) { LibraryTrackSearchHit(track, name) }
            assertThrows(IllegalArgumentException::class.java) { trackHit.copy(fileName = name) }
        }
    }

    @Test
    fun `filename cannot carry a source path or content URI`() {
        for (name in listOf("Music/file.m4a", "/file.m4a", "content://media/audio/1")) {
            assertThrows(IllegalArgumentException::class.java) { trackHit.copy(fileName = name) }
        }
        // Android filenames can contain backslash; do not impose Windows filename rules.
        assertEquals("part\\one.m4a", trackHit.copy(fileName = "part\\one.m4a").fileName)
    }

    @Test
    fun `album explanations preserve complete membership credit and artwork`() {
        assertSame(album, albumHit.album)
        assertEquals(12, albumHit.album.trackCount)
        assertEquals(AlbumArtistCredit.Various, albumHit.album.artistCredit)
        assertEquals(album.artwork, albumHit.album.artwork)
        assertEquals(emptyList<String>(), albumHit.copy(matchingArtistNames = emptyList()).matchingArtistNames)
        // A missing title may match by a known participant.
        assertNull(albumHit.copy(album = album.copy(title = null)).album.title)
    }

    @Test
    fun `album explanation names must be known and cleaned`() {
        for (name in listOf("", " \t", "\u00A0", "<unknown>", "<UNKNOWN>", " Ёлка", "Ёлка ")) {
            assertThrows(IllegalArgumentException::class.java) { LibraryAlbumSearchHit(album, listOf(name)) }
            assertThrows(IllegalArgumentException::class.java) { albumHit.copy(matchingArtistNames = listOf(name)) }
        }
    }

    @Test
    fun `album names are unique by original text without normalizing display spelling`() {
        assertThrows(IllegalArgumentException::class.java) {
            albumHit.copy(matchingArtistNames = listOf("Ёлка", "Ёлка"))
        }
        val names = listOf("Елка", "Ёлка", "ёлка", "Static  Pilgrim")
        assertEquals(names, albumHit.copy(matchingArtistNames = names).matchingArtistNames)
    }

    @Test
    fun `duplicate track identity is rejected even when metadata differs`() {
        val changed = trackHit.copy(track = track.copy(title = "Changed"), fileName = null)
        assertThrows(IllegalArgumentException::class.java) {
            LibrarySearchResults(listOf(trackHit, changed), emptyList(), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) { results.copy(tracks = listOf(trackHit, changed)) }
    }

    @Test
    fun `duplicate album identity is rejected even with different explanations`() {
        val changed = albumHit.copy(matchingArtistNames = emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            LibrarySearchResults(emptyList(), listOf(albumHit, changed), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) { results.copy(albums = listOf(albumHit, changed)) }
    }

    @Test
    fun `duplicate artist identity is rejected even when metadata differs`() {
        val changed = artist.copy(name = "Changed")
        assertThrows(IllegalArgumentException::class.java) {
            LibrarySearchResults(emptyList(), emptyList(), listOf(artist, changed))
        }
        assertThrows(IllegalArgumentException::class.java) { results.copy(artists = listOf(artist, changed)) }
    }

    @Test
    fun `equal labels across identities and equal opaque IDs across groups are valid`() {
        val expanded = LibrarySearchResults(
            tracks = listOf(trackHit, trackHit.copy(track = track.copy(id = TrackId("card:1")))),
            albums = listOf(albumHit, albumHit.copy(album = album.copy(id = AlbumId("card:1")))),
            artists = listOf(artist, artist.copy(id = ArtistId("card:1"))),
        )
        assertEquals(2, expanded.tracks.size)
        assertEquals(2, expanded.albums.size)
        assertEquals(2, expanded.artists.size)
        assertEquals(12, expanded.artists.first().trackCount)
        assertEquals(3, expanded.artists.first().albumCount)
    }

    @Test
    fun `unknown or unclean artist names cannot be search hits`() {
        for (name in listOf(null, "<unknown>", "<UNKNOWN>", " Ёлка", "Ёлка ")) {
            assertThrows(IllegalArgumentException::class.java) {
                results.copy(artists = listOf(artist.copy(name = name)))
            }
        }
    }
}
