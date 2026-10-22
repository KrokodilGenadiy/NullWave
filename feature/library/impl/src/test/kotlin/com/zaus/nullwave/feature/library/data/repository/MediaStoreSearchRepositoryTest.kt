package com.zaus.nullwave.feature.library.data.repository

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults
import com.zaus.nullwave.feature.library.data.source.audioRow
import com.zaus.nullwave.feature.library.data.source.FakeMediaStoreAudioSource
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaStoreSearchRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = FakeMediaStoreAudioSource()
    private val repository = MediaStoreLibraryRepository(source, dispatcher)
    private val query = checkNotNull(LibrarySearchQuery.fromInput("needle", "en"))

    @Test
    fun `one observation and one read rebuild all groups together on every invalidation`() = runTest(dispatcher) {
        source.read = {
            val id = source.reads.toLong()
            listOf(audioRow(id, title = "Needle $id", artist = "Needle $id").copy(album = "Needle $id", artistId = id, albumId = id))
        }
        val results = mutableListOf<AppResult<LibrarySearchResults, DataError>>()
        val collector = backgroundScope.launch { repository.observeSearch(query).toList(results) }
        runCurrent()
        source.invalidate()
        runCurrent()
        assertEquals(1, source.registrations)
        assertEquals(2, source.reads)
        assertEquals(2, results.size)
        results.forEachIndexed { index, result ->
            val snapshot = (result as AppResult.Success).value
            val id = "external_primary:${index + 1}"
            assertEquals(id, snapshot.tracks.single().track.id.value)
            assertEquals(id, snapshot.albums.single().album.id.value)
            assertEquals(id, snapshot.artists.single().id.value)
            assertEquals(snapshot.tracks.single().track.artwork, snapshot.albums.single().album.artwork)
        }
        collector.cancel()
        runCurrent()
        assertEquals(1, source.closes)
    }

    @Test
    fun `no matches stay observed and a new recording becomes searchable`() = runTest(dispatcher) {
        source.read = { listOf(audioRow()) }
        val results = mutableListOf<AppResult<LibrarySearchResults, DataError>>()
        backgroundScope.launch { repository.observeSearch(query).toList(results) }
        runCurrent()
        assertEquals(AppResult.Success(LibrarySearchResults(emptyList(), emptyList(), emptyList())), results.single())
        source.read = { listOf(audioRow(title = "Needle")) }
        source.invalidate()
        runCurrent()
        assertEquals("Needle", (results.last() as AppResult.Success).value.tracks.single().track.title)
        assertEquals(1, source.activeObservers)
    }

    @Test
    fun `malformed snapshot becomes IO with diagnostics rather than publishing partial hits and can recover`() = runTest(dispatcher) {
        source.read = { listOf(audioRow(title = "Needle"), audioRow(2).copy(displayName = "folder/audio.mp3")) }
        val results = mutableListOf<AppResult<LibrarySearchResults, DataError>>()
        backgroundScope.launch { repository.observeSearch(query).toList(results) }
        runCurrent()
        val failure = results.single() as AppResult.Failure
        assertEquals(DataError.Io, failure.error)
        assertTrue(failure.cause is IOException)
        assertEquals("MediaStore display name contains a path", failure.originalMessage)
        source.read = { listOf(audioRow(title = "Needle")) }
        source.invalidate()
        runCurrent()
        assertTrue(results.last() is AppResult.Success)
        assertEquals(1, source.registrations)
    }

    @Test
    fun `cancellation during matching emits nothing closes the observer once and allows recollection`() = runTest(dispatcher) {
        lateinit var collector: Job
        val rows = (1L..64L).map { audioRow(it, title = "Needle $it") }
        var traversals = 0
        var matchingRows = 0
        source.read = {
            object : AbstractList<MediaStoreAudioRow>() {
                override val size = rows.size
                override fun get(index: Int) = rows[index]
                override fun iterator(): Iterator<MediaStoreAudioRow> {
                    // The full input is validated, then grouped into albums, then matched as tracks.
                    // Group member lists are separate; their artwork work cannot trigger this gate.
                    val matching = ++traversals == 3
                    val iterator = rows.iterator()
                    return object : Iterator<MediaStoreAudioRow> {
                        override fun hasNext() = iterator.hasNext()
                        override fun next(): MediaStoreAudioRow {
                            val row = iterator.next()
                            if (matching && ++matchingRows == 4) collector.cancel()
                            return row
                        }
                    }
                }
            }
        }
        val results = mutableListOf<AppResult<LibrarySearchResults, DataError>>()
        collector = backgroundScope.launch { repository.observeSearch(query).toList(results) }
        runCurrent()
        assertEquals("The cancellation gate must be reached during track matching", 4, matchingRows)
        assertTrue(collector.isCancelled && collector.isCompleted)
        assertTrue("Neither partial Success nor a cancellation-as-Io may escape", results.isEmpty())
        assertEquals(1, source.reads)
        assertEquals(1, source.registrations)
        assertEquals(1, source.closes)
        assertEquals(0, source.activeObservers)
        source.invalidate()
        runCurrent()
        assertEquals(1, source.reads)
        assertEquals(1, source.closes)

        source.read = { rows }
        val next = repository.observeSearch(query).first() as AppResult.Success
        assertEquals(rows.size, next.value.tracks.size)
        assertEquals(2, source.registrations)
        assertEquals(2, source.closes)
        assertEquals(0, source.activeObservers)
    }
}
