package com.zaus.nullwave.feature.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.player.NullWaveRepeatMode
import com.zaus.nullwave.core.designsystem.components.player.NullWaveSeekBar
import com.zaus.nullwave.core.designsystem.components.player.NullWaveTransportControls
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.player.presentation.PlayerWaveform
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.library.api.ui.libraryDuration
import com.zaus.nullwave.feature.player.impl.R
import com.zaus.nullwave.feature.player.presentation.PlayerIntent
import com.zaus.nullwave.feature.player.presentation.PlayerState
import com.zaus.nullwave.core.designsystem.R as DesignR

/** Sheet content only: the Activity host owns expansion, collection and the shared Player VM. */
@Composable
internal fun PlayerFullPlayer(
    state: PlayerState,
    canSubmit: Boolean,
    onIntent: (PlayerIntent) -> Unit,
    clock: PlaybackClock,
    artwork: () -> LibraryArtworkRenderer,
    lifecycleOwner: LifecycleOwner,
    onClose: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    waveform: @Composable (TrackId) -> PlayerWaveform = { PlayerWaveform.Unavailable },
) {
    val queue = state.displaySnapshot?.queue
    // The engine emits progress frequently; metadata/order scans only belong to queue changes.
    val track = remember(queue) { state.currentTrack }
    val currentIndex = remember(queue) {
        queue?.current?.let { queue.playOrder.indexOf(it.entryId) }
    }
    val renderer = remember(artwork) { artwork() }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    val playerScroll = rememberScrollState()
    if (queueOpen) BackHandler { queueOpen = false }
    val lifecycle by lifecycleOwner.lifecycle.currentStateAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val spacing = NullWaveTheme.spacing
    Column(modifier.fillMaxSize().background(NullWaveTheme.colors.bg).testTag("player_full")) {
        val header: @Composable () -> Unit = {
            PlayerHeader(queueOpen, queue, currentIndex) { if (queueOpen) queueOpen = false else onClose() }
        }
        if (!queueOpen) header()
        // The pinned MiniPlayer gets its natural height before the notice and scroll viewport.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val noticeMaxHeight = maxOf(maxHeight / 3, NullWaveTheme.dimens.touchTarget).coerceAtMost(maxHeight)
            Column(Modifier.fillMaxSize()) {
                if (queueOpen) {
                    if (queue != null && currentIndex != null) {
                        PlayerQueue(queue, currentIndex, renderer, Modifier.weight(1f), header)
                    } else {
                        header()
                        Text(
                            stringResource(R.string.player_queue_unavailable),
                            modifier = Modifier.weight(1f).padding(spacing.lg),
                            color = NullWaveTheme.colors.textSecondary,
                            style = NullWaveTheme.typography.body,
                        )
                    }
                } else if (track != null) {
                    PlayerBodyLayout(
                        modifier = Modifier.weight(1f),
                        scroll = playerScroll,
                        artwork = { size ->
                            renderer.Artwork(
                                track.artwork,
                                track.album ?: track.title,
                                size,
                                Modifier.testTag("player_full_artwork"),
                            )
                        },
                    ) {
                        PlayerFullMetadata(track)
                        val timeline = if (state.liveSnapshot != null) waveform(track.id) else PlayerWaveform.Unavailable
                        PlayerFullControls(state, canSubmit && active, onIntent, clock, track.id, timeline)
                        PlayerSecondaryActions { queueOpen = true }
                    }
                } else {
                    Text(
                        stringResource(R.string.player_no_track),
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(spacing.xs),
                        textAlign = TextAlign.Center,
                        color = NullWaveTheme.colors.textSecondary,
                        style = NullWaveTheme.typography.body,
                    )
                }
                // A late failure is visible without moving the artwork/controls scroll position.
                PlayerScrollableNotice(
                    state, canSubmit && active, onIntent, onOpenLibrary,
                    Modifier.fillMaxWidth().heightIn(max = noticeMaxHeight),
                )
            }
        }
        if (queueOpen) {
            PlayerMiniPlayer(state, canSubmit, onIntent, clock, artwork, lifecycleOwner, onExpand = { queueOpen = false })
        }
    }
}

@Composable
private fun PlayerHeader(queueOpen: Boolean, queue: PlaybackQueue?, currentIndex: Int?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = NullWaveTheme.spacing.xs, vertical = NullWaveTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NullWaveIconButton(
            icon = NullWaveIcons.Collapse,
            contentDescription = stringResource(if (queueOpen) R.string.player_back_to_player else R.string.player_collapse),
            onClick = onBack,
            modifier = Modifier.testTag(if (queueOpen) "player_queue_back" else "player_full_close"),
        )
        Text(
            text = when {
                queueOpen && queue != null -> queueSummary(queue.entries)
                queueOpen -> stringResource(R.string.player_queue)
                currentIndex != null && queue != null -> stringResource(R.string.player_queue_position, currentIndex + 1, queue.playOrder.size)
                else -> stringResource(R.string.player_now_playing)
            },
            modifier = Modifier.weight(1f),
            color = NullWaveTheme.colors.textSecondary,
            style = NullWaveTheme.typography.mono,
            textAlign = TextAlign.Center,
        )
        if (queueOpen) {
            NullWaveIconButton(NullWaveIcons.More, stringResource(R.string.player_queue_options), {}, enabled = false)
        } else Spacer(Modifier.size(NullWaveTheme.dimens.touchTarget))
    }
}

@Composable
private fun PlayerBodyLayout(
    modifier: Modifier,
    scroll: ScrollState,
    artwork: @Composable (Dp) -> Unit,
    controls: @Composable () -> Unit,
) {
    val spacing = NullWaveTheme.spacing
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val viewportHeight = constraints.maxHeight
        Layout(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).testTag("player_full_scroll")
                .padding(horizontal = spacing.lg),
            content = {
                BoxWithConstraints { artwork(maxWidth) }
                Column { controls() }
            },
        ) { (cover, details), constraints ->
            // Text and controls take their natural height, including any wrapped rows.
            val width = constraints.maxWidth
            val panel = details.measure(Constraints.fixedWidth(width))
            val inset = spacing.xs.roundToPx()
            val gap = spacing.md.roundToPx()
            val maxSide = minOf(width, MaxArtworkSize.roundToPx())
            val remaining = viewportHeight - panel.height - 2 * inset - gap
            val side = remaining.coerceIn(minOf(MinArtworkSize.roundToPx(), maxSide), maxSide)
            val image = cover.measure(Constraints.fixed(side, side))
            val panelTop = inset + side + gap
            layout(width, maxOf(constraints.minHeight, panelTop + panel.height + inset)) {
                image.placeRelative((width - side) / 2, inset)
                panel.placeRelative(0, panelTop)
            }
        }
    }
}

@Composable
private fun PlayerFullMetadata(track: MediaTrack) {
    val typography = NullWaveTheme.typography
    val spacing = NullWaveTheme.spacing
    // Keep the space before the timeline inside the block. Taller fallback glyphs can use it.
    val reservedHeight = with(LocalDensity.current) {
        val titleLine = typography.display.lineHeight.toDp()
        val creditLine = typography.body.lineHeight.toDp()
        (titleLine + creditLine) * MetadataMaxLines + spacing.xxs + maxOf(spacing.lg, titleLine)
    }
    Column(
        Modifier.fillMaxWidth().heightIn(min = reservedHeight).testTag("player_full_metadata")
            .padding(bottom = spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = track.title,
            modifier = Modifier.fillMaxWidth().testTag("player_full_title"),
            color = NullWaveTheme.colors.textPrimary,
            style = typography.display,
            maxLines = MetadataMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        val artist = track.artist ?: stringResource(R.string.player_unknown_artist)
        Text(
            text = track.album?.let { stringResource(R.string.player_artist_album, artist, it) } ?: artist,
            modifier = Modifier.fillMaxWidth().testTag("player_full_subtitle"),
            color = NullWaveTheme.colors.textSecondary,
            style = typography.body,
            maxLines = MetadataMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayerFullControls(
    state: PlayerState,
    canSubmit: Boolean,
    onIntent: (PlayerIntent) -> Unit,
    clock: PlaybackClock,
    trackId: TrackId,
    waveform: PlayerWaveform,
) {
    val live = state.liveSnapshot
    val target = live?.queue?.current
    val anchor = state.progress?.takeIf { live != null && it.matches(live) }
    val duration = anchor?.durationMs
    val position = state.positionAt(clock.nowMs())
    val enabled = live != null && canSubmit
    val seekEnabled = enabled && duration != null && duration > 0L &&
        state.supportsItemCommand(PlaybackCapability.SeekTo)
    val playRequested = state.displaySnapshot?.transport?.playRequested == true
    val displayQueue = state.displaySnapshot?.queue
    val repeatMode = displayQueue?.repeatMode ?: RepeatMode.Off
    val unknownTime = stringResource(DesignR.string.nw_time_unknown)

    // Full token equality includes session and activation: an old gesture cannot seek a new item.
    // Availability also includes lifecycle/busy/capability, so losing any of them cancels the draft.
    val source = remember(target, duration, seekEnabled) { MutableInteractionSource() }
    val dragged by key(source) { source.collectIsDraggedAsState() }
    var scrubPosition by remember(source) { mutableStateOf<Long?>(null) }
    val scrubbing = seekEnabled && (dragged || scrubPosition != null)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
        NullWaveSeekBar(
            mediaKey = trackId.value,
            positionMs = position ?: 0L,
            durationMs = duration,
            onSeek = { destination ->
                if (seekEnabled && target != null) onIntent(PlayerIntent.SeekTo(target, destination))
            },
            // Zero is only the disabled track's drawing origin; an absent engine anchor stays unknown.
            formatTime = { if (position == null) unknownTime else libraryDuration(it).orEmpty() },
            modifier = Modifier.fillMaxWidth().testTag("player_full_seek"),
            enabled = seekEnabled,
            waveform = waveform.peaks,
            onScrubPositionChange = { scrubPosition = it },
            interactionSource = source,
            glowEnabled = scrubbing,
        )
        // Transport keeps its focus/interaction nodes when only the seek session changes.
        NullWaveTransportControls(
            isPlaying = playRequested,
            onPlayPause = { onIntent(PlayerIntent.SetPlayRequested(!playRequested)) },
            onPrevious = { onIntent(PlayerIntent.Previous) },
            onNext = { onIntent(PlayerIntent.Next) },
            modifier = Modifier.testTag("player_full_transport"),
            enabled = enabled,
            canPlayPause = state.supportsItemCommand(PlaybackCapability.SetPlayRequested),
            canPrevious = state.supportsItemCommand(PlaybackCapability.Previous),
            canNext = state.supportsItemCommand(PlaybackCapability.Next),
            shuffle = displayQueue?.shuffleEnabled == true,
            onShuffleChange = { onIntent(PlayerIntent.SetShuffle(it)) },
            canShuffle = PlaybackCapability.SetShuffle in live?.capabilities.orEmpty(),
            repeatMode = when (repeatMode) {
                RepeatMode.Off -> NullWaveRepeatMode.Off
                RepeatMode.All -> NullWaveRepeatMode.All
                RepeatMode.One -> NullWaveRepeatMode.One
            },
            onRepeatClick = {
                // UI chooses the cycle; only an engine observation changes the displayed mode.
                onIntent(PlayerIntent.SetRepeat(when (repeatMode) {
                    RepeatMode.Off -> RepeatMode.All
                    RepeatMode.All -> RepeatMode.One
                    RepeatMode.One -> RepeatMode.Off
                }))
            },
            canRepeat = PlaybackCapability.SetRepeat in live?.capabilities.orEmpty(),
            playPauseGlowEnabled = live?.transport?.isPlaying == true && !scrubbing,
        )
    }
}

private val MaxArtworkSize = 320.dp
private val MinArtworkSize = 96.dp
private const val MetadataMaxLines = 2
