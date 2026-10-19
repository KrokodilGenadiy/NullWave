package com.zaus.nullwave.feature.library.data

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.LibraryQuery
import com.zaus.nullwave.feature.library.api.model.LibraryTrack
import java.io.IOException
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaStoreLibraryRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = FakeMediaStoreAudioSource()
    private val repository = MediaStoreLibraryRepository(source, dispatcher)
    private val query = LibraryQuery("en")

    @Test
    fun `flow is cold and denied access completes without observer or query`() = runTest(dispatcher) {
        source.granted = false
        val flow = repository.observeTracks(query)
        assertEquals(0, source.registrations)
        val results = flow.toList()
        assertEquals(listOf(AppResult.Failure(DataError.AccessDenied)), results)
        assertEquals(0, source.registrations)
        assertEquals(0, source.reads)
    }

    @Test
    fun `observer is installed before initial reading and cancellation unregisters it`() = runTest(dispatcher) {
        source.onRegister = { source.invalidate() }
        source.read = { listOf(audioRow()) }
        val results = mutableListOf<AppResult<List<LibraryTrack>, DataError>>()
        val collector = backgroundScope.launch { repository.observeTracks(query).toList(results) }
        runCurrent()
        assertEquals(1, source.registrations)
        assertEquals(1, source.reads)
        assertEquals("Low Orbit", (results.single() as AppResult.Success).value.single().title)
        collector.cancel()
        runCurrent()
        assertEquals(1, source.closes)
    }

    @Test
    fun `invalidations during a query are conflated but one follow up reading is preserved`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        source.read = {
            if (source.reads == 1) gate.await()
            listOf(audioRow(id = source.reads.toLong()))
        }
        val results = mutableListOf<AppResult<List<LibraryTrack>, DataError>>()
        backgroundScope.launch { repository.observeTracks(query).toList(results) }
        runCurrent()
        repeat(100) { source.invalidate() }
        runCurrent()
        assertEquals(1, source.reads)
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, source.reads)
        assertEquals(2, results.size)
        assertEquals(1, source.activeObservers)
    }

    @Test
    fun `empty provider success is empty data rather than failure`() = runTest(dispatcher) {
        assertEquals(AppResult.Success(emptyList<LibraryTrack>()), repository.observeTracks(query).first())
        assertEquals(1, source.closes)
    }

    @Test
    fun `IO failure preserves original diagnostics and observation can recover`() = runTest(dispatcher) {
        val problem = IOException("provider unavailable")
        source.read = { throw problem }
        val results = mutableListOf<AppResult<List<LibraryTrack>, DataError>>()
        backgroundScope.launch { repository.observeTracks(query).toList(results) }
        runCurrent()
        val failure = results.single() as AppResult.Failure
        assertEquals(DataError.Io, failure.error)
        assertSame(problem, failure.cause)
        assertEquals("provider unavailable", failure.originalMessage)
        assertEquals(1, source.activeObservers)

        source.read = { listOf(audioRow()) }
        source.invalidate()
        runCurrent()
        assertTrue(results.last() is AppResult.Success)
        assertEquals(1, source.registrations)
    }

    @Test
    fun `observation setup failure completes and recollection retries setup`() = runTest(dispatcher) {
        source.registrationFailure = IOException("cannot observe")
        assertEquals(DataError.Io, (repository.observeTracks(query).toList().single() as AppResult.Failure).error)
        assertEquals(0, source.reads)
        source.registrationFailure = null
        assertEquals(AppResult.Success(emptyList<LibraryTrack>()), repository.observeTracks(query).first())
        assertEquals(1, source.closes)
    }

    @Test
    fun `access revoked before the next query clears observation without reading again`() = runTest(dispatcher) {
        val results = mutableListOf<AppResult<List<LibraryTrack>, DataError>>()
        val collector = backgroundScope.launch { repository.observeTracks(query).toList(results) }
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
    fun `access revoked during a successful query never publishes its protected rows`() = runTest(dispatcher) {
        source.read = {
            source.granted = false
            listOf(audioRow())
        }
        assertEquals(listOf(AppResult.Failure(DataError.AccessDenied)), repository.observeTracks(query).toList())
        assertEquals(1, source.closes)
    }

    @Test
    fun `permission race retains SecurityException when access check confirms denial`() = runTest(dispatcher) {
        val problem = SecurityException("audio access revoked")
        source.read = {
            source.granted = false
            throw problem
        }
        val failure = repository.observeTracks(query).toList().single() as AppResult.Failure
        assertEquals(DataError.AccessDenied, failure.error)
        assertSame(problem, failure.cause)
        assertEquals(1, source.closes)
    }

    @Test
    fun `unexplained SecurityException is a defect rather than a false permission gate`() = runTest(dispatcher) {
        val problem = SecurityException("wrong provider operation")
        source.read = { throw problem }
        val thrown = runCatching { repository.observeTracks(query).first() }.exceptionOrNull()
        assertTrue(generateSequence(thrown) { it.cause }.any { it === problem })
        assertEquals(1, source.closes)
    }

    @Test
    fun `programming failure propagates and closes observation`() = runTest(dispatcher) {
        val problem = IllegalArgumentException("invalid projection")
        source.read = { throw problem }
        val thrown = runCatching { repository.observeTracks(query).first() }.exceptionOrNull()
        assertTrue(generateSequence(thrown) { it.cause }.any { it === problem })
        assertEquals(1, source.closes)
    }

    @Test
    fun `collector IO exception is not reclassified as a provider failure`() = runTest(dispatcher) {
        val problem = IOException("UI consumer failure")
        val thrown = runCatching { repository.observeTracks(query).collect { throw problem } }.exceptionOrNull()
        assertTrue(generateSequence(thrown) { it.cause }.any { it === problem })
        assertEquals(1, source.closes)
        assertEquals(1, source.reads)
    }

    @Test
    fun `cancellation during reading closes observation without emitting a UI failure`() = runTest(dispatcher) {
        var cancelled = false
        source.read = {
            try { awaitCancellation() } finally { cancelled = true }
        }
        val results = mutableListOf<AppResult<List<LibraryTrack>, DataError>>()
        val collector = backgroundScope.launch { repository.observeTracks(query).toList(results) }
        runCurrent()
        collector.cancel()
        runCurrent()
        assertTrue(cancelled)
        assertTrue(results.isEmpty())
        assertEquals(1, source.closes)
    }

    @Test
    fun `source self cancellation is not an IO failure`() = runTest(dispatcher) {
        source.read = { throw CancellationException("provider coroutine cancelled") }
        val thrown = runCatching { repository.observeTracks(query).first() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertEquals(1, source.closes)
    }

    @Test
    fun `cleanup failure is suppressed onto the primary failure`() = runTest(dispatcher) {
        val primary = IllegalArgumentException("bad query")
        val cleanup = IllegalStateException("unregister failed")
        source.read = { throw primary }
        source.closeFailure = cleanup
        val thrown = runCatching { repository.observeTracks(query).first() }.exceptionOrNull()
        assertTrue(generateSequence(thrown) { it.cause }.any { it === primary })
        assertTrue(primary.suppressed.any { it === cleanup })
    }

    @Test
    fun `independent collectors own separate observers and cancelling one leaves the other live`() = runTest(dispatcher) {
        val first = backgroundScope.launch { repository.observeTracks(query).collect {} }
        backgroundScope.launch { repository.observeTracks(query.copy(localeTag = "ru")).collect {} }
        runCurrent()
        assertEquals(2, source.activeObservers)
        first.cancel()
        runCurrent()
        assertEquals(1, source.activeObservers)
        val previousReads = source.reads
        source.invalidate()
        runCurrent()
        assertEquals(previousReads + 1, source.reads)
    }
}
