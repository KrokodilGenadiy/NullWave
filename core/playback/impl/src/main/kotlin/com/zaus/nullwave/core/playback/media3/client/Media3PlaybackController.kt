package com.zaus.nullwave.core.playback.media3.client

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.media3.service.NullWavePlaybackService
import com.zaus.nullwave.core.playback.media3.session.PlaybackCommandTickets
import com.zaus.nullwave.core.playback.media3.session.PlaybackSessionProtocol
import com.zaus.nullwave.core.playback.media3.state.PlaybackServiceRegistry
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** App-scoped facade. Construction is inert; observation, reconnect and commands acquire UI leases. */
@UnstableApi
internal class Media3PlaybackController(
    context: Context,
    main: CoroutineDispatcher,
    registry: PlaybackServiceRegistry,
    tickets: PlaybackCommandTickets,
) : PlaybackController by SharedPlaybackClient(main, registry, Media3Connector(context.applicationContext, main, tickets))

@UnstableApi
private class Media3Connector(
    private val context: Context,
    main: CoroutineDispatcher,
    private val tickets: PlaybackCommandTickets,
) : PlaybackConnector {
    private val cleanupScope = CoroutineScope(SupervisorJob() + main)
    private val executor = Executor { command -> cleanupScope.launch { command.run() } }

    override suspend fun connect(onDisconnected: () -> Unit): PlaybackClientConnection {
        val token = SessionToken(context, ComponentName(context, NullWavePlaybackService::class.java))
        val connecting = MediaController.Builder(context, token)
            .setApplicationLooper(Looper.getMainLooper())
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) { onDisconnected() }
            })
            .buildAsync()
        return awaitPlaybackConnection(connecting, MediaController::releaseFuture) { controller ->
            val id = controller.sessionExtras.getString(PlaybackSessionProtocol.SESSION_ID)?.takeIf(String::isNotBlank)
                ?: throw PlaybackConnectException(PlaybackConnectionError.ConnectionFailed)
            ConnectedController(controller, PlaybackSessionId(id), tickets, cleanupScope, executor)
        }
    }
}

@UnstableApi
private class ConnectedController(
    private val controller: MediaController,
    override val sessionId: PlaybackSessionId,
    private val tickets: PlaybackCommandTickets,
    private val cleanupScope: CoroutineScope,
    private val executor: Executor,
) : PlaybackClientConnection {
    private var closed = false

    override suspend fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> = dispatchThenAwaitPlayback(
        isConnected = { !closed && controller.isConnected },
        dispatch = { dispatch(command) },
        awaitReply = { (ticket, future) -> awaitReply(ticket, future) },
    )

    private fun dispatch(command: PlaybackCommand): AppResult<SentCommand, PlaybackCommandError> {
        if (closed || !controller.isConnected) return AppResult.Failure(PlaybackCommandError.ConnectionUnavailable)
        // Even an unavailable Pause/Next reaches owner admission to revoke an older replacement.
        val sessionCommand = SessionCommand(PlaybackSessionProtocol.EXECUTE_PRIVATE, Bundle.EMPTY)
        if (!controller.availableSessionCommands.contains(sessionCommand)) {
            return AppResult.Failure(PlaybackCommandError.CommandUnavailable)
        }
        val ticket = tickets.stage(command) ?: return AppResult.Failure(PlaybackCommandError.CommandUnavailable)
        val future = try {
            controller.sendCustomCommand(
                sessionCommand,
                Bundle().apply { putString(PlaybackSessionProtocol.TICKET_ID, ticket.id) },
            )
        } catch (failure: Throwable) {
            tickets.discard(ticket.id)
            throw failure
        }
        // These bounded cleanup jobs survive UI cancellation. They never sample progress or own audio.
        val expiry = cleanupScope.launch {
            delay(PlaybackSessionProtocol.COMMAND_TIMEOUT_MS)
            tickets.expire(ticket.id)
        }
        future.addListener({
            expiry.cancel()
            tickets.discard(ticket.id)
        }, executor)
        return AppResult.Success(SentCommand(ticket, future))
    }

    private suspend fun awaitReply(
        ticket: PlaybackCommandTickets.Ticket,
        future: ListenableFuture<SessionResult>,
    ): EmptyResult<PlaybackCommandError> {
        val response = try {
            withTimeout(PlaybackSessionProtocol.COMMAND_TIMEOUT_MS) { future.awaitPlaybackResult() }
        } catch (failure: TimeoutCancellationException) {
            // A service may already own the request: timing out this wait is not rollback.
            return AppResult.Failure(PlaybackCommandError.ConnectionUnavailable, failure)
        }
        // Same-process ticket preserves original diagnostics without serializing Throwable in a Bundle.
        if (ticket.result.isCompleted) return ticket.result.await()
        return when (response.resultCode) {
            SessionError.ERROR_BAD_VALUE -> AppResult.Failure(PlaybackCommandError.InvalidRequest)
            SessionError.ERROR_NOT_SUPPORTED, SessionError.ERROR_PERMISSION_DENIED ->
                AppResult.Failure(PlaybackCommandError.CommandUnavailable)
            // A successful transport ack without its owner reply is not a confirmed queue install.
            else -> AppResult.Failure(PlaybackCommandError.ConnectionUnavailable)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        controller.release()
    }

    private data class SentCommand(
        val ticket: PlaybackCommandTickets.Ticket,
        val future: ListenableFuture<SessionResult>,
    )
}
