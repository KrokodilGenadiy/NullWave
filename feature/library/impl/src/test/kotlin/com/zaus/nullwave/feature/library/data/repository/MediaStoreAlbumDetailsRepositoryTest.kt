package com.zaus.nullwave.feature.library.data.repository

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumDetails
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.data.source.FakeMediaStoreAudioSource
import com.zaus.nullwave.feature.library.data.source.audioRow
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaStoreAlbumDetailsRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = FakeMediaStoreAudioSource()
    private var waveformSubmissions = 0
    private val repository = MediaStoreLibraryRepository(source, dispatcher) { waveformSubmissions++ }
    private val id = AlbumId("external_primary:8")

    @Test
    fun `opening the Download card returns all nine members and their catalog artwork`() = runTest(dispatcher) {
        val download = audioRow().copy(album = "Download", relativePath = "Download/")
        val nine = (1L..9L).map { download.copy(id = it, albumId = if (it == 1L) 37 else 52) }
        val regular = download.copy(id = 10, albumId = 37, relativePath = "Music/")
        source.read = { nine.reversed() + regular + download.copy(volume = "card") }
        val albums = (repository.observeAlbums(LibraryAlbumQuery("en")).first() as AppResult.Success).value
        val tracks = (repository.observeTracks(LibraryTrackQuery("en")).first() as AppResult.Success).value
        val card = albums.single { it.trackCount == 9 }
        val expectedIds = nine.map { "external_primary:${it.id}" }
        val details = (repository.observeAlbumDetails(card.id, "en").first() as AppResult.Success).value
        assertEquals(card, details.album)
        assertEquals(expectedIds, details.tracks.map { it.id.value })
        assertEquals(tracks.filter { it.id.value in expectedIds }, details.tracks)
        val ordinaryCard = albums.single { it.id == AlbumId("external_primary:37") }
        val ordinary = (repository.observeAlbumDetails(ordinaryCard.id, "en").first() as AppResult.Success).value
        assertEquals(ordinaryCard, ordinary.album)
        assertEquals(listOf(tracks.single { it.id.value == "external_primary:10" }), ordinary.tracks)
        source.read = { nine.drop(1) + regular }
        val remaining = (repository.observeAlbumDetails(card.id, "en").first() as AppResult.Success).value
        assertEquals(card.id, remaining.album.id)
        assertEquals(expectedIds.drop(1), remaining.tracks.map { it.id.value })
        assertEquals(2, waveformSubmissions)
    }

    @Test
    fun `disc and track sort numerically then localized display title and ordinal track ID`() = runTest(dispatcher) {
        fun row(id: Long, number: Long?, title: String?) = audioRow(id, title = title).copy(trackNumber = number)
        val rows = listOf(
            row(4, 2000, "A"), row(5, 2001, "Z"), row(6, 1000, "A"), row(7, 1010, "A"),
            row(8, 2, "Z"), row(9, 1002, "Ä"), row(10, 2, "Ä"), row(3, 0, "Z"),
            row(2, -2, null).copy(displayName = "Ä"), row(1, null, "B"),
        )
        source.read = { rows }
        suspend fun orderedIds(locale: String) =
            (repository.observeAlbumDetails(id, locale).first() as AppResult.Success).value.tracks.map { it.id.value }
        val german = listOf(10, 9, 8, 7, 6, 5, 4, 2, 1, 3).map { "external_primary:$it" }
        val swedish = listOf(8, 10, 9, 7, 6, 5, 4, 1, 3, 2).map { "external_primary:$it" }
        assertEquals(german, orderedIds("de"))
        assertEquals(swedish, orderedIds("sv"))
        source.read = { rows.reversed() }
        assertEquals(german, orderedIds("de"))
    }

    @Test
    fun `the last member can disappear and return on the same subscription`() = runTest(dispatcher) {
        val track = audioRow()
        val otherVolume = track.copy(volume = "card")
        source.read = { listOf(track, otherVolume) }
        val results = mutableListOf<AppResult<LibraryAlbumDetails, DataError>>()
        val collector = backgroundScope.launch { repository.observeAlbumDetails(id, "en").toList(results) }
        runCurrent()
        val first = (results.single() as AppResult.Success).value
        source.read = { listOf(otherVolume) }
        source.invalidate()
        runCurrent()
        assertEquals(AppResult.Failure(DataError.NotFound), results.last())
        source.read = { listOf(track) }
        source.invalidate()
        runCurrent()
        assertEquals(listOf(AppResult.Success(first), AppResult.Failure(DataError.NotFound), AppResult.Success(first)), results)
        assertEquals(1, source.registrations)
        collector.cancel()
        runCurrent()
        assertEquals(1, source.closes)
    }

    @Test
    fun `revocation during reading wins over both content and absence and closes the observer`() = runTest(dispatcher) {
        for (rows in listOf(listOf(audioRow()), emptyList())) {
            source.granted = true
            source.read = { listOf(audioRow()) }
            val results = mutableListOf<AppResult<LibraryAlbumDetails, DataError>>()
            val collector = backgroundScope.launch { repository.observeAlbumDetails(id, "en").toList(results) }
            runCurrent()
            assertTrue(results.single() is AppResult.Success)
            source.read = { source.granted = false; rows }
            source.invalidate()
            runCurrent()
            assertEquals(2, results.size)
            assertEquals(AppResult.Failure(DataError.AccessDenied), results.last())
            assertTrue(collector.isCompleted)
            assertEquals(0, source.activeObservers)
        }
    }

    @Test
    fun `revocation racing with provider IO is terminal during registration and reading`() = runTest(dispatcher) {
        val problem = IOException("Media unavailable")
        source.beforeRegister = { source.granted = false }
        source.registrationFailure = problem
        suspend fun verifyDenied() {
            val result = repository.observeAlbumDetails(id, "en").toList().single() as AppResult.Failure
            assertEquals(DataError.AccessDenied, result.error)
            assertSame(problem, result.cause)
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
    }

    @Test
    fun `duplicate provider identities are a recoverable read failure`() = runTest(dispatcher) {
        source.read = { listOf(audioRow(), audioRow()) }
        val results = mutableListOf<AppResult<LibraryAlbumDetails, DataError>>()
        backgroundScope.launch { repository.observeAlbumDetails(id, "en").toList(results) }
        runCurrent()
        assertEquals(DataError.Io, (results.single() as AppResult.Failure).error)
        source.read = { listOf(audioRow()) }
        source.invalidate()
        runCurrent()
        assertTrue(results.last() is AppResult.Success)
        assertEquals(1, source.registrations)
    }
}
