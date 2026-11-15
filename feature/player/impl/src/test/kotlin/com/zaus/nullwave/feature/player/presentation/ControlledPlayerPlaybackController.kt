package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** The fake service owns its replies; cancelling a UI waiter cannot cancel accepted work. */
internal class ControlledPlayerPlaybackController : PlaybackController {
    val states = MutableStateFlow<PlaybackConnection>(PlaybackConnection.Disconnected)
    val progresses = MutableStateFlow<PlaybackProgress?>(null)
    val calls = mutableListOf<Call>()
    val reconnectCalls = mutableListOf<ReconnectCall>()
    var beforeStateCollection: suspend (Int) -> Unit = {}
    var beforeProgressCollection: suspend (Int) -> Unit = {}
    var stateObservations = 0
        private set
    var progressObservations = 0
        private set
    var activeStateCollectors = 0
        private set
    var activeProgressCollectors = 0
        private set

    override val publishedSnapshot: PlaybackSnapshot?
        get() = (states.value as? PlaybackConnection.Connected)?.snapshot

    override fun observeState(): Flow<PlaybackConnection> = flow {
        val observation = ++stateObservations
        activeStateCollectors++
        try {
            beforeStateCollection(observation)
            emitAll(states)
        } finally { activeStateCollectors-- }
    }

    override fun observeProgress(): Flow<PlaybackProgress?> = flow {
        val observation = ++progressObservations
        activeProgressCollectors++
        try {
            beforeProgressCollection(observation)
            emitAll(progresses)
        } finally { activeProgressCollectors-- }
    }

    override suspend fun execute(
        command: PlaybackCommand,
        beforeDispatch: (PlaybackSnapshot?) -> Unit,
    ): EmptyResult<PlaybackCommandError> {
        beforeDispatch(publishedSnapshot)
        val call = Call(command).also(calls::add)
        try { return call.reply.await() }
        catch (failure: CancellationException) {
            call.waiterCancelled = true
            throw failure
        }
    }

    override suspend fun reconnect(): EmptyResult<PlaybackConnectionError> {
        val call = ReconnectCall().also(reconnectCalls::add)
        try { return call.reply.await() }
        catch (failure: CancellationException) {
            call.waiterCancelled = true
            throw failure
        }
    }

    class Call(val command: PlaybackCommand) {
        val reply = CompletableDeferred<EmptyResult<PlaybackCommandError>>()
        var waiterCancelled = false
    }

    class ReconnectCall {
        val reply = CompletableDeferred<EmptyResult<PlaybackConnectionError>>()
        var waiterCancelled = false
    }
}
