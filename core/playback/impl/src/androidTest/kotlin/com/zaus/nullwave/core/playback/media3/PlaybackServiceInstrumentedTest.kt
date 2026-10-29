package com.zaus.nullwave.core.playback.media3

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.playback.media3.testing.PlaybackTestActivity
import com.zaus.nullwave.core.playback.media3.testing.PlaybackTestApplication
import com.zaus.nullwave.core.playback.media3.testing.withPlaybackTestCleanup
import com.zaus.nullwave.core.result.AppResult
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

/** Real service/MediaController/ExoPlayer over a silent local WAV; no device library permission needed. */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceInstrumentedTest {
    @Test
    fun portableSeekOutOfEndedReplaysInForegroundAfterTheActivityStops(): Unit = runBlocking {
        val app = application()
        val activity = ActivityScenario.launch(PlaybackTestActivity::class.java)
        val sourceFile = installSilentTrack(app)
        val controller = app.dependencies.controller()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val states = MutableStateFlow<PlaybackConnection>(PlaybackConnection.Disconnected)
        val observer = scope.launch { controller.observeState().collect { states.value = it } }
        withPlaybackTestCleanup(
            cleanup = listOf(
                { observer.cancelAndJoin() },
                { scope.cancel() },
                { stopServiceAndAwaitDetach(app) },
                { activity.close() },
                { sourceFile.delete() },
                { app.tracks.clear() },
            ),
        ) {
            withTimeout(20_000.milliseconds) {
                states.connected { true }
                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.ReplaceQueue(PlaybackQueueRequest.create(listOf(ID), 0))))
                val playing = states.connected { it.transport.isPlaying }
                val token = requireNotNull(playing.queue.current)
                val progress = controller.observeProgress().filterNotNull().first { it.durationMs != null }
                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.SeekTo(token, progress.durationMs!!)))
                val ended = states.connected { it.transport.phase == PlaybackPhase.Ended }
                assertTrue("Ending does not clear native playback intent", ended.transport.playRequested)
                activity.moveToState(Lifecycle.State.CREATED)

                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.SeekTo(ended.queue.current!!, 0)))
                val replaying = states.connected { it.transport.isPlaying }
                assertEquals(playing.sessionId, replaying.sessionId)
                assertNotEquals(ended.queue.current, replaying.queue.current)
                while (!isPlaybackServiceForeground(app)) delay(50)
                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.StopAndClear))
            }
        }
    }

    @Test
    fun stoppedAndDestroyedServiceCannotInstallARequestAfterItsUiWaitWasCancelled(): Unit = runBlocking {
        val app = application()
        val activity = ActivityScenario.launch(PlaybackTestActivity::class.java)
        val sourceFile = installSilentTrack(app)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val sourceReturned = CompletableDeferred<Unit>()
        val resolved = app.tracks.getValue(ID)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        app.read = { _ ->
            entered.complete(Unit)
            try {
                withContext(NonCancellable) { release.await() }
                AppResult.Success(resolved)
            } finally { sourceReturned.complete(Unit) }
        }
        val caller = scope.async {
            app.dependencies.controller().execute(PlaybackCommand.ReplaceQueue(PlaybackQueueRequest.create(listOf(ID), 0)))
        }
        withPlaybackTestCleanup(
            cleanup = listOf(
                { release.complete(Unit) },
                { caller.cancelAndJoin() },
                { scope.cancel() },
                { stopServiceAndAwaitDetach(app) },
                { if (entered.isCompleted) withTimeout(15_000) { sourceReturned.await() } },
                { app.read = null },
                { activity.close() },
                { sourceFile.delete() },
                { app.tracks.clear() },
            ),
        ) {
            var phase = "waiting for resolver entry"
            try {
                withTimeout(15_000.milliseconds) {
                    entered.await()
                    assertTrue(app.dependencies.registry.snapshot.value != null)
                    phase = "cancelling the UI waiter"
                    caller.cancelAndJoin()
                    // Media3 keeps its service binding while a released controller has pending commands.
                    // An explicit owner stop completes that reply without waiting for provider cleanup.
                    phase = "stopping the accepted preparation"
                    assertEquals(AppResult.Success(Unit), app.dependencies.controller().execute(PlaybackCommand.StopAndClear))
                    assertFalse("Stop must not wait for NonCancellable provider cleanup", sourceReturned.isCompleted)
                    phase = "waiting for service destruction after explicit stop"
                    withContext(Dispatchers.Main.immediate) { app.stopService(Intent(app, NullWavePlaybackService::class.java)) }
                    app.dependencies.registry.snapshot.first { it == null }
                    assertFalse("Provider must still be blocked when the service is destroyed", sourceReturned.isCompleted)
                    phase = "releasing the late provider after service destruction"
                    release.complete(Unit)
                    sourceReturned.await()
                    withContext(Dispatchers.Main.immediate) { assertEquals(null, app.dependencies.registry.snapshot.value) }
                }
            } catch (failure: TimeoutCancellationException) {
                throw AssertionError("Timed out $phase; service snapshot=${app.dependencies.registry.snapshot.value}", failure)
            }
        }
    }

    @Test
    fun privateReplacementIsAtomicAndConditionalSeekRejectsAnOldToken(): Unit = runBlocking {
        val app = application()
        val activity = ActivityScenario.launch(PlaybackTestActivity::class.java)
        val sourceFile = installSilentTrack(app)
        val controller = app.dependencies.controller()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val states = MutableStateFlow<PlaybackConnection>(PlaybackConnection.Disconnected)
        val observer = scope.launch { controller.observeState().collect { states.value = it } }
        withPlaybackTestCleanup(
            cleanup = listOf(
                { observer.cancelAndJoin() },
                { scope.cancel() },
                { stopServiceAndAwaitDetach(app) },
                { activity.close() },
                { sourceFile.delete() },
                { app.tracks.clear() },
            ),
        ) {
            withTimeout(15_000.milliseconds) {
                states.connected { true }
                val request = PlaybackQueueRequest.create(listOf(ID, ID), 1, startPositionMs = 120, playRequested = false)
                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.ReplaceQueue(request)))
                val ready = states.connected { it.queue.entries.size == 2 && it.transport.phase == PlaybackPhase.Ready }
                val token = requireNotNull(ready.queue.current)
                assertEquals(2, ready.queue.entries.map { it.entryId }.toSet().size)
                assertEquals(ready.queue.entries[1].entryId, token.entryId)
                val failure = controller.execute(PlaybackCommand.ReplaceQueue(PlaybackQueueRequest.create(listOf(TrackId("missing")), 0)))
                assertEquals(PlaybackCommandError.SourceFailure(TrackId("missing"), PlaybackSourceError.NotFound), (failure as AppResult.Failure).error)
                assertEquals(token, states.value.let { (it as PlaybackConnection.Connected).snapshot.queue.current })

                val stale = token.copy(activation = token.activation + 100)
                assertEquals(PlaybackCommandError.StaleTarget, (controller.execute(PlaybackCommand.SeekTo(stale, 0)) as AppResult.Failure).error)
                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.SeekTo(token, 250)))
                val anchor = controller.observeProgress().filterNotNull().first { it.positionMs >= 250 }
                assertEquals(token, anchor.target)
                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.StopAndClear))
                states.connected { it.queue.entries.isEmpty() }
            }
        }
    }

    @Test
    fun losingUiLeasesAndForegroundActivityDoesNotReplaceThePlayingService(): Unit = runBlocking {
        val app = application()
        val activity = ActivityScenario.launch(PlaybackTestActivity::class.java)
        val sourceFile = installSilentTrack(app)
        val controller = app.dependencies.controller()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val states = MutableStateFlow<PlaybackConnection>(PlaybackConnection.Disconnected)
        val observer = scope.launch { controller.observeState().collect { states.value = it } }
        withPlaybackTestCleanup(
            cleanup = listOf(
                { observer.cancelAndJoin() },
                { scope.cancel() },
                { stopServiceAndAwaitDetach(app) },
                { activity.close() },
                { sourceFile.delete() },
                { app.tracks.clear() },
            ),
        ) {
            withTimeout(20_000.milliseconds) {
                states.connected { true }
                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.SetRepeat(RepeatMode.All)))
                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.ReplaceQueue(PlaybackQueueRequest.create(listOf(ID), 0))))
                val playing = states.connected { it.transport.isPlaying }
                val first = controller.observeProgress().filterNotNull().first { it.advancing }
                observer.cancelAndJoin()
                activity.moveToState(Lifecycle.State.CREATED)

                val reconnected = controller.observeState().filterIsInstance<PlaybackConnection.Connected>().first { it.snapshot.transport.isPlaying }.snapshot
                assertEquals(playing.sessionId, reconnected.sessionId)
                val next = controller.observeProgress().filterNotNull().first { it.sampledAtMs > first.sampledAtMs }
                assertTrue(next.advancing)
                assertEquals(playing.sessionId, next.target.sessionId)
                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.SetPlayRequested(false)))
                controller.observeState().filterIsInstance<PlaybackConnection.Connected>().first { !it.snapshot.transport.playRequested }
                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.StopAndClear))
            }
        }
    }

    @Test
    fun delayedAutoplayStillStartsInForegroundServiceAfterActivityStops(): Unit = runBlocking {
        val app = application()
        val activity = ActivityScenario.launch(PlaybackTestActivity::class.java)
        val sourceFile = installSilentTrack(app)
        val controller = app.dependencies.controller()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val states = MutableStateFlow<PlaybackConnection>(PlaybackConnection.Disconnected)
        val observer = scope.launch { controller.observeState().collect { states.value = it } }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val sourceReturned = CompletableDeferred<Unit>()
        val resolved = app.tracks.getValue(ID)
        app.read = { _ ->
            entered.complete(Unit)
            try {
                withContext(NonCancellable) { release.await() }
                AppResult.Success(resolved)
            } finally { sourceReturned.complete(Unit) }
        }
        val caller = scope.async {
            controller.execute(PlaybackCommand.ReplaceQueue(PlaybackQueueRequest.create(listOf(ID), 0)))
        }
        withPlaybackTestCleanup(
            cleanup = listOf(
                { release.complete(Unit) },
                { caller.cancelAndJoin() },
                { observer.cancelAndJoin() },
                { scope.cancel() },
                { stopServiceAndAwaitDetach(app) },
                { if (entered.isCompleted) withTimeout(15_000) { sourceReturned.await() } },
                { app.read = null },
                { activity.close() },
                { sourceFile.delete() },
                { app.tracks.clear() },
            ),
        ) {
            withTimeout(20_000.milliseconds) {
                entered.await()
                val initial = states.connected { it.queue.entries.isEmpty() }
                activity.moveToState(Lifecycle.State.CREATED)
                release.complete(Unit)

                assertEquals(AppResult.Success(Unit), caller.await())
                val playing = states.connected { it.transport.phase == PlaybackPhase.Ready && it.transport.isPlaying }
                assertEquals(initial.sessionId, playing.sessionId)
                while (!isPlaybackServiceForeground(app)) delay(50)
                assertEquals(AppResult.Success(Unit), controller.execute(PlaybackCommand.StopAndClear))
            }
        }
    }

    @Test
    fun pauseWithEmptyQueueRevokesPendingAutoplayEvenWhenProviderReturnsAfterCancellation(): Unit = runBlocking {
        val app = application()
        val activity = ActivityScenario.launch(PlaybackTestActivity::class.java)
        val sourceFile = installSilentTrack(app)
        val controller = app.dependencies.controller()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val states = MutableStateFlow<PlaybackConnection>(PlaybackConnection.Disconnected)
        val observer = scope.launch { controller.observeState().collect { states.value = it } }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val sourceReturned = CompletableDeferred<Unit>()
        val resolved = app.tracks.getValue(ID)
        app.read = { _ ->
            entered.complete(Unit)
            try {
                withContext(NonCancellable) { release.await() }
                AppResult.Success(resolved)
            } finally { sourceReturned.complete(Unit) }
        }
        val caller = scope.async {
            controller.execute(PlaybackCommand.ReplaceQueue(PlaybackQueueRequest.create(listOf(ID), 0)))
        }
        withPlaybackTestCleanup(
            cleanup = listOf(
                { release.complete(Unit) },
                { caller.cancelAndJoin() },
                { observer.cancelAndJoin() },
                { scope.cancel() },
                { stopServiceAndAwaitDetach(app) },
                { if (entered.isCompleted) withTimeout(15_000) { sourceReturned.await() } },
                { app.read = null },
                { activity.close() },
                { sourceFile.delete() },
                { app.tracks.clear() },
            ),
        ) {
            withTimeout(15_000.milliseconds) {
                entered.await()
                states.connected { it.queue.entries.isEmpty() }
                // The engine has no current item to pause, but admission must still revoke autoplay.
                val pause = controller.execute(PlaybackCommand.SetPlayRequested(false))
                assertEquals(PlaybackCommandError.CommandUnavailable, (pause as AppResult.Failure).error)
                assertEquals(PlaybackCommandError.Superseded, (caller.await() as AppResult.Failure).error)
                release.complete(Unit)
                sourceReturned.await()
                withContext(Dispatchers.Main.immediate) {
                    val snapshot = requireNotNull(app.dependencies.registry.snapshot.value)
                    assertTrue(snapshot.queue.entries.isEmpty())
                    assertTrue(!snapshot.transport.playRequested && !snapshot.transport.isPlaying)
                }
            }
        }
    }

    private suspend fun MutableStateFlow<PlaybackConnection>.connected(predicate: (PlaybackSnapshot) -> Boolean): PlaybackSnapshot =
        filterIsInstance<PlaybackConnection.Connected>().first { predicate(it.snapshot) }.snapshot

    private fun application() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PlaybackTestApplication

    private suspend fun stopServiceAndAwaitDetach(app: PlaybackTestApplication) {
        withContext(Dispatchers.Main.immediate) { app.stopService(Intent(app, NullWavePlaybackService::class.java)) }
        withTimeout(15_000.milliseconds) { app.dependencies.registry.snapshot.first { it == null } }
    }

    @Suppress("DEPRECATION") // Android still exposes the calling app's own foreground-service state.
    private fun isPlaybackServiceForeground(app: PlaybackTestApplication): Boolean =
        app.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE).any {
            it.service.className == NullWavePlaybackService::class.java.name && it.foreground
        }

    private fun installSilentTrack(app: PlaybackTestApplication): File {
        val file = File(app.cacheDir, "playback-integration.wav")
        val pcmBytes = 8_000 * 4 * 2
        val data = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcmBytes)
        data.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        data.putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
        data.put("data".toByteArray(Charsets.US_ASCII)).putInt(pcmBytes)
        file.writeBytes(data.array())
        app.tracks[ID] = ResolvedPlaybackTrack(MediaTrack(ID, "Silent fixture", null, null, 4_000), Uri.fromFile(file).toString())
        return file
    }

    private companion object { val ID = TrackId("test:local-wave") }
}
