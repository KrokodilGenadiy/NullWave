package com.zaus.nullwave.feature.player.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleStartEffect
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.media.waveform.WaveformSource
import com.zaus.nullwave.core.designsystem.components.overlay.NullWaveModalSheet
import com.zaus.nullwave.core.mvi.ui.MviUiState
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.player.presentation.PlayerIntent
import com.zaus.nullwave.feature.player.presentation.PlayerState
import com.zaus.nullwave.feature.player.presentation.PlayerViewModel
import com.zaus.nullwave.feature.player.presentation.PlayerWaveform
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Modal presentation only: closing never sends a playback command or changes the host's binding. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerFullSheet(
    ui: MviUiState<PlayerState, PlayerIntent>,
    lifecycleOwner: LifecycleOwner,
    clock: PlaybackClock,
    artwork: () -> LibraryArtworkRenderer,
    waveformSource: WaveformSource,
    onClose: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val state = ui.state
    val empty = state.liveSnapshot?.queue?.entries?.isEmpty() == true
    LaunchedEffect(empty) { if (empty) onClose() }
    if (empty) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            try {
                sheetState.hide()
                if (!sheetState.isVisible) onClose()
            } finally { closing = false }
        }
    }
    NullWaveModalSheet(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxSize(),
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        showDragHandle = false,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        PlayerFullPlayer(
            state = state,
            canSubmit = ui.operations[PlayerViewModel.RequestKey].canSubmit && !closing,
            onIntent = ui.onIntent,
            clock = clock,
            artwork = artwork,
            lifecycleOwner = lifecycleOwner,
            onClose = { dismiss() },
            onOpenLibrary = {
                // Navigation must not depend on a hide animation owned by this composition.
                if (!closing) {
                    closing = true
                    onClose()
                    onOpenLibrary()
                }
            },
            modifier = Modifier.fillMaxSize(),
            waveform = { id -> rememberPlayerWaveform(id, waveformSource, lifecycleOwner) },
        )
    }
}

@Composable
private fun rememberPlayerWaveform(trackId: TrackId, source: WaveformSource, lifecycleOwner: LifecycleOwner): PlayerWaveform {
    var waveform by remember(trackId, source, lifecycleOwner) { mutableStateOf<PlayerWaveform>(PlayerWaveform.Unavailable) }
    val scope = rememberCoroutineScope()
    LifecycleStartEffect(trackId, source, lifecycleOwner = lifecycleOwner) {
        waveform = PlayerWaveform.Loading
        val observation = scope.launch {
            val result = source.load(trackId)
            // A stopped reader may finish after the same track has been opened again.
            ensureActive()
            waveform = result?.let(PlayerWaveform::Ready) ?: PlayerWaveform.Unavailable
        }
        onStopOrDispose {
            observation.cancel()
            waveform = PlayerWaveform.Unavailable
        }
    }
    return waveform
}
