package com.zaus.nullwave.feature.library.data.search

import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.data.mapping.toAlbums
import com.zaus.nullwave.feature.library.data.mapping.toArtists
import com.zaus.nullwave.feature.library.data.mapping.toTracks
import com.zaus.nullwave.feature.library.data.source.audioRow
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreSearchMappingTest {
    @Test
    fun `tracks require every token but allow distribution across separate fields`() = runTest {
        val rows = listOf(
            audioRow(1, title = "Moon", artist = "Light").copy(album = "Orbit", displayName = "take7.m4a"),
            audioRow(2, title = "Moon", artist = "Other").copy(album = "Orbit", displayName = "take7.m4a"),
        )
        assertEquals(listOf("external_primary:1"), rows.search("MOON light orbit take7").tracks.map { it.track.id.value })
        assertTrue(rows.search("moonlight").tracks.isEmpty())
        assertTrue(rows.search("moon absent").tracks.isEmpty())
        assertEquals(2, rows.search("moon moon").tracks.size)
    }

    @Test
    fun `file name remains searchable with a title and does not make an album or artist match`() = runTest {
        val result = listOf(audioRow().copy(displayName = "  Session_07.M4A  ")).search("session_07.m4a")
        assertEquals("Low Orbit", result.tracks.single().track.title)
        assertEquals("Session_07.M4A", result.tracks.single().fileName)
        assertTrue(result.albums.isEmpty())
        assertTrue(result.artists.isEmpty())
    }

    @Test
    fun `normalization is shared by all three groups and preserves display text`() = runTest {
        val original = "  Ёлка\tLIVE  "
        val result = listOf(audioRow(title = original, artist = original).copy(album = original)).search("ЕЛКА live", "tr")
        assertEquals(original.trim(), result.tracks.single().track.title)
        assertEquals(original.trim(), result.albums.single().album.title)
        assertEquals(original.trim(), result.artists.single().name)
        assertTrue(result.albums.single().matchingArtistNames.isEmpty())
    }

    @Test
    fun `punctuation is literal and accent stripping transliteration and fuzzy matching are absent`() = runTest {
        val rows = listOf(audioRow(title = "50%_mix* - 'live' 🎵 Beyoncé Straße Ёлка"))
        for (query in listOf("%", "_", "*", "-", "'live'", "🎵", "Beyoncé", "Straße", "елка")) {
            assertEquals(query, 1, rows.search(query).tracks.size)
        }
        for (query in listOf("Beyonce", "strasse", "elka", "lve", "50.*", "mix%")) {
            assertTrue(query, rows.search(query).tracks.isEmpty())
        }
    }

    @Test
    fun `tracks rank exact then phrase prefix then primary tokens then supporting fields`() = runTest {
        val rows = listOf(
            audioRow(4, title = "A Moon", artist = "Light"),
            audioRow(3, title = "Light over Moon"),
            audioRow(2, title = "Moon Light Live"),
            audioRow(1, title = "Moon Light"),
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), rows.search("moon light").tracks.map { it.track.id.value.substringAfter(':').toLong() })
        assertEquals(listOf(3L, 1L, 2L, 4L), rows.search("light moon").tracks.map { it.track.id.value.substringAfter(':').toLong() })
    }

    @Test
    fun `albums rank primary matches before supporting matches and unknown names sort last`() = runTest {
        val titles = listOf("Moon Light", "Moon Light Live", "Light over Moon", "A Moon", null)
        val rows = titles.mapIndexed { index, title ->
            audioRow(index.toLong(), artist = "Moon Light").copy(albumId = index.toLong(), album = title)
        }
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L), rows.reversed().search("moon light").albums.map { it.album.id.value.substringAfter(':').toLong() })
        assertTrue(rows.search("moon light").albums.take(3).all { it.matchingArtistNames.isEmpty() })
        assertEquals(listOf("Moon Light"), rows.search("moon light").albums.last().matchingArtistNames)
    }

    @Test
    fun `artists rank only their canonical primary name`() = runTest {
        val names = listOf("Moon Light", "Moon Light Live", "Light over Moon", "A Moon", null)
        val rows = names.mapIndexed { index, name ->
            audioRow(index.toLong(), title = "Moon Light", artist = name).copy(artistId = index.toLong())
        }
        assertEquals(listOf("Moon Light", "Moon Light Live", "Light over Moon"), rows.reversed().search("moon light").artists.map { it.name })
    }

    @Test
    fun `locale sorts equal ranks and ordinal IDs break label ties independent of cursor order`() = runTest {
        val rows = listOf(
            audioRow(2, title = "Å song", artist = "Å song").copy(album = "Å song", albumId = 2, artistId = 2),
            audioRow(10, title = "Z song", artist = "Z song").copy(album = "Z song", albumId = 10, artistId = 10),
            audioRow(1, title = "Z song", artist = "Z song").copy(album = "Z song", albumId = 1, artistId = 1),
        )
        for ((locale, ids) in listOf("en" to listOf(2, 1, 10), "sv" to listOf(1, 10, 2))) {
            val result = rows.search("song", locale)
            val expected = ids.map { "external_primary:$it" }
            assertEquals(expected, result.tracks.map { it.track.id.value })
            assertEquals(expected, result.albums.map { it.album.id.value })
            assertEquals(expected, result.artists.map { it.id.value })
            assertEquals(result, rows.reversed().search("song", locale))
        }
    }

    @Test
    fun `album tokens must be covered by title and one participant not several participants`() = runTest {
        val rows = listOf(audioRow(1, artist = "Alpha"), audioRow(2, artist = "Beta").copy(artistId = 5))
        assertTrue(rows.search("alpha beta").albums.isEmpty())
        val hit = rows.search("signals beta").albums.single()
        assertEquals(listOf("Beta"), hit.matchingArtistNames)
        assertEquals(2, hit.album.trackCount)
        assertEquals(AlbumArtistCredit.Various, hit.album.artistCredit)
        assertTrue(rows.search("signals").albums.single().matchingArtistNames.isEmpty())
    }

    @Test
    fun `album explanation retains every qualifying original name uniquely in locale order`() = runTest {
        val names = listOf("Guest Åsa", "Guest Zed", " Guest Åsa ", "unrelated", "guest zed")
        val rows = names.mapIndexed { index, name -> audioRow(index.toLong(), artist = name).copy(artistId = null) }
        assertEquals(listOf("Guest Åsa", "guest zed", "Guest Zed"), rows.search("guest", "en").albums.single().matchingArtistNames)
        assertEquals(listOf("guest zed", "Guest Zed", "Guest Åsa"), rows.search("guest", "sv").albums.single().matchingArtistNames)
        assertTrue(rows.search("guest").artists.isEmpty())
        assertEquals(AlbumArtistCredit.Unknown, rows.search("guest").albums.single().album.artistCredit)
    }

    @Test
    fun `participant deduplication keeps distinct original spellings and complete membership`() = runTest {
        val names = listOf(" Ёлка ", "Ёлка", "Елка", "ёлка", "Other", " Other ")
        val rows = names.mapIndexed { index, name -> audioRow(index.toLong(), artist = name) }
        val hit = rows.search("елка", "ru").albums.single()
        assertEquals(setOf("Ёлка", "Елка", "ёлка"), hit.matchingArtistNames.toSet())
        assertEquals(3, hit.matchingArtistNames.size)
        assertEquals(names.size, hit.album.trackCount)
        assertEquals(hit, rows.reversed().search("елка", "ru").albums.single())
    }

    @Test
    fun `conflicting participant tags can explain albums but do not rename canonical artists or albums`() = runTest {
        val rows = listOf(audioRow(1, artist = "Alpha"), audioRow(2, artist = "Beta").copy(album = "Alternate"))
        assertEquals(listOf("Beta"), rows.search("beta").albums.single().matchingArtistNames)
        assertTrue(rows.search("beta").artists.isEmpty())
        assertTrue(rows.search("alternate").albums.isEmpty())
        assertEquals(rows.search("beta"), rows.reversed().search("beta"))
    }

    @Test
    fun `search models counts and artwork equal catalog models from complete membership`() = runTest {
        val rows = listOf(
            audioRow(1, title = "Needle", artist = "Needle").copy(album = "Needle", sizeBytes = 100),
            audioRow(2, title = "Other", artist = "Else").copy(album = "Needle", artistId = 5, sizeBytes = 200),
            audioRow(3, title = "Third", artist = "Needle").copy(albumId = 9, album = "Other"),
        )
        val result = rows.search("needle")
        val tracks = rows.toTracks(LibraryTrackQuery("en")).associateBy { it.id }
        val albums = rows.toAlbums(LibraryAlbumQuery("en")).associateBy { it.id }
        val artists = rows.toArtists(LibraryArtistQuery("en")).associateBy { it.id }
        result.tracks.forEach { assertEquals(tracks[it.track.id], it.track) }
        result.albums.forEach { assertEquals(albums[it.album.id], it.album) }
        result.artists.forEach { assertEquals(artists[it.id], it) }
        assertEquals(2, result.artists.single().trackCount)
        assertEquals(2, result.artists.single().albumCount)
        assertEquals(2, result.albums.first().album.trackCount)
        assertEquals(result.albums.first().album.artwork, result.tracks.first().track.artwork)
    }

    @Test
    fun `equal IDs across volumes and equal names within a volume remain separate`() = runTest {
        val rows = listOf(
            audioRow(1, title = "Needle", artist = "Needle").copy(album = "Needle"),
            audioRow(1, volume = "card", title = "Needle", artist = "Needle").copy(album = "Needle"),
            audioRow(2, title = "Needle", artist = "Needle").copy(album = "Needle", albumId = 9, artistId = 5),
        )
        val result = rows.search("needle")
        assertEquals(3, result.tracks.size)
        assertEquals(3, result.albums.size)
        assertEquals(3, result.artists.size)
    }

    @Test
    fun `unknown metadata is not indexed and absent membership does not invent groups`() = runTest {
        val rows = listOf(audioRow(title = "Known", artist = " <UNKNOWN> ").copy(album = " ", displayName = null))
        for (query in listOf("unknown", "various", "неизвестный")) {
            assertEquals(emptyList<Any>(), rows.search(query).tracks)
            assertTrue(rows.search(query).albums.isEmpty())
            assertTrue(rows.search(query).artists.isEmpty())
        }
        assertNull(rows.search("known").tracks.single().fileName)
        val noMembership = listOf(audioRow(artist = "Known").copy(album = "Known", albumId = -1, artistId = null)).search("known")
        assertEquals(1, noMembership.tracks.size)
        assertTrue(noMembership.albums.isEmpty())
        assertTrue(noMembership.artists.isEmpty())
    }

    @Test
    fun `unknown album may match a known participant and fallback file title is searchable`() = runTest {
        val result = listOf(audioRow(title = "<unknown>", artist = "Known").copy(album = null, displayName = "Memo.m4a")).search("known")
        assertNull(result.albums.single().album.title)
        assertEquals(listOf("Known"), result.albums.single().matchingArtistNames)
        assertEquals("Memo.m4a", result.tracks.single().track.title)
        assertEquals(1, listOf(audioRow(title = null).copy(displayName = "Memo.m4a")).search("memo").tracks.size)
    }

    @Test
    fun `no matches and empty catalog return three empty groups`() = runTest {
        val empty = emptyList<MediaStoreAudioRow>().search("needle")
        assertTrue(empty.tracks.isEmpty() && empty.albums.isEmpty() && empty.artists.isEmpty())
        assertEquals(empty, listOf(audioRow()).search("needle"))
    }

    @Test
    fun `provider corruption fails even if the bad row would not match`() = runTest {
        val valid = audioRow(title = "Needle")
        for (bad in listOf(
            audioRow(2).copy(title = null, displayName = null),
            audioRow(-1), audioRow(2, volume = null),
            audioRow(2).copy(displayName = "folder/track.mp3"),
            valid.copy(title = "unrelated"),
        )) {
            try {
                listOf(valid, bad).search("needle")
                error("Expected an I/O failure for $bad")
            } catch (_: IOException) { /* Typed by the repository, never a constructor failure. */ }
        }
    }

    @Test
    fun `results are complete without a hidden hit cap`() = runTest {
        val rows = (1L..80L).map { audioRow(it, title = "Needle", artist = "Needle").copy(album = "Needle", albumId = it, artistId = it) }
        val result = rows.search("needle")
        assertEquals(80, result.tracks.size)
        assertEquals(80, result.albums.size)
        assertEquals(80, result.artists.size)
    }

    @Test
    fun `cancellation during snapshot traversal stops mapping without a result`() = runTest {
        val job = Job(currentCoroutineContext()[Job])
        var reads = 0
        val rows = object : AbstractList<MediaStoreAudioRow>() {
            override val size = 100
            override fun get(index: Int): MediaStoreAudioRow {
                if (++reads == 5) job.cancel()
                return audioRow(index.toLong())
            }
        }
        try {
            withContext(job) { rows.search("orbit") }
            error("Expected cancellation")
        } catch (_: CancellationException) { }
        assertTrue("Mapping continued after cancellation: $reads", reads < rows.size)
    }

    private suspend fun List<MediaStoreAudioRow>.search(text: String, locale: String = "en") =
        toSearchResults(checkNotNull(LibrarySearchQuery.fromInput(text, locale)))
}
