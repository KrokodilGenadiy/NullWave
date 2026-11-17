package com.zaus.nullwave.core.playback.preparation

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.result.AppResult
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real preparation logic with a deterministic source boundary; there is no playback engine here. */
@OptIn(ExperimentalCoroutinesApi::class)
class QueuePreparerTest {
    private val dispatcher = StandardTestDispatcher()
    private val resolver = FakeResolver()
    private val preparer = QueuePreparer(resolver, dispatcher)

    @Test
    fun `unique resources are read in first occurrence order and repeated selection is preserved`() = runTest(dispatcher) {
        val request = PlaybackQueueRequest.create(
            listOf(id("b"), id("a"), id("b"), id("c"), id("a")),
            startIndex = 4,
            startPositionMs = Long.MAX_VALUE,
            playRequested = false,
        )
        val prepared = success(preparer.prepare(request))

        assertEquals(listOf(id("b"), id("a"), id("c")), resolver.calls)
        assertSame(request, prepared.request)
        assertEquals(request.trackIds, prepared.items.map { it.track.id })
        assertSame(prepared.items[0], prepared.items[2])
        assertSame(prepared.items[1], prepared.items[4])
        assertEquals(id("a"), prepared.items[prepared.request.startIndex].track.id)
        // Catalogue duration is only metadata; the eventual engine decides how to clamp the position.
        assertEquals(Long.MAX_VALUE, prepared.request.startPositionMs)
        assertFalse(prepared.request.playRequested)
    }

    @Test
    fun `resource URI remains opaque to preparation`() = runTest(dispatcher) {
        val resource = resolved(id("a")).copy(resourceUri = "source-specific resource ?token=#value")
        resolver.read = { AppResult.Success(resource) }
        val prepared = success(preparer.prepare(request("a")))
        assertSame(resource, prepared.items.single())
    }

    @Test
    fun `any source failure rejects the complete request and prevents later reads`() = runTest(dispatcher) {
        for (error in listOf(PlaybackSourceError.AccessDenied(), PlaybackSourceError.NotFound, PlaybackSourceError.Io)) {
            val source = FakeResolver().apply {
                read = { track ->
                    if (track == id("b")) AppResult.Failure(error)
                    else AppResult.Success(resolved(track))
                }
            }
            val result = QueuePreparer(source, dispatcher).prepare(request("a", "a", "b", "c"))
            assertEquals(
                PlaybackCommandError.SourceFailure(id("b"), error),
                failure(result).error,
            )
            assertEquals(listOf(id("a"), id("b")), source.calls)
        }
    }

    @Test
    fun `source failure retains cause and explicit original message including null`() = runTest(dispatcher) {
        val problem = IOException("raw provider message")
        for (originalMessage in listOf("diagnostic captured earlier", null)) {
            resolver.read = { AppResult.Failure(PlaybackSourceError.Io, problem, originalMessage) }
            val failure = failure(preparer.prepare(request("a")))
            assertEquals(PlaybackCommandError.SourceFailure(id("a"), PlaybackSourceError.Io), failure.error)
            assertSame(problem, failure.cause)
            assertEquals(originalMessage, failure.originalMessage)
        }
    }

    @Test
    fun `source diagnostics without throwable are retained`() = runTest(dispatcher) {
        resolver.read = {
            AppResult.Failure(PlaybackSourceError.NotFound, originalMessage = "Record no longer exists")
        }
        val failure = failure(preparer.prepare(request("a")))
        assertNull(failure.cause)
        assertEquals("Record no longer exists", failure.originalMessage)
    }

    @Test
    fun `success for a different identity is a resolver contract violation`() = runTest(dispatcher) {
        resolver.read = { AppResult.Success(resolved(id("wrong"))) }
        expectThrown<IllegalStateException> { preparer.prepare(request("a", "b")) }
        assertEquals(listOf(id("a")), resolver.calls)
    }

    @Test
    fun `unexpected exceptions propagate without becoming source failures`() = runTest(dispatcher) {
        val problem = IllegalArgumentException("Broken resolver implementation")
        resolver.read = { throw problem }
        val propagated = expectThrown<IllegalArgumentException> { preparer.prepare(request("a")) }
        assertEquals(problem.message, propagated.message)
        // Coroutine stack-trace recovery can add a copy while retaining the original cause.
        assertTrue(generateSequence<Throwable>(propagated) { it.cause }.any { it === problem })
    }

    @Test
    fun `new preparations resolve fresh resources instead of sharing a cache`() = runTest(dispatcher) {
        resolver.read = { track -> AppResult.Success(resolved(track, title = "Reading ${resolver.calls.size}")) }
        val first = success(preparer.prepare(request("a", "a")))
        val second = success(preparer.prepare(request("a", "a")))
        assertEquals(listOf(id("a"), id("a")), resolver.calls)
        assertEquals("Reading 1", first.items.first().track.title)
        assertEquals("Reading 2", second.items.first().track.title)
    }

    @Test
    fun `preparation enters the injected default dispatcher`() = runTest(dispatcher) {
        val default = StandardTestDispatcher(testScheduler, name = "queue-preparation")
        resolver.read = { track ->
            assertSame(default, currentCoroutineContext()[ContinuationInterceptor])
            AppResult.Success(resolved(track))
        }
        assertTrue(QueuePreparer(resolver, default).prepare(request("a")) is AppResult.Success)
    }

    @Test
    fun `cancellation before default dispatch avoids every source call`() = runTest(dispatcher) {
        val default = StandardTestDispatcher(testScheduler, name = "pending-preparation")
        val preparer = QueuePreparer(resolver, default)
        val preparation = async(start = CoroutineStart.UNDISPATCHED) { preparer.prepare(request("a")) }
        assertTrue(resolver.calls.isEmpty())
        preparation.cancel()
        runCurrent()
        expectThrown<CancellationException> { preparation.await() }
        assertTrue(resolver.calls.isEmpty())
    }

    @Test
    fun `cancellation while resolving awaits cleanup and prevents later resources`() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>()
        var cleaned = false
        resolver.read = {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cleaned = true }
        }
        var published: AppResult<PreparedQueue, PlaybackCommandError.SourceFailure>? = null
        val preparation = async { preparer.prepare(request("a", "b")).also { published = it } }
        entered.await()
        preparation.cancel()
        preparation.join()
        assertTrue(cleaned)
        assertNull(published)
        assertEquals(listOf(id("a")), resolver.calls)
        expectThrown<CancellationException> { preparation.await() }
    }

    @Test
    fun `late source success after cancellation cannot publish a prepared queue`() = runTest(dispatcher) {
        resolver.read = { track ->
            currentCoroutineContext().cancel()
            AppResult.Success(resolved(track))
        }
        var published: AppResult<PreparedQueue, PlaybackCommandError.SourceFailure>? = null
        val preparation = async { preparer.prepare(request("a", "b")).also { published = it } }
        runCurrent()
        expectThrown<CancellationException> { preparation.await() }
        assertNull(published)
        assertEquals(listOf(id("a")), resolver.calls)
    }

    @Test
    fun `late source failure after cancellation remains cancellation`() = runTest(dispatcher) {
        resolver.read = {
            currentCoroutineContext().cancel()
            AppResult.Failure(PlaybackSourceError.Io, IOException("Late provider failure"))
        }
        var published: AppResult<PreparedQueue, PlaybackCommandError.SourceFailure>? = null
        val preparation = async { preparer.prepare(request("a", "b")).also { published = it } }
        runCurrent()
        expectThrown<CancellationException> { preparation.await() }
        assertNull(published)
        assertEquals(listOf(id("a")), resolver.calls)
    }

    @Test
    fun `overlapping preparations of the same track are isolated when one is cancelled`() = runTest(dispatcher) {
        val firstEntered = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        var firstCleaned = false
        resolver.read = { track ->
            if (resolver.calls.size == 1) {
                firstEntered.complete(Unit)
                try { awaitCancellation() } finally { firstCleaned = true }
            } else {
                secondEntered.complete(Unit)
                releaseSecond.await()
                AppResult.Success(resolved(track, title = "Independent resource"))
            }
        }
        val first = async { preparer.prepare(request("a")) }
        val secondRequest = request("a", "a")
        val second = async { preparer.prepare(secondRequest) }
        runCurrent()
        assertTrue(firstEntered.isCompleted)
        assertTrue(secondEntered.isCompleted)
        assertEquals(listOf(id("a"), id("a")), resolver.calls)

        first.cancel()
        first.join()
        assertTrue(firstCleaned)
        assertFalse(second.isCompleted)
        releaseSecond.complete(Unit)
        val prepared = success(second.await())
        assertSame(secondRequest, prepared.request)
        assertEquals(listOf(id("a"), id("a")), prepared.items.map { it.track.id })
        assertSame(prepared.items[0], prepared.items[1])
        assertEquals("Independent resource", prepared.items[0].track.title)
        expectThrown<CancellationException> { first.await() }
    }

    private fun success(result: AppResult<PreparedQueue, PlaybackCommandError.SourceFailure>): PreparedQueue =
        (result as AppResult.Success).value

    private fun failure(
        result: AppResult<PreparedQueue, PlaybackCommandError.SourceFailure>,
    ): AppResult.Failure<PlaybackCommandError.SourceFailure> = result as AppResult.Failure

    private suspend inline fun <reified T : Throwable> expectThrown(block: suspend () -> Any?): T {
        try {
            block()
        } catch (failure: Throwable) {
            if (failure is T) return failure
            throw failure
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }

    private class FakeResolver : PlaybackSourceResolver {
        val calls = mutableListOf<TrackId>()
        var read: suspend (TrackId) -> AppResult<ResolvedPlaybackTrack, PlaybackSourceError> = {
            AppResult.Success(resolved(it))
        }

        override suspend fun resolveTrack(id: TrackId): AppResult<ResolvedPlaybackTrack, PlaybackSourceError> {
            calls += id
            return read(id)
        }
    }

    companion object {
        private fun id(value: String) = TrackId(value)
        private fun request(vararg ids: String) = PlaybackQueueRequest.create(ids.map(::id), startIndex = 0)
        private fun resolved(id: TrackId, title: String = "Track ${id.value}") = ResolvedPlaybackTrack(
            MediaTrack(id, title, artist = null, album = null, durationMs = 10),
            "opaque:${id.value}",
        )
    }
}
