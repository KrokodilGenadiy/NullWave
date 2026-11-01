package com.zaus.nullwave.feature.player.presentation

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackProblem
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertTrue

/** Fresh per test; ViewModelStore owns only the UI instances, while replies belong to the fake. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PlayerFixture {
    val dispatcher = StandardTestDispatcher()
    val playback = ControlledPlayerPlaybackController()
    val store = ViewModelStore()
    private var nextKey = 0
    val track = MediaTrack(TrackId("primary:7"), "Night Drive", "Artist", "Album", 180_000)

    fun setUp() = Dispatchers.setMain(dispatcher)

    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    fun createVm(): PlayerViewModel = PlayerViewModel(playback).also { store.put("player-${nextKey++}", it) }

    fun TestScope.bind(vm: PlayerViewModel): Job =
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }.also { runCurrent() }

    suspend fun TestScope.send(vm: PlayerViewModel, intent: PlayerIntent) {
        vm.uiApi().onIntent(intent)
        runCurrent()
    }

    fun TestScope.control(value: PlaybackConnection) {
        playback.states.value = value
        runCurrent()
    }

    fun TestScope.progress(value: PlaybackProgress?) {
        playback.progresses.value = value
        runCurrent()
    }

    fun TestScope.respond(result: EmptyResult<PlaybackCommandError>) {
        assertTrue(playback.calls.last().reply.complete(result))
        runCurrent()
    }

    fun TestScope.reconnectResult(result: EmptyResult<PlaybackConnectionError>) {
        assertTrue(playback.reconnectCalls.last().reply.complete(result))
        runCurrent()
    }

    fun TestScope.readyVm(value: PlaybackConnection.Connected = connected()): PlayerViewModel {
        playback.states.value = value
        return createVm().also { bind(it) }
    }

    fun connected(
        sessionId: String = "player-session",
        entryId: String = "entry",
        activation: Long = 0,
        positionRevision: Long = 0,
        revision: Long = 0,
        transport: PlaybackTransport = PlaybackTransport(phase = PlaybackPhase.Ready),
        capabilities: Set<PlaybackCapability> = PlaybackCapability.entries.filterTo(mutableSetOf()) {
            it != PlaybackCapability.Retry || transport.problem != null
        },
    ): PlaybackConnection.Connected {
        val session = PlaybackSessionId(sessionId)
        val entry = QueueEntry(QueueEntryId(entryId), track)
        val token = PlaybackItemToken(session, entry.entryId, activation)
        return PlaybackConnection.Connected(PlaybackSnapshot.create(
            sessionId = session,
            revision = revision,
            queue = PlaybackQueue.create(entries = listOf(entry), current = token),
            transport = transport,
            positionRevision = positionRevision,
            capabilities = capabilities,
        ))
    }

    fun problemSnapshot(
        code: PlaybackProblemCode = PlaybackProblemCode.DecoderFailure,
        id: String = "problem",
        sessionId: String = "player-session",
        activation: Long = 0,
        targeted: Boolean = true,
    ): PlaybackConnection.Connected {
        val target = PlaybackItemToken(PlaybackSessionId(sessionId), QueueEntryId("entry"), activation)
        return connected(
            sessionId = sessionId,
            activation = activation,
            transport = PlaybackTransport(problem = PlaybackProblem(PlaybackProblemId(id), code, target.takeIf { targeted })),
        )
    }

    fun anchor(
        snapshot: PlaybackSnapshot,
        positionMs: Long = 1_000,
        durationMs: Long? = 10_000,
        sampledAtMs: Long = 100,
        advancing: Boolean = false,
    ): PlaybackProgress = PlaybackProgress(
        target = checkNotNull(snapshot.queue.current),
        positionRevision = snapshot.positionRevision,
        positionMs = positionMs,
        durationMs = durationMs,
        bufferedPositionMs = null,
        sampledAtMs = sampledAtMs,
        advancing = advancing,
    )
}
