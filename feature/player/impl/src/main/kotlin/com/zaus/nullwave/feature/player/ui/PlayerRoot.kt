package com.zaus.nullwave.feature.player.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import com.zaus.nullwave.core.media.waveform.WaveformSource
import com.zaus.nullwave.core.mvi.ui.MviUiState
import com.zaus.nullwave.core.mvi.ui.collectMviUiWithLifecycle
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.player.presentation.PlayerIntent
import com.zaus.nullwave.feature.player.presentation.PlayerState
import com.zaus.nullwave.feature.player.presentation.PlayerViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

internal const val PlayerViewModelKey = "nullwave.player"

/** One Activity-owned binding; navigation entries and both player surfaces share its presentation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerRoot(
    owner: ViewModelStoreOwner,
    lifecycleOwner: LifecycleOwner,
    onOpenLibrary: () -> Unit,
    clock: PlaybackClock,
    artwork: () -> LibraryArtworkRenderer,
    waveformSource: WaveformSource,
    content: @Composable () -> Unit,
) {
    val viewModel = metroViewModel<PlayerViewModel>(viewModelStoreOwner = owner, key = PlayerViewModelKey)
    val ui = viewModel.collectMviUiWithLifecycle(lifecycleOwner, minActiveState = Lifecycle.State.STARTED)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val showControls = !WindowInsets.isImeVisible
    PlayerHostLayout(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        controlsVisible = !expanded,
        notice = { if (showControls) PlayerHostNotice(ui, onOpenLibrary) },
        miniPlayer = { if (showControls) PlayerHostMini(ui, lifecycleOwner, clock, artwork, { expanded = true }, !expanded) },
        content = content,
    )
    if (expanded) PlayerFullSheet(ui, lifecycleOwner, clock, artwork, waveformSource, { expanded = false }, onOpenLibrary)
}

/** Snapshot reads stay below navigation, so progress samples don't invalidate the navigation host. */
@Composable
private fun PlayerHostMini(
    ui: MviUiState<PlayerState, PlayerIntent>,
    lifecycleOwner: LifecycleOwner,
    clock: PlaybackClock,
    artwork: () -> LibraryArtworkRenderer,
    onExpand: () -> Unit,
    active: Boolean,
) {
    PlayerMiniPlayer(ui.state, ui.operations[PlayerViewModel.RequestKey].canSubmit, ui.onIntent, clock, artwork, lifecycleOwner,
        onExpand = onExpand, active = active)
}

@Composable
private fun PlayerHostNotice(ui: MviUiState<PlayerState, PlayerIntent>, onOpenLibrary: () -> Unit) {
    PlayerScrollableNotice(
        state = ui.state,
        canSubmit = ui.operations[PlayerViewModel.RequestKey].canSubmit,
        onIntent = ui.onIntent,
        onOpenLibrary = onOpenLibrary,
    )
}
