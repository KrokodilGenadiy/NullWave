package com.zaus.nullwave.feature.search.presentation

import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.result.EmptyResult
import com.zaus.nullwave.core.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Accepted fake commands own their replies; cancelling a UI waiter cannot cancel that work. */
internal class ControlledSearchPlaybackController : PlaybackController {
    val calls = mutableListOf<Call>()
    val states = MutableStateFlow<PlaybackConnection>(PlaybackConnection.Connected(
        PlaybackSnapshot.create(PlaybackSessionId("search-test-session")),
    ))
    var beforeStateCollection: suspend (Int) -> Unit = {}
    var beforeCommandDispatch: suspend () -> Unit = {}
    var connectionFailure: AppResult.Failure<PlaybackCommandError>? = null
    var stateObservations = 0
        private set
    var activeStateCollectors = 0
        private set
    var progressObservations = 0
        private set

    override fun observeState(): Flow<PlaybackConnection> {
        val observation = ++stateObservations
        return flow {
            activeStateCollectors++
            try {
                beforeStateCollection(observation)
                emitAll(states)
            }
            finally { activeStateCollectors-- }
        }
    }

    override fun observeProgress(): Flow<PlaybackProgress?> {
        progressObservations++
        return emptyFlow()
    }

    override suspend fun execute(
        command: PlaybackCommand,
        beforeDispatch: (PlaybackSnapshot?) -> Unit,
    ): EmptyResult<PlaybackCommandError> {
        beforeCommandDispatch()
        connectionFailure?.let { return it }
        beforeDispatch((states.value as? PlaybackConnection.Connected)?.snapshot)
        val call = Call(command).also(calls::add)
        try {
            return call.reply.await()
        } catch (failure: CancellationException) {
            call.waiterCancelled = true
            throw failure
        }
    }

    class Call(val command: PlaybackCommand) {
        val reply = CompletableDeferred<EmptyResult<PlaybackCommandError>>()
        var waiterCancelled = false
    }
}
