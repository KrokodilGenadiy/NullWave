package com.zaus.nullwave.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.library.api.ui.libraryMonogram
import com.zaus.nullwave.feature.player.presentation.PlayerState

@Preview(name = "Player mini host", widthDp = 360, showBackground = true)
@Preview(name = "Player mini host RU large", locale = "ru", fontScale = 1.5f, widthDp = 320, showBackground = true)
@Composable
private fun PlayerMiniPlayerPreview() {
    NullWaveTheme {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md)) {
            val playing = previewPlayerState()
            Text("Playing", color = NullWaveTheme.colors.textPrimary)
            MiniPreview(playing)
            Text("Paused / unknown artist", color = NullWaveTheme.colors.textPrimary)
            MiniPreview(previewPlayerState(playing = false, artist = null))
            Text("Connection lost", color = NullWaveTheme.colors.textPrimary)
            val disconnected = playing.copy(connection = PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected), progress = null)
            Column {
                PlayerNotice(disconnected, canSubmit = true, onIntent = {}, onOpenLibrary = {})
                MiniPreview(disconnected)
            }
        }
    }
}

@Preview(name = "Player compact notice RU", locale = "ru", fontScale = 2f, widthDp = 320, heightDp = 240)
@Preview(name = "Player compact notice EN", widthDp = 360, heightDp = 200)
@Composable
private fun PlayerCompactHostPreview() {
    NullWaveTheme {
        val state = previewPlayerState(playing = false, problem = PlaybackProblemCode.AccessDenied)
        PlayerHostLayout(
            modifier = Modifier.fillMaxSize().background(NullWaveTheme.colors.bg),
            notice = {
                PlayerNotice(state, canSubmit = true, onIntent = {}, onOpenLibrary = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            miniPlayer = { MiniPreview(state) },
            content = { Text("Library", color = NullWaveTheme.colors.textPrimary) },
        )
    }
}

@Composable
private fun MiniPreview(state: PlayerState) {
    PlayerMiniPlayer(state, true, {}, PreviewClock, { PreviewArtwork }, LocalLifecycleOwner.current)
}

private val PreviewClock = PlaybackClock { 10_000L }
private object PreviewArtwork : LibraryArtworkRenderer {
    @Composable
    override fun Artwork(artwork: ArtworkRef?, title: String, size: Dp, modifier: Modifier) {
        NullWaveCoverArt(modifier = modifier, size = size, monogram = libraryMonogram(title))
    }
}

private fun previewPlayerState(
    playing: Boolean = true,
    artist: String? = "Static Pilgrim",
    problem: PlaybackProblemCode? = null,
): PlayerState {
    val track = MediaTrack(TrackId("preview:track"), "Deadzone Chorus — live at the city beneath the neon sky", artist, "Nullwave EP", 180_000L)
    val session = PlaybackSessionId("preview-session")
    val entry = QueueEntry(QueueEntryId("preview-entry"), track)
    val target = PlaybackItemToken(session, entry.entryId, 0)
    val snapshot = PlaybackSnapshot.create(
        sessionId = session,
        revision = 0,
        queue = PlaybackQueue.create(entries = listOf(entry), current = target),
        transport = PlaybackTransport(if (problem == null) PlaybackPhase.Ready else PlaybackPhase.Idle, playRequested = playing,
            problem = problem?.let { PlaybackProblem(PlaybackProblemId("preview-problem"), it, target) }),
        positionRevision = 0,
        capabilities = if (problem == null) setOf(PlaybackCapability.SetPlayRequested, PlaybackCapability.Next) else emptySet(),
    )
    return PlayerState(
        connection = PlaybackConnection.Connected(snapshot), lastKnownSnapshot = snapshot,
        progress = PlaybackProgress(target, 0, 68_400L, 180_000L, null, 10_000L, playing),
    )
}
