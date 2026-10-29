package com.zaus.nullwave.core.playback.media3.engine

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.FakePlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.playback.media3.service.PlaybackStartGuard
import com.zaus.nullwave.core.playback.media3.state.PlaybackServiceRegistry
import com.zaus.nullwave.core.playback.preparation.PreparedQueue
import com.zaus.nullwave.core.playback.preparation.QueuePreparer
import com.zaus.nullwave.core.playback.runtime.PlaybackIdentities
import com.zaus.nullwave.core.playback.runtime.PlaybackQueueOwner
import com.zaus.nullwave.core.playback.runtime.QueueCommit
import com.zaus.nullwave.core.result.AppResult
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@UnstableApi
class OwnerSessionPlayerInstrumentedTest {
    @Test
    fun nativeSeekLeavingEndedWithPlaybackIntentAcquiresForegroundBeforeTheEngineCall(): Unit = runBlocking {
        withContext(Dispatchers.Main.immediate) {
            val fixture = SeekFixture(playRequested = true)
            try {
                val future = OwnerSessionPlayerTestAccess.seek(fixture.player, 123)
                assertTrue(future.isDone)
                future.get()

                assertEquals(listOf("foreground", "seek", "release"), fixture.events)
                assertEquals(Player.STATE_READY, fixture.raw.playbackState)
                assertEquals(123L, fixture.raw.currentPosition)
            } finally { fixture.close() }
        }
    }

    @Test
    fun nativeSeekWhilePausedDoesNotAcquireForeground(): Unit = runBlocking {
        withContext(Dispatchers.Main.immediate) {
            val fixture = SeekFixture(playRequested = false)
            try {
                val future = OwnerSessionPlayerTestAccess.seek(fixture.player, 123)
                assertTrue(future.isDone)
                future.get()

                assertEquals(listOf("seek"), fixture.events)
                assertEquals(123L, fixture.raw.currentPosition)
            } finally { fixture.close() }
        }
    }

    @Test
    fun rejectedNativeStartPreservesOriginalCauseAndExplicitDiagnosticMessage(): Unit = runBlocking {
        val serviceJob = SupervisorJob()
        try {
            withContext(Dispatchers.Main.immediate) {
                val raw = FakePlayer(Player.STATE_IDLE, false, emptyList(), 1f, 0)
                val registry = PlaybackServiceRegistry()
                val identities = PlaybackIdentities()
                val target = Media3QueueCommitTarget(raw, identities, registry, PlaybackClock { 100L })
                val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
                val source = object : PlaybackSourceResolver {
                    override suspend fun resolveTrack(id: TrackId): AppResult<ResolvedPlaybackTrack, PlaybackSourceError> =
                        error("Denied native start must not resolve a track")
                }
                val owner = PlaybackQueueOwner(
                    scope, Dispatchers.Main.immediate, QueuePreparer(source, Dispatchers.Default), target, identities,
                )
                val original = IllegalStateException("Platform cause")
                val suppressed = IllegalArgumentException("Additional diagnostic")
                original.addSuppressed(suppressed)
                var revocations = 0
                val guard = PlaybackStartGuard(
                    acquire = { AppResult.Failure(PlaybackCommandError.StartNotAllowed, original, "Explicit service detail") },
                    onDenied = { revocations++ },
                )
                val player = OwnerSessionPlayer(raw, owner, target, scope, guard)
                try {
                    val future = OwnerSessionPlayerTestAccess.setPlayWhenReady(player, true)
                    assertTrue("Immediate Main rejection must complete the native future", future.isDone)
                    val failure = try {
                        future.get()
                        error("Native foreground denial must fail the handler future")
                    } catch (error: ExecutionException) {
                        error.cause!!
                    }

                    assertTrue(failure is IllegalStateException)
                    assertSame(original, failure.cause)
                    assertTrue(failure.message!!.contains("Explicit service detail"))
                    assertSame(suppressed, failure.cause!!.suppressed.single())
                    assertEquals(1, revocations)
                } finally {
                    owner.close()
                    target.close()
                    player.release()
                }
            }
        } finally {
            serviceJob.cancelAndJoin()
        }
    }

    private class SeekFixture(playRequested: Boolean) {
        private val serviceJob = SupervisorJob()
        private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
        val events = mutableListOf<String>()
        val raw = FakePlayer(Player.STATE_IDLE, false, emptyList(), 1f, 0)
        private val observed = object : ForwardingPlayer(raw) {
            override fun seekTo(positionMs: Long) {
                events += "seek"
                super.seekTo(positionMs)
            }
        }
        private val identities = PlaybackIdentities()
        private val target = Media3QueueCommitTarget(observed, identities, PlaybackServiceRegistry(), PlaybackClock { 100L })
        private val source = object : PlaybackSourceResolver {
            override suspend fun resolveTrack(id: TrackId): AppResult<ResolvedPlaybackTrack, PlaybackSourceError> =
                error("A native seek uses the installed resource")
        }
        private val owner = PlaybackQueueOwner(
            scope, Dispatchers.Main.immediate, QueuePreparer(source, Dispatchers.Default), target, identities,
        )
        private val guard = PlaybackStartGuard(
            acquire = {
                events += "foreground"
                AppResult.Success(AutoCloseable { events += "release" })
            },
            onDenied = { error("Unexpected foreground denial") },
        )
        val player = OwnerSessionPlayer(observed, owner, target, scope, guard)

        init {
            val track = MediaTrack(TrackId("native-seek"), "Track", null, null, 10_000)
            val request = PlaybackQueueRequest.create(listOf(track.id), 0, playRequested = playRequested)
            val prepared = PreparedQueue.create(request, listOf(ResolvedPlaybackTrack(track, "content://test/native-seek")))
            assertEquals(AppResult.Success(Unit), target.install(QueueCommit.create(prepared, identities)))
            raw.setPosition(5_000)
            raw.setPlaybackState(if (playRequested) Player.STATE_ENDED else Player.STATE_READY)
            raw.addCommands(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            target.publish()
            events.clear()
        }

        suspend fun close() {
            owner.close()
            target.close()
            player.release()
            serviceJob.cancelAndJoin()
        }
    }
}
