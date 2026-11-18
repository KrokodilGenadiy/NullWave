package com.zaus.nullwave.feature.library.data.repository

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistDetails
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.data.source.FakeMediaStoreAudioSource
import com.zaus.nullwave.feature.library.data.source.audioRow
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaStoreArtistDetailsRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = FakeMediaStoreAudioSource()
    private var waveformSubmissions = 0
    private val repository = MediaStoreLibraryRepository(source, dispatcher) { waveformSubmissions++ }
    private val id = ArtistId("external_primary:4")

    @Test
    fun `compilation summary includes every artist while tracks and identity match the selected artist`() = runTest(dispatcher) {
        val guest = audioRow(1, artist = "Guest").copy(artistId = 7, album = "Compilation", dateModifiedSeconds = 20)
        val selected = listOf(audioRow(2), audioRow(3))
        val sameName = audioRow(4).copy(artistId = 9, albumId = 9)
        source.read = { selected + guest + sameName + audioRow(2, volume = "card") }

        val details = readDetails()
        assertEquals(id, details.artist.id)
        assertEquals(2, details.artist.trackCount)
        assertEquals(1, details.artist.albumCount)
        assertEquals(listOf("external_primary:2", "external_primary:3"), details.tracks.map { it.id.value })
        assertEquals(3, details.albums.single().trackCount)
        assertEquals("Compilation", details.albums.single().title)
        assertEquals(AlbumArtistCredit.Various, details.albums.single().artistCredit)
        assertEquals(1, source.reads)
        assertEquals(0, waveformSubmissions)

        val albums = (repository.observeAlbums(LibraryAlbumQuery("en")).first() as AppResult.Success).value
        val artists = (repository.observeArtists(LibraryArtistQuery("en")).first() as AppResult.Success).value
        val tracks = (repository.observeTracks(LibraryTrackQuery("en")).first() as AppResult.Success).value
        val search = (repository.observeSearch(checkNotNull(LibrarySearchQuery.fromInput("nullwave", "en")))
            .first() as AppResult.Success).value
        assertEquals(albums.single { it.id == AlbumId("external_primary:8") }, details.albums.single())
        assertEquals(artists.single { it.id == id }, details.artist)
        assertEquals(search.artists.single { it.id == id }, details.artist)
        assertEquals(search.albums.single { it.album.id == AlbumId("external_primary:8") }.album, details.albums.single())
        assertEquals(tracks.filter { it.id in details.tracks.map { track -> track.id } }, details.tracks)
    }

    @Test
    fun `another compilation member changes both track and album artwork revisions`() = runTest(dispatcher) {
        val selected = audioRow(2)
        var guest = audioRow(1, artist = "Guest").copy(artistId = 7, dateModifiedSeconds = 20)
        source.read = { listOf(selected, guest) }
        val results = mutableListOf<AppResult<LibraryArtistDetails, DataError>>()
        backgroundScope.launch { repository.observeArtistDetails(id, "en").toList(results) }
        runCurrent()
        val first = (results.single() as AppResult.Success).value

        guest = guest.copy(dateModifiedSeconds = 30)
        source.invalidate()
        runCurrent()
        val updated = (results.last() as AppResult.Success).value
        assertNotEquals(first.albums.single().artwork, updated.albums.single().artwork)
        assertNotEquals(first.tracks.single().artwork, updated.tracks.single().artwork)
        assertEquals(updated.albums.single().artwork, updated.tracks.single().artwork)
        assertEquals(first.tracks.single().id, updated.tracks.single().id)
        assertEquals(1, source.registrations)
        assertEquals(0, waveformSubmissions)
    }

    @Test
    fun `Download card keeps all nine tracks and shares catalog identity across two provider albums`() = runTest(dispatcher) {
        val download = audioRow().copy(album = "Download", relativePath = "Download/")
        val nine = (1L..9L).map {
            download.copy(id = it, albumId = if (it == 1L) 37 else 52, artistId = if (it == 4L || it == 8L) 4 else 7)
        }
        val ordinary = download.copy(id = 10, albumId = 37, relativePath = "Music/", dateModifiedSeconds = 40)
        source.read = { nine.reversed() + ordinary + download.copy(volume = "card") }
        val details = readDetails()

        assertEquals(3, details.artist.trackCount)
        assertEquals(2, details.artist.albumCount)
        assertEquals(listOf("external_primary:10", "external_primary:4", "external_primary:8"), details.tracks.map { it.id.value })
        val grouped = details.albums.single { it.id == AlbumId("external_primary:folder/Download") }
        assertEquals(9, grouped.trackCount)
        val albums = (repository.observeAlbums(LibraryAlbumQuery("en")).first() as AppResult.Success).value
        val search = (repository.observeSearch(checkNotNull(LibrarySearchQuery.fromInput("download", "en")))
            .first() as AppResult.Success).value
        assertEquals(albums.filter { it.id in details.albums.map { album -> album.id } }, details.albums)
        assertEquals(search.albums.single { it.album.id == grouped.id }.album, grouped)
        val tracks = (repository.observeTracks(LibraryTrackQuery("en")).first() as AppResult.Success).value
        assertEquals(tracks.filter { it.id in details.tracks.map { track -> track.id } }, details.tracks)

        source.read = { nine.drop(1) + ordinary }
        val afterRemoval = readDetails()
        assertEquals(8, afterRemoval.albums.single { it.id == grouped.id }.trackCount)
        assertEquals(details.tracks.map { it.id }, afterRemoval.tracks.map { it.id })
        assertEquals(details.tracks.drop(1), afterRemoval.tracks.drop(1))
        assertNotEquals(details.tracks.first().artwork, afterRemoval.tracks.first().artwork)
    }

    @Test
    fun `tracks with absent or invalid album identities still form an artist with no albums`() = runTest(dispatcher) {
        val first = audioRow(1, title = null, artist = "<unknown>").copy(albumId = null, album = null, displayName = "Memo.mp3")
        val second = audioRow(2, artist = null).copy(albumId = -1, album = "Tag without identity")
        source.read = { listOf(first, second) }

        val details = readDetails()
        assertEquals(null, details.artist.name)
        assertEquals(2, details.artist.trackCount)
        assertEquals(0, details.artist.albumCount)
        assertTrue(details.albums.isEmpty())
        assertEquals(listOf("Low Orbit", "Memo.mp3"), details.tracks.map { it.title })
        assertTrue(details.tracks.all { it.artwork == null })
    }

    @Test
    fun `sorting uses screen locale fallback titles and ordinal identities with unknown albums last`() = runTest(dispatcher) {
        val rows = listOf(
            audioRow(9, title = "Ä").copy(albumId = 9, album = "Ä", trackNumber = 1),
            audioRow(10, title = "Ä").copy(albumId = 10, album = "Ä", trackNumber = 2002),
            audioRow(8, title = "Z").copy(albumId = 8, album = "Z"),
            audioRow(1, title = "B").copy(albumId = 1, album = "B"),
            audioRow(2, title = "<unknown>").copy(albumId = 2, album = "<unknown>", displayName = "Ä"),
        )
        source.read = { rows }
        val german = readDetails("de")
        val swedish = readDetails("sv")
        assertEquals(listOf(10, 9, 1, 8, 2).map { "external_primary:$it" }, german.albums.map { it.id.value })
        assertEquals(listOf(1, 8, 10, 9, 2).map { "external_primary:$it" }, swedish.albums.map { it.id.value })
        assertEquals(listOf(10, 2, 9, 1, 8).map { "external_primary:$it" }, german.tracks.map { it.id.value })
        assertEquals(listOf(1, 8, 10, 2, 9).map { "external_primary:$it" }, swedish.tracks.map { it.id.value })
        assertEquals(null, german.albums.last().title)
        assertEquals((repository.observeTracks(LibraryTrackQuery("de")).first() as AppResult.Success).value, german.tracks)
        assertEquals((repository.observeTracks(LibraryTrackQuery("sv")).first() as AppResult.Success).value, swedish.tracks)
        source.read = { rows.reversed() }
        assertEquals(german, readDetails("de"))
        assertEquals(swedish, readDetails("sv"))
    }

    @Test
    fun `artist disappears and returns on the same subscription while another volume remains`() = runTest(dispatcher) {
        val track = audioRow()
        val otherVolume = track.copy(volume = "card")
        source.read = { listOf(track, otherVolume) }
        val results = mutableListOf<AppResult<LibraryArtistDetails, DataError>>()
        val collector = backgroundScope.launch { repository.observeArtistDetails(id, "en").toList(results) }
        runCurrent()
        val first = results.single()
        source.read = { listOf(otherVolume) }
        source.invalidate()
        runCurrent()
        assertEquals(AppResult.Failure(DataError.NotFound), results.last())
        source.read = { listOf(track) }
        source.invalidate()
        runCurrent()
        assertEquals(listOf(first, AppResult.Failure(DataError.NotFound), first), results)
        assertEquals(1, source.registrations)
        collector.cancel()
        runCurrent()
        assertEquals(1, source.closes)
    }

    @Test
    fun `denied access completes a cold flow before registration and a fresh collection can recover`() = runTest(dispatcher) {
        source.granted = false
        val flow = repository.observeArtistDetails(id, "en")
        assertEquals(0, source.registrations)
        assertEquals(listOf(AppResult.Failure(DataError.AccessDenied)), flow.toList())
        assertEquals(0, source.registrations)
        assertEquals(0, source.reads)
        source.granted = true
        source.read = { listOf(audioRow()) }
        assertTrue(flow.first() is AppResult.Success)
        assertEquals(1, source.closes)
        assertEquals(0, waveformSubmissions)
    }

    @Test
    fun `revocation during reading wins over both content and absence and terminates observation`() = runTest(dispatcher) {
        for (rows in listOf(listOf(audioRow()), emptyList())) {
            source.granted = true
            source.read = { listOf(audioRow()) }
            val results = mutableListOf<AppResult<LibraryArtistDetails, DataError>>()
            val collector = backgroundScope.launch { repository.observeArtistDetails(id, "en").toList(results) }
            runCurrent()
            assertTrue(results.single() is AppResult.Success)
            source.read = { source.granted = false; rows }
            source.invalidate()
            runCurrent()
            assertEquals(2, results.size)
            assertEquals(AppResult.Failure(DataError.AccessDenied), results.last())
            assertTrue(collector.isCompleted)
            assertEquals(0, source.activeObservers)
            source.invalidate()
            runCurrent()
            assertEquals(2, results.size)
        }
    }

    @Test
    fun `revocation before reading closes the observer without reading protected rows again`() = runTest(dispatcher) {
        source.read = { listOf(audioRow()) }
        val results = mutableListOf<AppResult<LibraryArtistDetails, DataError>>()
        val collector = backgroundScope.launch { repository.observeArtistDetails(id, "en").toList(results) }
        runCurrent()
        source.granted = false
        source.invalidate()
        runCurrent()
        assertEquals(AppResult.Failure(DataError.AccessDenied), results.last())
        assertEquals(1, source.reads)
        assertEquals(1, source.closes)
        assertTrue(collector.isCompleted)
    }

    @Test
    fun `revocation racing with IO during registration and reading preserves denial diagnostics`() = runTest(dispatcher) {
        val problem = IOException("Media unavailable")
        source.beforeRegister = { source.granted = false }
        source.registrationFailure = problem
        suspend fun verifyDenied() {
            val failure = repository.observeArtistDetails(id, "en").toList().single() as AppResult.Failure
            assertEquals(DataError.AccessDenied, failure.error)
            assertSame(problem, failure.cause)
            assertEquals(0, source.activeObservers)
        }
        verifyDenied()
        assertEquals(0, source.reads)
        source.granted = true
        source.beforeRegister = null
        source.registrationFailure = null
        source.read = { source.granted = false; throw problem }
        verifyDenied()
        assertEquals(1, source.closes)
        source.granted = true
        source.read = { listOf(audioRow()) }
        assertEquals(id, readDetails().artist.id)
        assertEquals(2, source.closes)
    }

    @Test
    fun `read IO keeps observation live and registration IO can be retried`() = runTest(dispatcher) {
        val problem = IOException("Storage unavailable")
        source.registrationFailure = problem
        val registration = repository.observeArtistDetails(id, "en").toList().single() as AppResult.Failure
        assertEquals(DataError.Io, registration.error)
        assertSame(problem, registration.cause)
        assertEquals(0, source.reads)
        source.registrationFailure = null
        source.read = { throw problem }
        val results = mutableListOf<AppResult<LibraryArtistDetails, DataError>>()
        backgroundScope.launch { repository.observeArtistDetails(id, "en").toList(results) }
        runCurrent()
        val reading = results.single() as AppResult.Failure
        assertEquals(DataError.Io, reading.error)
        assertSame(problem, reading.cause)
        source.read = { listOf(audioRow()) }
        source.invalidate()
        runCurrent()
        assertTrue(results.last() is AppResult.Success)
        assertEquals(1, source.registrations)
    }

    @Test
    fun `duplicate rows in a compilation are recoverable IO even for another artist`() = runTest(dispatcher) {
        val guest = audioRow(2).copy(artistId = 7)
        val results = mutableListOf<AppResult<LibraryArtistDetails, DataError>>()
        source.read = { listOf(audioRow(), guest, guest.copy(title = "Changed")) }
        backgroundScope.launch { repository.observeArtistDetails(id, "en").toList(results) }
        runCurrent()
        assertEquals(DataError.Io, (results.single() as AppResult.Failure).error)
        source.read = { listOf(audioRow(), guest) }
        source.invalidate()
        runCurrent()
        assertEquals(2, (results.last() as AppResult.Success).value.albums.single().trackCount)
        assertEquals(1, source.registrations)
    }

    @Test
    fun `cancellation during reading releases observation without publishing a failure`() = runTest(dispatcher) {
        var cancelled = false
        source.read = {
            try { awaitCancellation() } finally { cancelled = true }
        }
        val results = mutableListOf<AppResult<LibraryArtistDetails, DataError>>()
        val collector = backgroundScope.launch { repository.observeArtistDetails(id, "en").toList(results) }
        runCurrent()
        collector.cancel()
        runCurrent()
        assertTrue(cancelled)
        assertTrue(results.isEmpty())
        assertEquals(1, source.closes)
        assertEquals(0, waveformSubmissions)
    }

    @Test
    fun `changes during initial reading trigger one follow up read with fresh counts`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        source.read = {
            val rows = if (source.reads == 1) listOf(audioRow()) else listOf(audioRow(), audioRow(2))
            if (source.reads == 1) gate.await()
            rows
        }
        val results = mutableListOf<AppResult<LibraryArtistDetails, DataError>>()
        backgroundScope.launch { repository.observeArtistDetails(id, "en").toList(results) }
        runCurrent()
        repeat(20) { source.invalidate() }
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2), results.map { (it as AppResult.Success).value.artist.trackCount })
        assertEquals(2, source.reads)
        assertEquals(1, source.registrations)
        assertEquals(0, waveformSubmissions)
    }

    @Test
    fun `blank sorting locale is rejected before observation`() {
        assertThrows(IllegalArgumentException::class.java) { repository.observeArtistDetails(id, " ") }
        assertEquals(0, source.registrations)
        assertEquals(0, source.reads)
    }

    private suspend fun readDetails(localeTag: String = "en"): LibraryArtistDetails =
        (repository.observeArtistDetails(id, localeTag).first() as AppResult.Success).value
}
