package com.zaus.nullwave.feature.search.testing

import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Exercises the real Root/VM command path without connecting to a service or playing audio. */
internal class RecordingSearchPlaybackController : PlaybackController {
    val states = MutableStateFlow<PlaybackConnection>(PlaybackConnection.Connected(
        PlaybackSnapshot.create(PlaybackSessionId("search-ui-test-session")),
    ))
    private val recorded = CopyOnWriteArrayList<Call>()
    val calls: List<Call> get() = recorded.toList()

    override val publishedSnapshot: PlaybackSnapshot?
        get() = (states.value as? PlaybackConnection.Connected)?.snapshot

    override fun observeState(): Flow<PlaybackConnection> = states
    override fun observeProgress(): Flow<PlaybackProgress?> = flowOf(null)

    override suspend fun reconnect(): EmptyResult<PlaybackConnectionError> = AppResult.Success(Unit)

    override suspend fun execute(
        command: PlaybackCommand,
        beforeDispatch: (PlaybackSnapshot?) -> Unit,
    ): EmptyResult<PlaybackCommandError> {
        beforeDispatch(publishedSnapshot)
        val call = Call(command).also(recorded::add)
        return call.reply.await()
    }

    /** A UI waiter does not own the accepted reply, just as it does not own service work. */
    class Call(val command: PlaybackCommand) {
        val reply = CompletableDeferred<EmptyResult<PlaybackCommandError>>()
    }
}
