package com.zaus.nullwave.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.ArtworkRef
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
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
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.library.api.ui.libraryMonogram
import com.zaus.nullwave.feature.player.presentation.PlayerState

@Preview(name = "Full player EN", widthDp = 412, heightDp = 820)
@Composable
private fun FullPlayerPreview() = FullPreview(fullPreviewState())

@Preview(name = "Full player RU large text", locale = "ru", fontScale = 1.5f, widthDp = 360, heightDp = 720)
@Composable
private fun FullPlayerRussianPreview() = FullPreview(fullPreviewState(russian = true, denied = true))

@Preview(name = "Full player compact", locale = "ru", fontScale = 2f, widthDp = 320, heightDp = 240)
@Composable
private fun FullPlayerCompactPreview() = FullPreview(fullPreviewState(russian = true, denied = true))

@Preview(name = "Queue with repeated track", widthDp = 360, heightDp = 260)
@Composable
private fun PlayerQueuePreview() {
    val first = QueueEntry(QueueEntryId("first"),
        MediaTrack(TrackId("chorus"), "Deadzone Chorus", "Static Pilgrim", "Nullwave EP", 221_000))
    val next = QueueEntry(QueueEntryId("next"),
        MediaTrack(TrackId("transit"), "Hollow Transit", "Kova", null, 267_000))
    val repeat = first.copy(entryId = QueueEntryId("repeat"))
    val queue = PlaybackQueue.create(
        entries = listOf(first, next, repeat),
        playOrder = listOf(next.entryId, repeat.entryId, first.entryId),
        current = PlaybackItemToken(PlaybackSessionId("preview"), repeat.entryId, 0),
        shuffleEnabled = true,
    )
    NullWaveTheme { PlayerQueue(queue, 1, FullPreviewArtwork, onSelectEntry = {}) }
}

@Preview(name = "Full player disconnected", widthDp = 360, heightDp = 720)
@Composable
private fun FullPlayerDisconnectedPreview() {
    FullPreview(fullPreviewState().copy(
        connection = PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected),
        progress = null,
    ))
}

@Composable
private fun FullPreview(state: PlayerState) {
    NullWaveTheme {
        PlayerFullPlayer(
            state = state,
            canSubmit = true,
            onIntent = {},
            clock = FullPreviewClock,
            artwork = { FullPreviewArtwork },
            lifecycleOwner = LocalLifecycleOwner.current,
            onClose = {},
            onOpenLibrary = {},
        )
    }
}

private val FullPreviewClock = PlaybackClock { 10_000L }
private object FullPreviewArtwork : LibraryArtworkRenderer {
    @Composable
    override fun Artwork(artwork: ArtworkRef?, title: String, size: Dp, modifier: Modifier) {
        NullWaveCoverArt(modifier = modifier, size = size, monogram = libraryMonogram(title))
    }
}

private fun fullPreviewState(russian: Boolean = false, denied: Boolean = false): PlayerState {
    val track = MediaTrack(
        id = TrackId("preview:full-track"),
        title = if (russian) "Сигнал сквозь ночной город" else "Deadzone Chorus",
        artist = if (russian) "Статический странник" else "Static Pilgrim",
        album = "Nullwave EP",
        durationMs = 241_000L,
    )
    val session = PlaybackSessionId("preview-full-session")
    val entries = List(14) { QueueEntry(QueueEntryId("preview-full-entry-$it"), track) }
    val target = PlaybackItemToken(session, entries[2].entryId, 0)
    val snapshot = PlaybackSnapshot.create(
        sessionId = session,
        revision = 0,
        queue = PlaybackQueue.create(entries = entries, current = target, shuffleEnabled = true, repeatMode = RepeatMode.All),
        transport = PlaybackTransport(
            if (denied) PlaybackPhase.Idle else PlaybackPhase.Ready,
            playRequested = !denied,
            problem = if (denied) PlaybackProblem(PlaybackProblemId("preview-denied"), PlaybackProblemCode.AccessDenied, target) else null,
        ),
        positionRevision = 0,
        capabilities = if (denied) emptySet() else setOf(
            PlaybackCapability.SetPlayRequested,
            PlaybackCapability.Previous,
            PlaybackCapability.Next,
            PlaybackCapability.SeekTo,
            PlaybackCapability.SetShuffle,
            PlaybackCapability.SetRepeat,
        ),
    )
    return PlayerState(
        connection = PlaybackConnection.Connected(snapshot),
        lastKnownSnapshot = snapshot,
        progress = PlaybackProgress(target, 0, 84_000L, 241_000L, null, 10_000L, advancing = !denied),
    )
}
