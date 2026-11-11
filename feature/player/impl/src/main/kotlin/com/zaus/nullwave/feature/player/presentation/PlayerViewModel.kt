package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.dsl.OperationKey
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.result.AppResult

/** Shared Player presentation. Binding owns observations; the service owns accepted audio work. */
internal class PlayerViewModel(private val playbackController: PlaybackController) :
    BaseMviViewModel<PlayerState, PlayerIntent, Nothing>(PlayerState(), sourceStopTimeoutMillis = 0) {

    override fun MviDefinition<PlayerState, PlayerIntent, Nothing>.define() {
        source("player.playback", flow = playbackController::observePlayerPlayback) { update ->
            copy(
                connection = update.connection,
                lastKnownSnapshot = (update.connection as? PlaybackConnection.Connected)?.snapshot ?: lastKnownSnapshot,
                progress = update.progress,
            )
        }
        val request = idle<PlayerRequest, UiText?>(requestKey) {
            execute { input ->
                when (input) {
                    is PlayerRequest.Transport ->
                        (playbackController.execute(input.command) as? AppResult.Failure)?.error?.toPlayerMessage()
                    PlayerRequest.Reconnect ->
                        (playbackController.reconnect() as? AppResult.Failure)?.error?.toPlayerMessage()
                }
            }
            onAccepted { copy(commandNotice = null) }
            onResult { _, message ->
                if (message == null) this else copy(commandNotice = PlayerCommandNotice(PlayerCommandNoticeId(), message))
            }
        }
        on<PlayerIntent.SetPlayRequested> { intent ->
            if (state.supportsItemCommand(PlaybackCapability.SetPlayRequested)) {
                submit(request, PlayerRequest.Transport(PlaybackCommand.SetPlayRequested(intent.value)))
            }
        }
        on<PlayerIntent.Next> {
            if (state.supportsItemCommand(PlaybackCapability.Next)) submit(request, PlayerRequest.Transport(PlaybackCommand.Next))
        }
        on<PlayerIntent.Previous> {
            if (state.supportsItemCommand(PlaybackCapability.Previous)) submit(request, PlayerRequest.Transport(PlaybackCommand.Previous))
        }
        on<PlayerIntent.SelectQueueEntry> { intent ->
            val snapshot = state.liveSnapshot ?: return@on
            if (PlaybackCapability.SelectQueueEntry !in snapshot.capabilities) return@on
            val command = PlaybackCommand.SelectQueueEntry(intent.sessionId, intent.entryId)
            if (command.resolveBaseIndex(snapshot) is AppResult.Success) submit(request, PlayerRequest.Transport(command))
        }
        on<PlayerIntent.RemoveQueueEntry> { intent ->
            val snapshot = state.liveSnapshot ?: return@on
            if (PlaybackCapability.RemoveQueueEntry !in snapshot.capabilities) return@on
            val command = PlaybackCommand.RemoveQueueEntry(intent.sessionId, intent.entryId)
            if (command.resolveBaseIndex(snapshot) is AppResult.Success) submit(request, PlayerRequest.Transport(command))
        }
        on<PlayerIntent.SeekTo> { intent ->
            val snapshot = state.liveSnapshot ?: return@on
            if (!state.supportsItemCommand(PlaybackCapability.SeekTo) || snapshot.queue.current != intent.target ||
                intent.positionMs < 0
            ) return@on
            val duration = state.progress?.takeIf { it.matches(snapshot) }?.durationMs?.takeIf { it > 0 } ?: return@on
            // Use engine duration, not catalog metadata; the service checks the captured token again.
            submit(request, PlayerRequest.Transport(PlaybackCommand.SeekTo(intent.target, intent.positionMs.coerceAtMost(duration))))
        }
        // Session modes can be available before a queue exists; they do not require a current item.
        on<PlayerIntent.SetShuffle> { intent ->
            if (PlaybackCapability.SetShuffle in state.liveSnapshot?.capabilities.orEmpty()) {
                submit(request, PlayerRequest.Transport(PlaybackCommand.SetShuffle(intent.enabled)))
            }
        }
        on<PlayerIntent.SetRepeat> { intent ->
            if (PlaybackCapability.SetRepeat in state.liveSnapshot?.capabilities.orEmpty()) {
                submit(request, PlayerRequest.Transport(PlaybackCommand.SetRepeat(intent.mode)))
            }
        }
        on<PlayerIntent.Reconnect> { submit(request, PlayerRequest.Reconnect) }
        on<PlayerIntent.RetryProblem> { intent ->
            val snapshot = state.liveSnapshot ?: return@on
            val problem = snapshot.transport.problem ?: return@on
            if (state.supportsItemCommand(PlaybackCapability.Retry) && snapshot.queue.current == intent.target &&
                problem.id == intent.problemId && (problem.target == null || problem.target == intent.target)
            ) submit(request, PlayerRequest.Transport(PlaybackCommand.Retry(intent.target, intent.problemId)))
        }
        on<PlayerIntent.DismissProblem> { intent ->
            if (state.problemKey == intent.key) reduce { copy(dismissedProblem = intent.key) }
        }
        on<PlayerIntent.DismissCommandNotice> { intent ->
            if (state.commandNotice?.id === intent.id) reduce { copy(commandNotice = null) }
        }
    }

    companion object {
        private val requestKey = OperationKey<PlayerRequest>("player.request")
        /** UI can observe admission/activity without gaining access to the private operation inputs. */
        val RequestKey: OperationKey<*> = requestKey
    }
}

private sealed interface PlayerRequest {
    data class Transport(val command: PlaybackCommand) : PlayerRequest
    data object Reconnect : PlayerRequest
}
