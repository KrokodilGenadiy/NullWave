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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackNativeAdmissionTest {
    @Test
    fun `native stop revokes pending preparation before acting and can retain the committed queue`() = runTest {
        val fixture = Fixture(this)
        val release = CompletableDeferred<Unit>()
        try {
            fixture.replace("old").also { runCurrent(); assertEquals(AppResult.Success(Unit), it.await()) }
            val old = fixture.target.current
            fixture.read = { id ->
                withContext(NonCancellable) { release.await() }
                AppResult.Success(resolved(id))
            }
            val replacement = fixture.replace("late", UnconfinedTestDispatcher(testScheduler))
            runCurrent()
            var nativeStops = 0
            val stop = backgroundScope.async {
                fixture.owner.executeNative {
                    assertEquals(PlaybackCommandError.Superseded, (replacement.getCompleted() as AppResult.Failure).error)
                    nativeStops++
                    // Player.stop retains the existing playlist; it is not the portable StopAndClear action.
                    assertSame(old, fixture.target.current)
                }
            }
            runCurrent()

            assertEquals(AppResult.Success(Unit), stop.await())
            assertEquals(1, nativeStops)
            assertSame(old, fixture.target.current)
            release.complete(Unit)
            runCurrent()
            assertSame(old, fixture.target.current)
            assertEquals(1, fixture.target.installs)
            assertTrue(fixture.target.commands.isEmpty())
        } finally {
            release.complete(Unit)
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `close and parent cancellation both reject native actions`() = runTest {
        val closed = Fixture(this)
        val cancelled = Fixture(this)
        var calls = 0
        try {
            closed.owner.close()
            cancelled.serviceJob.cancel()
            assertEquals(
                AppResult.Failure(PlaybackCommandError.ConnectionUnavailable),
                closed.owner.executeNative { calls++ },
            )
            assertEquals(
                AppResult.Failure(PlaybackCommandError.ConnectionUnavailable),
                cancelled.owner.executeNative { calls++ },
            )
            assertEquals(0, calls)
        } finally {
            closed.close()
            cancelled.close()
        }
    }

    @Test
    fun `native callback reentry waits until the current action returns`() = runTest {
        val immediate = UnconfinedTestDispatcher(testScheduler)
        val fixture = Fixture(this, immediate)
        val events = mutableListOf<String>()
        lateinit var reentered: Deferred<EmptyResult<PlaybackCommandError>>
        try {
            assertEquals(AppResult.Success(Unit), fixture.owner.executeNative {
                events += "first:start"
                reentered = backgroundScope.async(immediate, start = CoroutineStart.UNDISPATCHED) {
                    fixture.owner.executeNative { events += "second" }
                }
                assertFalse(reentered.isCompleted)
                events += "first:end"
            })
            runCurrent()

            assertEquals(AppResult.Success(Unit), reentered.await())
            assertEquals(listOf("first:start", "first:end", "second"), events)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a native exception reaches its caller without cancelling the service owner`() = runTest {
        val fixture = Fixture(this)
        val cause = IllegalArgumentException("native action failed")
        var laterActions = 0
        try {
            val failure = runCatching { fixture.owner.executeNative { throw cause } }.exceptionOrNull()
                ?: throw AssertionError("Native exception must propagate")
            assertEquals(cause.javaClass, failure.javaClass)
            assertEquals(cause.message, failure.message)
            assertTrue(generateSequence(failure) { it.cause }.any { it === cause })

            assertEquals(AppResult.Success(Unit), fixture.owner.executeNative { laterActions++ })
            assertEquals(1, laterActions)
        } finally {
            fixture.close()
        }
    }

    private class Fixture(private val testScope: TestScope, main: CoroutineDispatcher = StandardTestDispatcher(testScope.testScheduler)) {
        val serviceJob = SupervisorJob(testScope.backgroundScope.coroutineContext.job)
        var read: suspend (TrackId) -> AppResult<ResolvedPlaybackTrack, PlaybackSourceError> = {
            AppResult.Success(resolved(it))
        }
        private val resolver = object : PlaybackSourceResolver {
            override suspend fun resolveTrack(id: TrackId): AppResult<ResolvedPlaybackTrack, PlaybackSourceError> = read(id)
        }
        val target = Target()
        val owner = PlaybackQueueOwner(
            CoroutineScope(testScope.backgroundScope.coroutineContext + serviceJob),
            main,
            QueuePreparer(resolver, StandardTestDispatcher(testScope.testScheduler)),
            target,
        )

        fun replace(id: String, callerDispatcher: CoroutineDispatcher? = null): Deferred<EmptyResult<PlaybackCommandError>> {
            val command = PlaybackCommand.ReplaceQueue(PlaybackQueueRequest.create(listOf(TrackId(id)), 0))
            return if (callerDispatcher == null) testScope.backgroundScope.async { owner.execute(command) }
            else testScope.backgroundScope.async(callerDispatcher) { owner.execute(command) }
        }

        suspend fun close() {
            owner.close()
            serviceJob.cancel()
        }
    }

    private class Target : QueueCommitTarget {
        var current: QueueCommit? = null
        var installs = 0
        val commands = mutableListOf<PlaybackCommand>()
        override fun install(commit: QueueCommit): EmptyResult<PlaybackCommandError> {
            current = commit
            installs++
            return AppResult.Success(Unit)
        }
        override fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> {
            commands += command
            return AppResult.Success(Unit)
        }
    }

    companion object {
        private fun resolved(id: TrackId) = ResolvedPlaybackTrack(
            MediaTrack(id, "Track ${id.value}", null, null, 10), "opaque:${id.value}",
        )
    }
}
