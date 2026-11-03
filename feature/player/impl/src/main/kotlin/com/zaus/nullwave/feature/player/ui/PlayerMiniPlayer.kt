package com.zaus.nullwave.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.zaus.nullwave.core.designsystem.components.player.NullWaveMiniPlayer
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.player.impl.R
import com.zaus.nullwave.feature.player.presentation.PlayerIntent
import com.zaus.nullwave.feature.player.presentation.PlayerState

/** No collection or ownership here: the Activity host supplies its single Player presentation. */
@Composable
internal fun PlayerMiniPlayer(
    state: PlayerState,
    canSubmit: Boolean,
    onIntent: (PlayerIntent) -> Unit,
    clock: PlaybackClock,
    artwork: () -> LibraryArtworkRenderer,
    lifecycleOwner: LifecycleOwner,
) {
    // Queue/metadata references survive progress-only emissions; never scan entries per frame.
    val queue = state.displaySnapshot?.queue
    val track = remember(queue) { state.currentTrack } ?: return
    val renderer = remember(artwork) { artwork() }
    val lifecycle by lifecycleOwner.lifecycle.currentStateAsState()
    val live = state.liveSnapshot
    val playRequested = state.displaySnapshot?.transport?.playRequested == true
    val progress = rememberPlayerProgress(state, clock, lifecycleOwner)
    NullWaveMiniPlayer(
        title = track.title,
        subtitle = track.artist ?: stringResource(R.string.player_unknown_artist),
        isPlaying = playRequested,
        onExpand = null,
        onPlayPause = { onIntent(PlayerIntent.SetPlayRequested(!playRequested)) },
        onNext = { onIntent(PlayerIntent.Next) },
        modifier = Modifier.testTag("player_mini").alpha(if (live == null) DisconnectedAlpha else 1f),
        artwork = { renderer.Artwork(track.artwork, track.album ?: track.title, MiniArtworkSize) },
        progressProvider = progress,
        buffering = live?.transport?.phase == PlaybackPhase.Buffering,
        playbackEnabled = live != null && canSubmit && lifecycle.isAtLeast(Lifecycle.State.STARTED),
        canPlayPause = state.supportsItemCommand(PlaybackCapability.SetPlayRequested),
        canNext = state.supportsItemCommand(PlaybackCapability.Next),
        playPauseGlowEnabled = live?.transport?.isPlaying == true,
        progressGlowEnabled = live?.transport?.isPlaying == true,
    )
}

private val MiniArtworkSize = 40.dp
private const val DisconnectedAlpha = 0.6f
