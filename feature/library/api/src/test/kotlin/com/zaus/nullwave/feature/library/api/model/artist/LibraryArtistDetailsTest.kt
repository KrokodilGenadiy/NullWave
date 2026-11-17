package com.zaus.nullwave.feature.library.api.model.artist

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryArtistDetailsTest {
    private val artist = LibraryArtist(ArtistId("primary:5"), "Nullwave", trackCount = 3, albumCount = 2)
    private val albumZ = LibraryAlbum(AlbumId("primary:20"), "Zenith", AlbumArtistCredit.Named("Nullwave"), trackCount = 2)
    private val albumA = LibraryAlbum(AlbumId("primary:10"), "Afterglow", AlbumArtistCredit.Named("Nullwave"), trackCount = 1)
    private val trackC = MediaTrack(TrackId("primary:300"), "Carrier", "Nullwave", "Zenith", 180_000)
    private val trackA = MediaTrack(TrackId("primary:100"), "Afterimage", "Nullwave", "Afterglow", 200_000)
    private val trackB = MediaTrack(TrackId("primary:200"), "Backbeat", "Nullwave", "Zenith", 190_000)

    @Test
    fun `preserves the supplied order of albums and tracks`() {
        val details = LibraryArtistDetails.create(artist, listOf(albumZ, albumA), listOf(trackC, trackA, trackB))

        assertEquals(artist, details.artist)
        assertEquals(listOf(albumZ, albumA), details.albums)
        assertEquals(listOf(trackC, trackA, trackB), details.tracks)
    }

    @Test
    fun `snapshots ignore later mutation of the input lists`() {
        val albums = mutableListOf(albumZ, albumA)
        val tracks = mutableListOf(trackC, trackA, trackB)
        val details = LibraryArtistDetails.create(artist, albums, tracks)

        albums.reverse()
        albums.add(albumA.copy(id = AlbumId("primary:30")))
        tracks.clear()

        assertEquals(listOf(albumZ, albumA), details.albums)
        assertEquals(listOf(trackC, trackA, trackB), details.tracks)
    }

    @Test
    fun `published lists cannot be modified`() {
        val details = LibraryArtistDetails.create(artist, listOf(albumZ, albumA), listOf(trackC, trackA, trackB))

        assertThrows(UnsupportedOperationException::class.java) {
            (details.albums as MutableList<LibraryAlbum>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (details.tracks as MutableList<MediaTrack>).add(trackA.copy(id = TrackId("primary:400")))
        }
        assertEquals(listOf(albumZ, albumA), details.albums)
        assertEquals(listOf(trackC, trackA, trackB), details.tracks)
    }

    @Test
    fun `rejects albums beyond or below the artist album count`() {
        val tracks = listOf(trackC, trackA, trackB)
        val extra = albumA.copy(id = AlbumId("primary:30"), title = "Extra")

        assertRejected("Album list must match the artist's album count.") {
            LibraryArtistDetails.create(artist, listOf(albumZ, albumA, extra), tracks)
        }
        assertRejected("Album list must match the artist's album count.") {
            LibraryArtistDetails.create(artist, listOf(albumZ), tracks)
        }
    }

    @Test
    fun `rejects tracks beyond or below the artist track count`() {
        val albums = listOf(albumZ, albumA)
        val extra = trackA.copy(id = TrackId("primary:400"), title = "Extra")

        assertRejected("Track list must match the artist's track count.") {
            LibraryArtistDetails.create(artist, albums, listOf(trackC, trackA, trackB, extra))
        }
        assertRejected("Track list must match the artist's track count.") {
            LibraryArtistDetails.create(artist, albums, listOf(trackC, trackA))
        }
    }

    @Test
    fun `rejects a repeated album identity even with differing metadata`() {
        val tracks = listOf(trackC, trackA, trackB)
        val retitled = albumZ.copy(title = "Zenith (Deluxe)", artistCredit = AlbumArtistCredit.Various, trackCount = 5)

        assertRejected("An artist must contain each album ID only once.") {
            LibraryArtistDetails.create(artist, listOf(albumZ, albumZ), tracks)
        }
        assertRejected("An artist must contain each album ID only once.") {
            LibraryArtistDetails.create(artist, listOf(albumZ, retitled), tracks)
        }
    }

    @Test
    fun `rejects a repeated track identity even with differing metadata`() {
        val albums = listOf(albumZ, albumA)
        val retitled = trackC.copy(title = "Carrier (Edit)", album = null, durationMs = 90_000)

        assertRejected("An artist must contain each track ID only once.") {
            LibraryArtistDetails.create(artist, albums, listOf(trackC, trackA, trackC))
        }
        assertRejected("An artist must contain each track ID only once.") {
            LibraryArtistDetails.create(artist, albums, listOf(trackC, trackA, retitled))
        }
    }

    @Test
    fun `accepts equal titles under distinct identities`() {
        val sameTitleAlbum = albumZ.copy(id = AlbumId("card:20"))
        val sameTitleTracks = listOf(trackC, trackC.copy(id = TrackId("card:300")), trackC.copy(id = TrackId("primary:301")))

        val details = LibraryArtistDetails.create(artist, listOf(albumZ, sameTitleAlbum), sameTitleTracks)

        assertEquals(listOf(AlbumId("primary:20"), AlbumId("card:20")), details.albums.map { it.id })
        assertEquals(
            listOf(TrackId("primary:300"), TrackId("card:300"), TrackId("primary:301")),
            details.tracks.map { it.id },
        )
    }

    @Test
    fun `accepts an artist whose tracks have no album`() {
        val looseArtist = LibraryArtist(ArtistId("primary:6"), null, trackCount = 2, albumCount = 0)
        val loose = listOf(
            MediaTrack(TrackId("primary:500"), "voice_memo_01", null, null, 30_000),
            MediaTrack(TrackId("primary:501"), "voice_memo_02", null, null, null),
        )

        val details = LibraryArtistDetails.create(looseArtist, emptyList(), loose)

        assertTrue(details.albums.isEmpty())
        assertEquals(loose, details.tracks)
    }

    @Test
    fun `accepts a compilation whose full track count exceeds the artist's tracks`() {
        val guest = LibraryArtist(ArtistId("primary:7"), "Guest", trackCount = 2, albumCount = 1)
        val compilation = LibraryAlbum(AlbumId("primary:40"), "Night Drive", AlbumArtistCredit.Various, trackCount = 12)
        val guestTracks = listOf(
            MediaTrack(TrackId("primary:600"), "Lights", "Guest", "Night Drive", 210_000),
            MediaTrack(TrackId("primary:601"), "Tunnel", "Guest", "Night Drive", 220_000),
        )

        val details = LibraryArtistDetails.create(guest, listOf(compilation), guestTracks)

        assertEquals(12, details.albums.single().trackCount)
        assertEquals(guestTracks, details.tracks)
    }

    private fun assertRejected(message: String, block: () -> Unit) {
        val error = assertThrows(IllegalArgumentException::class.java) { block() }
        assertEquals(message, error.message)
    }
}
