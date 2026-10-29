package com.zaus.nullwave.core.playback.media3

import android.os.Bundle
import android.os.Process
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackSessionCallback(
    private val execute: suspend (PlaybackCommand) -> EmptyResult<PlaybackCommandError>,
    private val tickets: PlaybackCommandTickets,
    private val scope: CoroutineScope,
) : MediaSession.Callback {
    override fun onConnectAsync(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.ConnectionResult> {
        if (controller.uid != Process.myUid() && !controller.isTrusted) {
            return Futures.immediateFuture(MediaSession.ConnectionResult.reject())
        }
        val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
        if (controller.uid == Process.myUid()) {
            commands.add(SessionCommand(PlaybackSessionProtocol.EXECUTE_PRIVATE, Bundle.EMPTY))
        }
        // Legacy/platform clients can block this thread waiting for the result: never post to Main.
        return Futures.immediateFuture(
            MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands.build())
                .build(),
        )
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (controller.uid != Process.myUid()) return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
        if (customCommand.customAction != PlaybackSessionProtocol.EXECUTE_PRIVATE) {
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
        val id = args.getString(PlaybackSessionProtocol.TICKET_ID)
        val ticket = id?.let(tickets::take)
            ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
        val future = scope.serviceFuture {
            try {
                val result = execute(ticket.command)
                ticket.result.complete(result)
                SessionResult(if (result is AppResult.Success) SessionResult.RESULT_SUCCESS else SessionError.ERROR_UNKNOWN)
            } catch (cancelled: CancellationException) {
                ticket.result.complete(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
                SessionResult(SessionError.ERROR_SESSION_DISCONNECTED)
            } catch (failure: Throwable) {
                ticket.result.completeExceptionally(failure)
                throw failure
            }
        }
        future.addListener({
            if (!ticket.result.isCompleted) ticket.result.complete(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
        return future
    }
}
