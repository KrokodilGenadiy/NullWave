package com.zaus.nullwave.core.playback.runtime

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.playback.preparation.QueuePreparer
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueOwnerReentryTest {
    @Test
    fun `install exception reaches its caller before a reentrant transport can supersede it`() = runTest {
        val fixture = Fixture(this)
        val failedCommand = replace("a")
        val cause = IllegalStateException("install A failed")
        lateinit var transport: Deferred<EmptyResult<PlaybackCommandError>>
        fixture.target.installAction = {
            transport = backgroundScope.async(fixture.main, start = CoroutineStart.UNDISPATCHED) {
                fixture.owner.execute(PlaybackCommand.StopAndClear)
            }
            assertFalse("Reentrant transport must wait for the installation boundary", transport.isCompleted)
            fixture.target.events += "install:a:throws"
            throw cause
        }
        try {
            // Catch at the client boundary, so runTest does not separately report an expected child failure.
            val caller = backgroundScope.async { runCatching { fixture.owner.execute(failedCommand) } }
            runCurrent()

            assertTrue(caller.isCompleted)
            assertOriginalThrowable(cause, caller.await().exceptionOrNull())
            assertEquals(AppResult.Success(Unit), transport.await())
            val diagnostic = withContext(fixture.main) { fixture.owner.lastFailure } as PlaybackCommandFailure.Unexpected
            assertEquals(failedCommand, diagnostic.command)
            assertOriginalThrowable(cause, diagnostic.cause)
            assertEquals(listOf("install:a", "install:a:throws", "transport:StopAndClear"), fixture.target.events)
            assertTrue(fixture.target.commits.isEmpty())
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `failed install cleanup cannot clear a reentrant replacement waiting for its resources`() = runTest {
        val fixture = Fixture(this)
        val releaseReplacement = CompletableDeferred<Unit>()
        val failedCommand = replace("a")
        val cause = IllegalArgumentException("install A failed")
        lateinit var replacement: Deferred<EmptyResult<PlaybackCommandError>>
        fixture.read = { id ->
            if (id == TrackId("b")) releaseReplacement.await()
            AppResult.Success(resolved(id))
        }
        fixture.target.installAction = { commit ->
            if (commit.prepared.request.trackIds.single() == TrackId("a")) {
                replacement = backgroundScope.async(fixture.main, start = CoroutineStart.UNDISPATCHED) {
                    fixture.owner.execute(replace("b"))
                }
                assertFalse(replacement.isCompleted)
                fixture.target.events += "install:a:throws"
                throw cause
            }
            AppResult.Success(Unit)
        }
        try {
            val caller = backgroundScope.async { runCatching { fixture.owner.execute(failedCommand) } }
            runCurrent()

            assertTrue(caller.isCompleted)
            assertOriginalThrowable(cause, caller.await().exceptionOrNull())
            assertEquals(listOf(TrackId("a"), TrackId("b")), fixture.reads)
            assertFalse("The replacement must still own its pending preparation", replacement.isCompleted)
            assertTrue(fixture.target.commits.isEmpty())
            val diagnostic = withContext(fixture.main) { fixture.owner.lastFailure } as PlaybackCommandFailure.Unexpected
            assertEquals(failedCommand, diagnostic.command)
            assertOriginalThrowable(cause, diagnostic.cause)

            releaseReplacement.complete(Unit)
            runCurrent()

            assertTrue("Old cleanup must not clear the replacement's pending record", replacement.isCompleted)
            assertEquals(AppResult.Success(Unit), replacement.await())
            assertEquals(listOf(TrackId("b")), fixture.target.commits.single().prepared.request.trackIds)
            assertSame(diagnostic, withContext(fixture.main) { fixture.owner.lastFailure })
            assertEquals(listOf("install:a", "install:a:throws", "install:b"), fixture.target.events)
        } finally {
            releaseReplacement.complete(Unit)
            fixture.close()
            runCurrent()
        }
    }

    private class Fixture(testScope: TestScope) {
        val main = HandlerLikeImmediateDispatcher(StandardTestDispatcher(testScope.testScheduler, "main-queue"))
        private val serviceJob = SupervisorJob(testScope.backgroundScope.coroutineContext.job)
        val reads = mutableListOf<TrackId>()
        var read: suspend (TrackId) -> AppResult<ResolvedPlaybackTrack, PlaybackSourceError> = {
            AppResult.Success(resolved(it))
        }
        private val resolver = object : PlaybackSourceResolver {
            override suspend fun resolveTrack(id: TrackId): AppResult<ResolvedPlaybackTrack, PlaybackSourceError> {
                reads += id
                return read(id)
            }
        }
        val target = Target()
        val owner = PlaybackQueueOwner(
            CoroutineScope(testScope.backgroundScope.coroutineContext + serviceJob),
            main,
            QueuePreparer(resolver, StandardTestDispatcher(testScope.testScheduler, "preparation")),
            target,
        )

        suspend fun close() {
            owner.close()
            serviceJob.cancel()
        }
    }

    private class Target : QueueCommitTarget {
        val events = mutableListOf<String>()
        val commits = mutableListOf<QueueCommit>()
        var installAction: (QueueCommit) -> EmptyResult<PlaybackCommandError> = { AppResult.Success(Unit) }

        override fun install(commit: QueueCommit): EmptyResult<PlaybackCommandError> {
            events += "install:${commit.prepared.request.trackIds.single().value}"
            val result = installAction(commit)
            if (result is AppResult.Success) commits += commit
            return result
        }

        override fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> {
            assertEquals(PlaybackCommand.StopAndClear, command)
            events += "transport:StopAndClear"
            return AppResult.Success(Unit)
        }
    }

    /** A queued Main entry permits immediate resumption inside that entry, including Mutex unlock. */
    private class HandlerLikeImmediateDispatcher(private val queue: CoroutineDispatcher) : CoroutineDispatcher() {
        private val onMain = ThreadLocal.withInitial { false }

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = onMain.get() != true

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.dispatch(context, Runnable {
                val previous = onMain.get()
                onMain.set(true)
                try { block.run() }
                finally { onMain.set(previous) }
            })
        }
    }

    companion object {
        private fun replace(value: String) = PlaybackCommand.ReplaceQueue(
            PlaybackQueueRequest.create(listOf(TrackId(value)), startIndex = 0),
        )

        private fun resolved(id: TrackId) = ResolvedPlaybackTrack(
            MediaTrack(id, "Track ${id.value}", artist = null, album = null, durationMs = 10),
            "opaque:${id.value}",
        )

        private fun assertOriginalThrowable(original: Throwable, actual: Throwable?) {
            val failure = actual ?: throw AssertionError("The caller must receive the installation exception")
            assertEquals(original.javaClass, failure.javaClass)
            assertEquals(original.message, failure.message)
            assertTrue("Original exception must remain in the cause chain", generateSequence(failure) { it.cause }.any { it === original })
        }
    }
}
