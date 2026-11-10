package com.zaus.nullwave.core.playback.runtime

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.playback.preparation.QueuePreparer
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
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
class PlaybackQueueOwnerTest {
    @Test
    fun `failed preflight keeps the old queue and preserves diagnostics without partial installation`() = runTest {
        val fixture = Fixture(this)
        fixture.replace("old").also { runCurrent(); assertSuccess(it.await()) }
        val old = fixture.target.current
        val beforeFailure = fixture.allocations
        val cause = IOException("provider text")
        val release = CompletableDeferred<Unit>()
        fixture.resolver.read = { id ->
            if (id == trackId("bad")) {
                release.await()
                AppResult.Failure(PlaybackSourceError.AccessDenied, cause, "captured diagnostic")
            } else AppResult.Success(resolved(id))
        }
        val reply = fixture.replace("good", "bad", "unvisited")
        runCurrent()
        assertSame(old, fixture.target.current)
        assertEquals(beforeFailure, fixture.allocations)
        release.complete(Unit)
        runCurrent()
        val failure = reply.await() as AppResult.Failure
        assertEquals(PlaybackCommandError.SourceFailure(trackId("bad"), PlaybackSourceError.AccessDenied), failure.error)
        assertSame(cause, failure.cause)
        assertEquals("captured diagnostic", failure.originalMessage)
        assertEquals(listOf(trackId("old"), trackId("good"), trackId("bad")), fixture.resolver.calls)
        assertSame(old, fixture.target.current)
        assertEquals(1, fixture.target.commits.size)
        assertEquals(beforeFailure, fixture.allocations)
        fixture.close()
    }

    @Test
    fun `latest replacement can install while the superseded resolver is still cleaning up`() = runTest {
        val fixture = Fixture(this)
        val releaseOld = CompletableDeferred<Unit>()
        fixture.resolver.read = { id ->
            if (id == trackId("a")) withContext(NonCancellable) { releaseOld.await() }
            AppResult.Success(resolved(id))
        }
        val first = fixture.replace("a")
        runCurrent()
        val second = fixture.replace("b", "b")
        runCurrent()
        assertFailure(first.await(), PlaybackCommandError.Superseded)
        assertSuccess(second.await())
        assertEquals(listOf(trackId("b"), trackId("b")), fixture.target.current!!.prepared.request.trackIds)
        assertEquals(2, fixture.allocations)
        val current = fixture.target.current
        releaseOld.complete(Unit)
        runCurrent()
        assertSame(current, fixture.target.current)
        assertEquals(1, fixture.target.commits.size)
        assertEquals(2, fixture.allocations)
        fixture.close()
    }

    @Test
    fun `transport revokes pending replacement before the engine action including rejected actions`() = runTest {
        for (action in listOf("pause", "play", "seek", "select", "next", "previous", "stop", "retry")) {
            val fixture = Fixture(this)
            fixture.replace("old").also { runCurrent(); assertSuccess(it.await()) }
            val old = fixture.target.current!!
            val release = CompletableDeferred<Unit>()
            fixture.resolver.read = { id ->
                withContext(NonCancellable) { release.await() }
                AppResult.Success(resolved(id))
            }
            val replacement = fixture.replace("pending", dispatcher = UnconfinedTestDispatcher(testScheduler))
            runCurrent()
            val command = when (action) {
                "pause" -> PlaybackCommand.SetPlayRequested(false)
                "play" -> PlaybackCommand.SetPlayRequested(true)
                "seek" -> PlaybackCommand.SeekTo(old.current, 4)
                "select" -> PlaybackCommand.SelectQueueEntry(old.current.sessionId, old.current.entryId)
                "next" -> PlaybackCommand.Next
                "previous" -> PlaybackCommand.Previous
                "stop" -> PlaybackCommand.StopAndClear
                else -> PlaybackCommand.Retry(old.current, PlaybackProblemId("problem"))
            }
            // An immediate waiter makes ordering observable inside the target, not just after both calls finish.
            fixture.target.beforeExecute = { assertFailure(replacement.getCompleted(), PlaybackCommandError.Superseded) }
            fixture.target.commandResult = AppResult.Failure(PlaybackCommandError.CommandUnavailable)
            val transport = fixture.submit(command)
            runCurrent()
            assertFailure(transport.await(), PlaybackCommandError.CommandUnavailable)
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf(command), fixture.target.commands)
            assertEquals(1, fixture.target.commits.size)
            fixture.close()
        }
    }

    @Test
    fun `repeat and shuffle preserve preparation and installation uses the latest engine modes`() = runTest {
        val fixture = Fixture(this)
        val release = CompletableDeferred<Unit>()
        fixture.resolver.read = { id -> release.await(); AppResult.Success(resolved(id)) }
        val replacement = fixture.replace("new")
        runCurrent()
        for (command in listOf(PlaybackCommand.SetRepeat(RepeatMode.All), PlaybackCommand.SetShuffle(true))) {
            fixture.submit(command).also { runCurrent(); assertSuccess(it.await()) }
        }
        assertFalse(replacement.isCompleted)
        release.complete(Unit)
        runCurrent()
        assertSuccess(replacement.await())
        assertEquals(listOf(RepeatMode.All to true), fixture.target.installModes)
        fixture.close()
    }

    @Test
    fun `cancelled caller before Main admission does not start service work`() = runTest {
        val fixture = Fixture(this)
        val caller = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            fixture.owner.execute(PlaybackCommand.ReplaceQueue(request("never")))
        }
        caller.cancel()
        runCurrent()
        expectThrown<CancellationException> { caller.await() }
        assertTrue(fixture.resolver.calls.isEmpty())
        assertTrue(fixture.target.commits.isEmpty())
        fixture.close()
    }

    @Test
    fun `cancelling UI wait after admission does not cancel the accepted replacement`() = runTest {
        val fixture = Fixture(this)
        val release = CompletableDeferred<Unit>()
        fixture.resolver.read = { id -> release.await(); AppResult.Success(resolved(id)) }
        val caller = fixture.replace("accepted")
        runCurrent()
        assertEquals(listOf(trackId("accepted")), fixture.resolver.calls)
        caller.cancel()
        runCurrent()
        release.complete(Unit)
        runCurrent()
        expectThrown<CancellationException> { caller.await() }
        assertEquals(listOf(trackId("accepted")), fixture.target.current!!.prepared.request.trackIds)
        assertEquals(1, fixture.target.commits.size)
        fixture.close()
    }

    @Test
    fun `source rejection is retained with diagnostics after the UI stops waiting`() = runTest {
        val fixture = Fixture(this)
        val release = CompletableDeferred<Unit>()
        val cause = IOException("original provider message")
        fixture.resolver.read = {
            release.await()
            AppResult.Failure(PlaybackSourceError.Io, cause, "captured source diagnostic")
        }
        val command = PlaybackCommand.ReplaceQueue(request("rejected"))
        val caller = fixture.submit(command)
        runCurrent()
        caller.cancel()
        release.complete(Unit)
        runCurrent()
        val diagnostic = withContext(fixture.main) { fixture.owner.lastFailure } as PlaybackCommandFailure.Rejected
        assertEquals(command, diagnostic.command)
        assertEquals(PlaybackCommandError.SourceFailure(trackId("rejected"), PlaybackSourceError.Io), diagnostic.result.error)
        assertSame(cause, diagnostic.result.cause)
        assertEquals("captured source diagnostic", diagnostic.result.originalMessage)
        fixture.submit(PlaybackCommand.SetRepeat(RepeatMode.All)).also { runCurrent(); assertSuccess(it.await()) }
        assertSame(diagnostic, withContext(fixture.main) { fixture.owner.lastFailure })
        fixture.close()
    }

    @Test
    fun `unexpected source failure is retained after the UI stops waiting`() = runTest {
        val fixture = Fixture(this)
        val release = CompletableDeferred<Unit>()
        val cause = IllegalStateException("unexpected source bug")
        fixture.resolver.read = { release.await(); throw cause }
        val command = PlaybackCommand.ReplaceQueue(request("broken"))
        val caller = fixture.submit(command)
        runCurrent()
        caller.cancel()
        release.complete(Unit)
        runCurrent()
        val diagnostic = withContext(fixture.main) { fixture.owner.lastFailure } as PlaybackCommandFailure.Unexpected
        assertEquals(command, diagnostic.command)
        assertOriginalThrowable(cause, diagnostic.cause)
        assertTrue(fixture.target.commits.isEmpty())
        fixture.close()
    }

    @Test
    fun `installation rejection retains its exact diagnostics and the fake engine keeps the old queue`() = runTest {
        val fixture = Fixture(this)
        fixture.replace("old").also { runCurrent(); assertSuccess(it.await()) }
        val old = fixture.target.current
        val cause = IllegalStateException("engine unavailable")
        val rejection = AppResult.Failure(PlaybackCommandError.StartNotAllowed, cause, "captured engine diagnostic")
        fixture.target.installResult = rejection
        val command = PlaybackCommand.ReplaceQueue(request("new"))
        val reply = fixture.submit(command)
        runCurrent()
        assertSame(rejection, reply.await())
        val diagnostic = withContext(fixture.main) { fixture.owner.lastFailure } as PlaybackCommandFailure.Rejected
        assertEquals(command, diagnostic.command)
        assertSame(rejection, diagnostic.result)
        assertSame(old, fixture.target.current)
        fixture.close()
    }

    @Test
    fun `close finishes pending reply promptly and late resolver cannot touch the released target`() = runTest {
        val fixture = Fixture(this)
        val release = CompletableDeferred<Unit>()
        fixture.resolver.read = { id -> withContext(NonCancellable) { release.await() }; AppResult.Success(resolved(id)) }
        val caller = fixture.replace("pending")
        runCurrent()
        fixture.close()
        fixture.target.released = true
        runCurrent()
        assertFailure(caller.await(), PlaybackCommandError.ConnectionUnavailable)
        release.complete(Unit)
        runCurrent()
        assertTrue(fixture.target.commits.isEmpty())
        assertEquals(0, fixture.allocations)
        assertFailure(fixture.owner.execute(PlaybackCommand.Next), PlaybackCommandError.ConnectionUnavailable)
        fixture.close()
    }

    @Test
    fun `parent service cancellation finishes pending reply and rejects further commands`() = runTest {
        val fixture = Fixture(this)
        val release = CompletableDeferred<Unit>()
        fixture.resolver.read = { id -> release.await(); AppResult.Success(resolved(id)) }
        val caller = fixture.replace("pending")
        runCurrent()
        fixture.serviceJob.cancel()
        runCurrent()
        expectThrown<CancellationException> { caller.await() }
        assertTrue(fixture.target.commits.isEmpty())
        assertFailure(fixture.owner.execute(PlaybackCommand.Next), PlaybackCommandError.ConnectionUnavailable)
        fixture.close()
    }

    @Test
    fun `parent cancellation finishes reply before noncancellable source cleanup returns`() = runTest {
        val fixture = Fixture(this)
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        fixture.resolver.read = {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    cleanupStarted.complete(Unit)
                    releaseCleanup.await()
                }
            }
        }
        try {
            val caller = fixture.replace("pending")
            runCurrent()
            assertEquals(listOf(trackId("pending")), fixture.resolver.calls)

            fixture.serviceJob.cancel()
            runCurrent()

            assertTrue("Source must still be waiting in cancellation cleanup", cleanupStarted.isCompleted)
            assertFalse(releaseCleanup.isCompleted)
            assertTrue("Owner reply must not wait for source cleanup", caller.isCompleted)
            expectThrown<CancellationException> { caller.await() }
            assertFailure(fixture.owner.execute(PlaybackCommand.Next), PlaybackCommandError.ConnectionUnavailable)
            assertTrue(fixture.target.commits.isEmpty())
            assertEquals(0, fixture.allocations)

            releaseCleanup.complete(Unit)
            runCurrent()
            assertTrue(fixture.target.commits.isEmpty())
        } finally {
            releaseCleanup.complete(Unit)
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `service cancellation before lazy preparation body starts still finishes the reply`() = runTest {
        val parent = SupervisorJob(backgroundScope.coroutineContext.job)
        val main = CancelOnSecondDispatch(StandardTestDispatcher(testScheduler)) { parent.cancel() }
        val fixture = Fixture(this, main, parent)
        val caller = fixture.replace("never")
        runCurrent()
        assertTrue(caller.isCompleted)
        expectThrown<CancellationException> { caller.await() }
        assertTrue(fixture.resolver.calls.isEmpty())
        assertTrue(fixture.target.commits.isEmpty())
        fixture.close()
    }

    @Test
    fun `parent cancellation during identity allocation prevents the final engine handoff`() = runTest {
        val fixture = Fixture(this)
        fixture.onEntryId = { fixture.serviceJob.cancel() }
        val caller = fixture.replace("new")
        runCurrent()
        assertTrue(caller.isCompleted)
        expectThrown<CancellationException> { caller.await() }
        assertTrue(fixture.target.commits.isEmpty())
        fixture.close()
    }

    @Test
    fun `already cancelled service rejects replacement without creating an unresolved lazy task`() = runTest {
        val fixture = Fixture(this)
        fixture.serviceJob.cancel()
        assertFailure(fixture.owner.execute(PlaybackCommand.ReplaceQueue(request("never"))), PlaybackCommandError.ConnectionUnavailable)
        assertTrue(fixture.resolver.calls.isEmpty())
        fixture.close()
    }

    @Test
    fun `unexpected resolver error preserves the original cause and a later replacement still works`() = runTest {
        val fixture = Fixture(this)
        val problem = IllegalStateException("broken source")
        fixture.resolver.read = { throw problem }
        val failed = backgroundScope.async {
            runCatching { fixture.owner.execute(PlaybackCommand.ReplaceQueue(request("bad"))) }
        }
        runCurrent()
        assertOriginalThrowable(problem, expectThrown<IllegalStateException> { failed.await().getOrThrow() })
        fixture.resolver.read = { AppResult.Success(resolved(it)) }
        fixture.replace("good").also { runCurrent(); assertSuccess(it.await()) }
        assertEquals(listOf(trackId("good")), fixture.target.current!!.prepared.request.trackIds)
        fixture.close()
    }

    @Test
    fun `unexpected installation error preserves the original cause and does not retain a pending request`() = runTest {
        val fixture = Fixture(this)
        val problem = IllegalArgumentException("broken engine")
        fixture.target.beforeInstall = { throw problem }
        val failed = backgroundScope.async {
            runCatching { fixture.owner.execute(PlaybackCommand.ReplaceQueue(request("bad"))) }
        }
        runCurrent()
        assertOriginalThrowable(problem, expectThrown<IllegalArgumentException> { failed.await().getOrThrow() })
        fixture.target.beforeInstall = {}
        fixture.replace("good").also { runCurrent(); assertSuccess(it.await()) }
        assertEquals(1, fixture.target.commits.size)
        fixture.close()
    }

    @Test
    fun `immediate target callback cannot reenter and overwrite an in-progress installation`() = runTest {
        val immediate = UnconfinedTestDispatcher(testScheduler)
        val fixture = Fixture(this, immediate, preparation = immediate)
        var reentered: Deferred<EmptyResult<PlaybackCommandError>>? = null
        fixture.target.beforeInstall = {
            reentered = backgroundScope.async(immediate, CoroutineStart.UNDISPATCHED) {
                fixture.owner.execute(PlaybackCommand.StopAndClear)
            }
            assertFalse(reentered.isCompleted)
        }
        assertSuccess(fixture.owner.execute(PlaybackCommand.ReplaceQueue(request("immediate"))))
        runCurrent()
        assertSuccess(reentered!!.await())
        assertEquals(listOf("install:start", "install:end", "command:StopAndClear"), fixture.target.events)
        fixture.target.beforeInstall = {}
        assertSuccess(fixture.owner.execute(PlaybackCommand.ReplaceQueue(request("following"))))
        fixture.close()
    }

    @Test
    fun `independent owners keep different sessions and cancelling one does not stop the other`() = runTest {
        val first = Fixture(this)
        val second = Fixture(this)
        val release = CompletableDeferred<Unit>()
        first.resolver.read = { id -> release.await(); AppResult.Success(resolved(id)) }
        second.resolver.read = { id -> release.await(); AppResult.Success(resolved(id)) }
        val firstReply = first.replace("a")
        val secondReply = second.replace("b")
        runCurrent()
        first.close()
        release.complete(Unit)
        runCurrent()
        assertFailure(firstReply.await(), PlaybackCommandError.ConnectionUnavailable)
        assertSuccess(secondReply.await())
        assertTrue(first.target.commits.isEmpty())
        assertEquals(1, second.target.commits.size)
        assertFalse(first.identities.sessionId == second.identities.sessionId)
        second.close()
    }

    private class Fixture(
        private val testScope: TestScope,
        val main: CoroutineDispatcher = StandardTestDispatcher(testScope.testScheduler, "owner"),
        val serviceJob: Job = SupervisorJob(testScope.backgroundScope.coroutineContext.job),
        preparation: CoroutineDispatcher = StandardTestDispatcher(testScope.testScheduler, "preparation"),
    ) {
        val resolver = FakeResolver()
        val target = FakeTarget()
        var allocations = 0
        var onEntryId: () -> Unit = {}
        val identities = PlaybackIdentities(entryId = {
            onEntryId()
            QueueEntryId("entry-${++allocations}")
        })
        val owner = PlaybackQueueOwner(
            CoroutineScope(testScope.backgroundScope.coroutineContext + serviceJob),
            main,
            QueuePreparer(resolver, preparation),
            target,
            identities,
        )

        fun submit(command: PlaybackCommand) = testScope.backgroundScope.async { owner.execute(command) }

        fun replace(vararg values: String, dispatcher: CoroutineDispatcher? = null): Deferred<EmptyResult<PlaybackCommandError>> {
            val command = PlaybackCommand.ReplaceQueue(request(*values))
            return if (dispatcher == null) submit(command)
            else testScope.backgroundScope.async(dispatcher) { owner.execute(command) }
        }

        suspend fun close() {
            owner.close()
            serviceJob.cancel()
        }
    }

    private class FakeResolver : PlaybackSourceResolver {
        val calls = mutableListOf<TrackId>()
        var read: suspend (TrackId) -> AppResult<ResolvedPlaybackTrack, PlaybackSourceError> = { AppResult.Success(resolved(it)) }
        override suspend fun resolveTrack(id: TrackId): AppResult<ResolvedPlaybackTrack, PlaybackSourceError> {
            calls += id
            return read(id)
        }
    }

    private class FakeTarget : QueueCommitTarget {
        val commits = mutableListOf<QueueCommit>()
        val commands = mutableListOf<PlaybackCommand>()
        val events = mutableListOf<String>()
        val installModes = mutableListOf<Pair<RepeatMode, Boolean>>()
        var current: QueueCommit? = null
        var repeat = RepeatMode.Off
        var shuffle = false
        var released = false
        var beforeInstall: () -> Unit = {}
        var beforeExecute: () -> Unit = {}
        var installResult: EmptyResult<PlaybackCommandError> = AppResult.Success(Unit)
        var commandResult: EmptyResult<PlaybackCommandError> = AppResult.Success(Unit)

        override fun install(commit: QueueCommit): EmptyResult<PlaybackCommandError> {
            check(!released) { "Released engine must not be called." }
            events += "install:start"
            beforeInstall()
            if (installResult is AppResult.Success) {
                commits += commit
                current = commit
                installModes += repeat to shuffle
            }
            events += "install:end"
            return installResult
        }

        override fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> {
            check(!released) { "Released engine must not be called." }
            beforeExecute()
            events += "command:${command::class.simpleName}"
            commands += command
            if (commandResult is AppResult.Success) {
                when (command) {
                    is PlaybackCommand.SetRepeat -> repeat = command.mode
                    is PlaybackCommand.SetShuffle -> shuffle = command.enabled
                    PlaybackCommand.StopAndClear -> current = null
                    else -> Unit
                }
            }
            return commandResult
        }
    }

    private class CancelOnSecondDispatch(
        private val delegate: CoroutineDispatcher,
        private val cancelService: () -> Unit,
    ) : CoroutineDispatcher() {
        private var dispatches = 0
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (++dispatches == 2) cancelService()
            delegate.dispatch(context, block)
        }
    }

    companion object {
        private fun trackId(value: String) = TrackId(value)
        private fun request(vararg values: String) = PlaybackQueueRequest.create(values.map(::trackId), 0)
        private fun resolved(id: TrackId) = ResolvedPlaybackTrack(
            MediaTrack(id, "Track ${id.value}", artist = null, album = null, durationMs = 10),
            "opaque:${id.value}",
        )
        private fun assertSuccess(result: EmptyResult<PlaybackCommandError>) = assertEquals(AppResult.Success(Unit), result)
        private fun assertFailure(result: EmptyResult<PlaybackCommandError>, expected: PlaybackCommandError) =
            assertEquals(expected, (result as AppResult.Failure).error)

        private fun assertOriginalThrowable(original: Throwable, actual: Throwable) {
            assertEquals(original.javaClass, actual.javaClass)
            assertEquals(original.message, actual.message)
            // Coroutine stacktrace recovery can copy an exception across suspend boundaries.
            assertTrue("Original exception must remain in the cause chain", generateSequence(actual) { it.cause }.any { it === original })
        }

        private suspend inline fun <reified T : Throwable> expectThrown(block: suspend () -> Any?): T {
            try { block() }
            catch (failure: Throwable) {
                if (failure is T) return failure
                throw failure
            }
            throw AssertionError("Expected ${T::class.simpleName}")
        }
    }
}
