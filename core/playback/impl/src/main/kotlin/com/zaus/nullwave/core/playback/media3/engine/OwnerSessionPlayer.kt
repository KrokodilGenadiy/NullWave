package com.zaus.nullwave.core.playback.media3.engine

import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.playback.media3.service.PlaybackStartGuard
import com.zaus.nullwave.core.playback.media3.session.serviceFuture
import com.zaus.nullwave.core.playback.runtime.PlaybackQueueOwner
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.CoroutineScope

/** Native/system controls and private commands share one admission boundary and one raw player. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class OwnerSessionPlayer(
    private val raw: Player,
    private val owner: PlaybackQueueOwner,
    private val target: Media3QueueCommitTarget,
    private val scope: CoroutineScope,
    private val startGuard: PlaybackStartGuard,
) : ForwardingSimpleBasePlayer(raw) {
    override fun getState(): State {
        val state = super.getState()
        return state.buildUpon().setAvailableCommands(
            state.availableCommands.buildUpon()
                .remove(Player.COMMAND_SET_MEDIA_ITEM)
                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .build(),
        ).build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> = command(playWhenReady) {
        owner.execute(PlaybackCommand.SetPlayRequested(playWhenReady))
    }

    override fun handlePrepare(): ListenableFuture<*> = native(raw.playWhenReady) { target.prepareFromNative() }

    // Native stop retains the playlist. The app's atomic StopAndClear is a private session command.
    override fun handleStop(): ListenableFuture<*> = native { raw.stop() }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> = command {
        val mode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> RepeatMode.Off
            Player.REPEAT_MODE_ONE -> RepeatMode.One
            Player.REPEAT_MODE_ALL -> RepeatMode.All
            else -> throw IllegalArgumentException("Unknown native repeat mode")
        }
        owner.execute(PlaybackCommand.SetRepeat(mode))
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> = command {
        owner.execute(PlaybackCommand.SetShuffle(shuffleModeEnabled))
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> = native(raw.playWhenReady) {
        when (seekCommand) {
            Player.COMMAND_SEEK_BACK -> raw.seekBack()
            Player.COMMAND_SEEK_FORWARD -> raw.seekForward()
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> raw.seekTo(positionMs)
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION -> raw.seekToDefaultPosition()
            Player.COMMAND_SEEK_TO_MEDIA_ITEM -> if (mediaItemIndex != C.INDEX_UNSET) raw.seekTo(mediaItemIndex, positionMs)
            Player.COMMAND_SEEK_TO_NEXT -> raw.seekToNext()
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> raw.seekToNextMediaItem()
            Player.COMMAND_SEEK_TO_PREVIOUS -> raw.seekToPrevious()
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> raw.seekToPreviousMediaItem()
            else -> throw IllegalArgumentException("Unknown native seek command")
        }
    }

    private fun native(needsForeground: Boolean = false, action: () -> Unit): ListenableFuture<*> =
        command(needsForeground) { owner.executeNative(action) }

    private fun command(
        needsForeground: Boolean = false,
        action: suspend () -> EmptyResult<PlaybackCommandError>,
    ): ListenableFuture<*> =
        scope.serviceFuture {
            when (val result = startGuard.run(needsForeground, action)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> throw IllegalStateException(
                    "Native playback command rejected: ${result.error}" +
                        (result.originalMessage?.let { ": $it" } ?: ""),
                    result.cause,
                )
            }
        }
}
