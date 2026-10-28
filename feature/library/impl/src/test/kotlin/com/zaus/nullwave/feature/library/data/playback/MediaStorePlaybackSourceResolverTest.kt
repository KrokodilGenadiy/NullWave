package com.zaus.nullwave.feature.library.data.playback

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.library.data.mapping.artworkReferences
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import com.zaus.nullwave.feature.library.data.source.MediaStorePlaybackData
import com.zaus.nullwave.feature.library.data.source.MediaStorePlaybackSource
import com.zaus.nullwave.feature.library.data.source.MediaStoreTrackAddress
import java.io.IOException
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real resolver/mapping/error handling with only the Android provider seam replaced. */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaStorePlaybackSourceResolverTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = FakePlaybackSource()
    private val resolver = MediaStorePlaybackSourceResolver(source, dispatcher)
    private val id = TrackId("external_primary:1")

    @Test
    fun `lookup returns fresh matching metadata and the platform resource URI`() = runTest(dispatcher) {
        val result = success(resolver.resolveTrack(id))
        assertEquals(id, result.track.id)
        assertEquals("Low Orbit", result.track.title)
        assertEquals("Nullwave", result.track.artist)
        assertEquals("Signals", result.track.album)
        assertEquals(180_000L, result.track.durationMs)
        assertEquals("content://media/external_primary/audio/media/1", result.resourceUri)
        assertEquals(listOf(MediaStoreTrackAddress("external_primary", 1)), source.addresses)
    }

    @Test
    fun `metadata uses the catalog fallback and unknown value rules`() = runTest(dispatcher) {
        source.read = {
            playbackData(row().copy(
                title = "  ", displayName = " recorder.m4a ", artist = "<unknown>",
                album = " ", durationMs = -1, sizeBytes = -5,
            ))
        }
        val track = success(resolver.resolveTrack(id)).track
        assertEquals("recorder.m4a", track.title)
        assertNull(track.artist)
        assertNull(track.album)
        assertNull(track.durationMs)
        assertNull(track.sizeBytes)
    }

    @Test
    fun `missing both title and display name is a provider failure rather than a constructor crash`() = runTest(dispatcher) {
        source.read = { playbackData(row().copy(title = null, displayName = " ")) }
        val failure = failure(resolver.resolveTrack(id))
        assertEquals(PlaybackSourceError.Io, failure.error)
        assertTrue(failure.cause is IOException)
        assertEquals(failure.cause?.message, failure.originalMessage)
    }

    @Test
    fun `unrecognized identities return not found without querying`() = runTest(dispatcher) {
        // Grammar cases belong to MediaStoreTrackAddressTest; here verify the resolver's no-I/O contract.
        for (value in listOf("foreign", "external_primary:01")) {
            assertEquals(PlaybackSourceError.NotFound, failure(resolver.resolveTrack(TrackId(value))).error)
        }
        assertTrue(source.addresses.isEmpty())
    }

    @Test
    fun `denied access takes precedence over parsing and prevents the provider read`() = runTest(dispatcher) {
        source.granted = false
        assertEquals(PlaybackSourceError.AccessDenied, failure(resolver.resolveTrack(TrackId("foreign"))).error)
        assertEquals(PlaybackSourceError.AccessDenied, failure(resolver.resolveTrack(id)).error)
        assertTrue(source.addresses.isEmpty())
    }

    @Test
    fun `missing row returns not found rather than an IO failure`() = runTest(dispatcher) {
        source.read = { null }
        assertEquals(PlaybackSourceError.NotFound, failure(resolver.resolveTrack(id)).error)
        assertEquals(1, source.addresses.size)
    }

    @Test
    fun `access revoked during successful reading hides the returned resource`() = runTest(dispatcher) {
        source.read = { source.granted = false; playbackData() }
        assertEquals(PlaybackSourceError.AccessDenied, failure(resolver.resolveTrack(id)).error)
    }

    @Test
    fun `access revoked while an empty query completes is access denied rather than not found`() = runTest(dispatcher) {
        source.read = { source.granted = false; null }
        assertEquals(PlaybackSourceError.AccessDenied, failure(resolver.resolveTrack(id)).error)
    }

    @Test
    fun `access lost only at the final publication check cannot expose mapped metadata`() = runTest(dispatcher) {
        // Initial access and post-query access succeed; the publication check sees the revocation.
        source.accessCheck = { source.accessChecks < 3 }
        assertEquals(PlaybackSourceError.AccessDenied, failure(resolver.resolveTrack(id)).error)
        assertEquals(1, source.addresses.size)
        assertEquals(3, source.accessChecks)
    }

    @Test
    fun `IO failures retain the original throwable and message`() = runTest(dispatcher) {
        val problem = IOException("provider could not read the item")
        source.read = { throw problem }
        val failure = failure(resolver.resolveTrack(id))
        assertEquals(PlaybackSourceError.Io, failure.error)
        assertSame(problem, failure.cause)
        assertEquals(problem.message, failure.originalMessage)
    }

    @Test
    fun `revocation wins over an IO exception without losing diagnostics`() = runTest(dispatcher) {
        val problem = IOException("storage failed while access changed")
        source.read = { source.granted = false; throw problem }
        val failure = failure(resolver.resolveTrack(id))
        assertEquals(PlaybackSourceError.AccessDenied, failure.error)
        assertSame(problem, failure.cause)
        assertEquals(problem.message, failure.originalMessage)
    }

    @Test
    fun `provider security denial remains typed even when the broad permission is granted`() = runTest(dispatcher) {
        val problem = SecurityException("provider denied this resource")
        source.read = { throw problem }
        val failure = failure(resolver.resolveTrack(id))
        assertTrue(source.granted)
        assertEquals(PlaybackSourceError.AccessDenied, failure.error)
        assertSame(problem, failure.cause)
        assertEquals(problem.message, failure.originalMessage)
    }

    @Test
    fun `programming errors propagate instead of becoming a false IO result`() = runTest(dispatcher) {
        val problem = IllegalStateException("broken source contract")
        source.read = { throw problem }
        val propagated = expectThrown<IllegalStateException> { resolver.resolveTrack(id) }
        assertEquals(problem.message, propagated.message)
        // Coroutine stack-trace recovery may wrap the exception across the dispatcher boundary.
        assertTrue(generateSequence<Throwable>(propagated) { it.cause }.any { it === problem })
    }

    @Test
    fun `provider reads run on the injected IO dispatcher`() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler, name = "resolver-io")
        val resolver = MediaStorePlaybackSourceResolver(source, io)
        source.read = {
            assertSame(io, currentCoroutineContext()[ContinuationInterceptor])
            playbackData()
        }
        assertTrue(resolver.resolveTrack(id) is AppResult.Success)
    }

    @Test
    fun `each resolve reads again and observes fresh metadata and fresh permission`() = runTest(dispatcher) {
        source.read = { playbackData(row().copy(title = "Reading ${source.addresses.size}")) }
        assertEquals("Reading 1", success(resolver.resolveTrack(id)).track.title)
        assertEquals("Reading 2", success(resolver.resolveTrack(id)).track.title)
        source.granted = false
        assertEquals(PlaybackSourceError.AccessDenied, failure(resolver.resolveTrack(id)).error)
        assertEquals(2, source.addresses.size)
    }

    @Test
    fun `mismatched provider identity cannot resolve the requested track`() = runTest(dispatcher) {
        for (returned in listOf(row().copy(id = 2), row().copy(volume = "1234-abcd"))) {
            source.read = { playbackData(returned) }
            val failure = failure(resolver.resolveTrack(id))
            assertEquals(PlaybackSourceError.Io, failure.error)
            assertTrue(failure.cause is IOException)
        }
    }

    @Test
    fun `artwork uses the complete album and matches the catalog cache revision`() = runTest(dispatcher) {
        val first = row().copy(albumId = 8, dateModifiedSeconds = 10, sizeBytes = 100)
        val second = row().copy(id = 2, albumId = 8, dateModifiedSeconds = 20, sizeBytes = 200)
        val album = listOf(second, first)
        source.read = { playbackData(first, album) }
        val expected = album.artworkReferences().getValue("external_primary:8")
        assertEquals(expected, success(resolver.resolveTrack(id)).track.artwork)
    }

    @Test
    fun `changing another album member invalidates the playing track artwork revision`() = runTest(dispatcher) {
        val first = row().copy(albumId = 8, dateModifiedSeconds = 10)
        var second = row().copy(id = 2, albumId = 8, dateModifiedSeconds = 20)
        source.read = { playbackData(first, listOf(first, second)) }
        val before = success(resolver.resolveTrack(id)).track.artwork
        second = second.copy(dateModifiedSeconds = 21)
        val after = success(resolver.resolveTrack(id)).track.artwork
        assertEquals(before?.key, after?.key)
        assertNotEquals(before?.revision, after?.revision)
    }

    @Test
    fun `missing album membership preserves absent artwork`() = runTest(dispatcher) {
        source.read = { playbackData(row().copy(albumId = null)) }
        assertNull(success(resolver.resolveTrack(id)).track.artwork)
    }

    @Test
    fun `caller cancellation while reading propagates and completes source cleanup`() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>()
        var cleaned = false
        source.read = {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cleaned = true }
        }
        val request = async { resolver.resolveTrack(id) }
        entered.await()
        request.cancel()
        request.join()
        assertTrue(request.isCancelled)
        assertTrue(cleaned)
        expectThrown<CancellationException> { request.await() }
    }

    @Test
    fun `caller cancelled before IO dispatch never reaches the provider`() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler, name = "pending-resolver-io")
        val resolver = MediaStorePlaybackSourceResolver(source, io)
        // Enter resolve now, but leave its different IO dispatcher queued on the shared scheduler.
        val request = async(start = CoroutineStart.UNDISPATCHED) { resolver.resolveTrack(id) }
        assertTrue(source.addresses.isEmpty())
        request.cancel()
        runCurrent()
        expectThrown<CancellationException> { request.await() }
        assertTrue(source.addresses.isEmpty())
        assertEquals(0, source.accessChecks)
    }

    @Test
    fun `a source returning a late value after cancellation cannot publish success`() = runTest(dispatcher) {
        source.read = {
            currentCoroutineContext().cancel()
            playbackData()
        }
        var published: AppResult<ResolvedPlaybackTrack, PlaybackSourceError>? = null
        val request = async { resolver.resolveTrack(id).also { published = it } }
        runCurrent()
        expectThrown<CancellationException> { request.await() }
        assertNull(published)
    }

    @Test
    fun `caller cancellation wins over a provider IO exception during cancellation`() = runTest(dispatcher) {
        source.read = {
            currentCoroutineContext().cancel()
            throw IOException("late provider failure")
        }
        val request = async { resolver.resolveTrack(id) }
        runCurrent()
        expectThrown<CancellationException> { request.await() }
        assertTrue(request.isCancelled)
    }

    private fun success(result: AppResult<ResolvedPlaybackTrack, PlaybackSourceError>): ResolvedPlaybackTrack =
        (result as AppResult.Success).value

    private fun failure(result: AppResult<ResolvedPlaybackTrack, PlaybackSourceError>): AppResult.Failure<PlaybackSourceError> =
        result as AppResult.Failure

    private suspend inline fun <reified T : Throwable> expectThrown(block: suspend () -> Any?): T {
        try {
            block()
        } catch (failure: Throwable) {
            if (failure is T) return failure
            throw failure
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }

    private class FakePlaybackSource : MediaStorePlaybackSource {
        var granted = true
        var accessChecks = 0
        var accessCheck: (() -> Boolean)? = null
        val addresses = mutableListOf<MediaStoreTrackAddress>()
        var read: suspend (MediaStoreTrackAddress) -> MediaStorePlaybackData? = { playbackData() }
        override fun hasReadAccess(): Boolean {
            accessChecks++
            return accessCheck?.invoke() ?: granted
        }
        override suspend fun readTrack(address: MediaStoreTrackAddress): MediaStorePlaybackData? {
            addresses += address
            return read(address)
        }
    }

    companion object {
        private fun row() = MediaStoreAudioRow(
            id = 1, volume = "external_primary", title = "Low Orbit", displayName = "track.mp3",
            artist = "Nullwave", album = "Signals", durationMs = 180_000, dateAddedSeconds = 1,
        )

        private fun playbackData(
            row: MediaStoreAudioRow = row(),
            albumRows: List<MediaStoreAudioRow> = emptyList(),
        ) = MediaStorePlaybackData(
            row = row,
            albumRows = albumRows,
            resourceUri = "content://media/${row.volume}/audio/media/${row.id}",
        )
    }
}
