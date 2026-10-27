package com.zaus.nullwave.feature.library.api.model

import com.zaus.nullwave.core.media.model.ArtworkRef
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LibraryCatalogModelsTest {
    private val album = LibraryAlbum(AlbumId("test:album:1"), "Transmission", AlbumArtistCredit.Named("Nullwave"), 3)
    private val artist = LibraryArtist(ArtistId("test:artist:1"), "Nullwave", trackCount = 3, albumCount = 1)

    @Test
    fun `unknown tags keep an identity and do not require invented artwork`() {
        val unknownAlbum = album.copy(title = null, artistCredit = AlbumArtistCredit.Unknown)
        val unknownArtist = artist.copy(name = null, albumCount = 0)
        assertEquals(album.id, unknownAlbum.id)
        assertNull(unknownAlbum.title)
        assertNull(unknownAlbum.artwork)
        assertEquals(AlbumArtistCredit.Unknown, unknownAlbum.artistCredit)
        assertEquals(artist.id, unknownArtist.id)
        assertNull(unknownArtist.name)
        assertEquals(0, unknownArtist.albumCount)
    }

    @Test
    fun `compilation credit remains distinct from missing metadata`() {
        val compilation = album.copy(artistCredit = AlbumArtistCredit.Various)
        val unknown = album.copy(artistCredit = AlbumArtistCredit.Unknown)
        assertNotEquals(compilation, unknown)
        assertEquals(AlbumArtistCredit.Various, compilation.artistCredit)
        assertEquals(album.trackCount, compilation.trackCount)
    }

    @Test
    fun `blank identities and known labels are rejected also through copy`() {
        for (blank in listOf("", " ", "\t\n")) {
            assertThrows(IllegalArgumentException::class.java) { AlbumId(blank) }
            assertThrows(IllegalArgumentException::class.java) { ArtistId(blank) }
            assertThrows(IllegalArgumentException::class.java) { album.copy(title = blank) }
            assertThrows(IllegalArgumentException::class.java) { artist.copy(name = blank) }
            assertThrows(IllegalArgumentException::class.java) { AlbumArtistCredit.Named(blank) }
        }
    }

    @Test
    fun `names and artwork keys never replace entity identity`() {
        val artwork = ArtworkRef("thumbnail:shared", "revision")
        val first = album.copy(id = AlbumId("primary:1"), artwork = artwork)
        val second = first.copy(id = AlbumId("card:1"))
        assertEquals(first.title, second.title)
        assertEquals(first.artwork, second.artwork)
        assertNotEquals(first.id, second.id)
        assertNotEquals(first, second)
        assertNotEquals(artist, artist.copy(id = ArtistId("card:1")))
    }

    @Test
    fun `empty or negative membership cannot masquerade as a catalog entity`() {
        for (count in listOf(Int.MIN_VALUE, -1, 0)) {
            assertThrows(IllegalArgumentException::class.java) { album.copy(trackCount = count) }
            assertThrows(IllegalArgumentException::class.java) { artist.copy(trackCount = count) }
        }
        assertEquals(1, album.copy(trackCount = 1).trackCount)
        assertEquals(Int.MAX_VALUE, album.copy(trackCount = Int.MAX_VALUE).trackCount)
    }

    @Test
    fun `artist album count is bounded by eligible track count and may be zero`() {
        assertEquals(0, artist.copy(albumCount = 0).albumCount)
        assertEquals(3, artist.copy(albumCount = 3).albumCount)
        assertEquals(Int.MAX_VALUE, artist.copy(trackCount = Int.MAX_VALUE, albumCount = Int.MAX_VALUE).albumCount)
        for (count in listOf(Int.MIN_VALUE, -1, 4, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { artist.copy(albumCount = count) }
        }
        assertThrows(IllegalArgumentException::class.java) { artist.copy(trackCount = 1, albumCount = 2) }
    }

    @Test
    fun `queries require explicit locale and preserve independent direction`() {
        val albums = LibraryAlbumQuery("ru-RU")
        val artists = LibraryArtistQuery("en-US", SortDirection.Descending)
        assertEquals(SortDirection.Ascending, albums.direction)
        assertEquals("en-US", artists.localeTag)
        assertEquals(SortDirection.Descending, artists.direction)
        assertNotEquals(albums, albums.copy(localeTag = "en-US"))
        assertNotEquals(albums, albums.copy(direction = SortDirection.Descending))
        assertNotEquals(artists, artists.copy(localeTag = "ru-RU"))
        assertNotEquals(artists, artists.copy(direction = SortDirection.Ascending))
        for (blank in listOf("", " ", "\t\n")) {
            assertThrows(IllegalArgumentException::class.java) { albums.copy(localeTag = blank) }
            assertThrows(IllegalArgumentException::class.java) { artists.copy(localeTag = blank) }
        }
    }
}
