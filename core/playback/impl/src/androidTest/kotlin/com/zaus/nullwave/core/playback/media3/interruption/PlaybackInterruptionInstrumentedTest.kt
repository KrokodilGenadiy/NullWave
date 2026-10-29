package com.zaus.nullwave.core.playback.media3.interruption

import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.playback.preparation.QueuePreparer
import com.zaus.nullwave.core.playback.runtime.PlaybackQueueOwner
import com.zaus.nullwave.core.playback.runtime.QueueCommit
import com.zaus.nullwave.core.playback.runtime.QueueCommitTarget
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackInterruptionInstrumentedTest {
    @Test
    fun permanentFocusLossRevokesPendingPreparationBeforeItsLateReturn() = runTest {
        val fixture = Fixture()
        try {
            val pending = fixture.delayReplacement(this, "a")
            withContext(Dispatchers.Main.immediate) {
                fixture.listener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            }
            assertFalse("Wrapper-owned user pause must not be a second interruption", pending.reply.isCompleted)

            withContext(Dispatchers.Main.immediate) {
                fixture.listener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            }

            pending.assertSupersededBeforeRelease()
        } finally { fixture.close() }
        assertTrue(fixture.installed.isEmpty())
    }

    @Test
    fun transientSuppressionRevokesReplacementWithoutPausingTheInstalledTrack() = runTest {
        val fixture = Fixture()
        try {
            assertEquals(AppResult.Success(Unit), fixture.owner.execute(replacement("installed")))
            val pending = fixture.delayReplacement(this, "replacement")
            withContext(Dispatchers.Main.immediate) {
                assertTrue(fixture.playRequested)
                fixture.listener.onPlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
            }

            pending.assertSupersededBeforeRelease()
            withContext(Dispatchers.Main.immediate) {
                assertTrue("The installed track retains its native playback intent", fixture.playRequested)
                assertEquals(0, fixture.pauses)
                assertEquals(listOf(TrackId("installed")), fixture.installed)
            }
        } finally { fixture.close() }
        assertEquals(listOf(TrackId("installed")), fixture.installed)
    }

    @Test
    fun unrelatedSuppressionDoesNotCancelPendingReplacement() = runTest {
        val fixture = Fixture()
        try {
            val pending = fixture.delayReplacement(this, "a")
            withContext(Dispatchers.Main.immediate) {
                fixture.listener.onPlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_NONE)
                fixture.listener.onPlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT)
                assertEquals(0, fixture.pauses)
            }
            assertFalse(pending.reply.isCompleted)

            pending.gate.release.complete(Unit)
            assertEquals(AppResult.Success(Unit), pending.reply.await())
            assertEquals(listOf(TrackId("a")), fixture.installed)
        } finally { fixture.close() }
    }

    @Test
    fun everyDeliveredNoisyEventRevokesNewPreparationEvenWhenAlreadyPaused() = runTest {
        val fixture = Fixture()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receiver = withContext(Dispatchers.Main.immediate) { PlaybackNoisyReceiver(context, fixture.listener::onAudioBecomingNoisy) }
        try {
            val first = fixture.delayReplacement(this, "a")
            withContext(Dispatchers.Main.immediate) {
                receiver.onReceive(context, Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            }
            first.assertSupersededBeforeRelease()
            withContext(Dispatchers.Main.immediate) { assertFalse(fixture.playRequested) }

            // Deliver a second system event without an intervening Player state change.
            val second = fixture.delayReplacement(this, "b")
            withContext(Dispatchers.Main.immediate) {
                receiver.onReceive(context, Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                assertFalse(fixture.playRequested)
                assertEquals(2, fixture.pauses)
            }
            second.assertSupersededBeforeRelease()
        } finally {
            withContext(Dispatchers.Main.immediate) { receiver.close() }
            fixture.close()
        }
        assertTrue("Neither late provider may install after its noisy event", fixture.installed.isEmpty())
    }

    @Test
    fun noisyReceiverIgnoresUnrelatedActionsAndCallbacksAfterClose() = runTest {
        val fixture = Fixture()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receiver = withContext(Dispatchers.Main.immediate) { PlaybackNoisyReceiver(context, fixture.listener::onAudioBecomingNoisy) }
        try {
            val pending = fixture.delayReplacement(this, "a")
            withContext(Dispatchers.Main.immediate) {
                receiver.onReceive(context, Intent("com.zaus.nullwave.UNRELATED"))
                receiver.close()
                receiver.close()
                receiver.onReceive(context, Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                assertEquals(0, fixture.pauses)
            }
            assertFalse(pending.reply.isCompleted)

            pending.gate.release.complete(Unit)
            assertEquals(AppResult.Success(Unit), pending.reply.await())
        } finally {
            withContext(Dispatchers.Main.immediate) { receiver.close() }
            fixture.close()
        }
        assertEquals(listOf(TrackId("a")), fixture.installed)
    }

    private class Fixture {
        private val serviceJob = SupervisorJob()
        private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
        private val gates = ConcurrentHashMap<TrackId, Gate>()
        val installed = mutableListOf<TrackId>()
        var playRequested = false
            private set
        var pauses = 0
            private set
        private val source = object : PlaybackSourceResolver {
            override suspend fun resolveTrack(id: TrackId): AppResult<ResolvedPlaybackTrack, PlaybackSourceError> {
                gates[id]?.let { gate ->
                    gate.entered.complete(Unit)
                    withContext(NonCancellable) { gate.release.await() }
                }
                return AppResult.Success(ResolvedPlaybackTrack(MediaTrack(id, "Track", null, null, null), "content://test/${id.value}"))
            }
        }
        private val target = object : QueueCommitTarget {
            override fun install(commit: QueueCommit): EmptyResult<PlaybackCommandError> {
                installed += commit.entries.single().track.id
                playRequested = commit.prepared.request.playRequested
                return AppResult.Success(Unit)
            }
            override fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> = AppResult.Success(Unit)
        }
        val owner = PlaybackQueueOwner(serviceScope, Dispatchers.Main.immediate, QueuePreparer(source, Dispatchers.Default), target)
        val listener = PlaybackInterruptionListener(owner, serviceScope) { pauses++; playRequested = false }

        suspend fun delayReplacement(callerScope: CoroutineScope, value: String): Pending {
            val gate = Gate()
            gates[TrackId(value)] = gate
            val reply = callerScope.async(Dispatchers.Default) { owner.execute(replacement(value)) }
            gate.entered.await()
            return Pending(reply, gate)
        }

        suspend fun close() = withContext(NonCancellable) {
            try { withContext(Dispatchers.Main.immediate) { owner.close() } }
            finally {
                gates.values.forEach { it.release.complete(Unit) }
                serviceJob.cancelAndJoin()
            }
        }
    }

    private class Gate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    private class Pending(val reply: Deferred<EmptyResult<PlaybackCommandError>>, val gate: Gate) {
        suspend fun assertSupersededBeforeRelease() {
            assertEquals(PlaybackCommandError.Superseded, (reply.await() as AppResult.Failure).error)
            assertFalse("Owner reply must not wait for NonCancellable provider cleanup", gate.release.isCompleted)
        }
    }

    private companion object {
        fun replacement(value: String) = PlaybackCommand.ReplaceQueue(PlaybackQueueRequest.create(listOf(TrackId(value)), 0))
    }
}
