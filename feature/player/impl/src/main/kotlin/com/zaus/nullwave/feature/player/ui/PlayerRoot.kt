package com.zaus.nullwave.feature.player.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import com.zaus.nullwave.core.mvi.ui.MviUiState
import com.zaus.nullwave.core.mvi.ui.collectMviUiWithLifecycle
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.player.presentation.PlayerIntent
import com.zaus.nullwave.feature.player.presentation.PlayerState
import com.zaus.nullwave.feature.player.presentation.PlayerViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

internal const val PlayerViewModelKey = "nullwave.player"

/** One Activity-owned binding; navigation entries and a future full player share its presentation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerRoot(
    owner: ViewModelStoreOwner,
    lifecycleOwner: LifecycleOwner,
    onOpenLibrary: () -> Unit,
    clock: PlaybackClock,
    artwork: () -> LibraryArtworkRenderer,
    content: @Composable () -> Unit,
) {
    val viewModel = metroViewModel<PlayerViewModel>(viewModelStoreOwner = owner, key = PlayerViewModelKey)
    val ui = viewModel.collectMviUiWithLifecycle(lifecycleOwner, minActiveState = Lifecycle.State.STARTED)
    val showControls = !WindowInsets.isImeVisible
    PlayerHostLayout(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        notice = { if (showControls) PlayerHostNotice(ui, onOpenLibrary) },
        miniPlayer = { if (showControls) PlayerHostMini(ui, lifecycleOwner, clock, artwork) },
        content = content,
    )
}

/** Snapshot reads stay below navigation, so progress samples don't invalidate the navigation host. */
@Composable
private fun PlayerHostMini(
    ui: MviUiState<PlayerState, PlayerIntent>,
    lifecycleOwner: LifecycleOwner,
    clock: PlaybackClock,
    artwork: () -> LibraryArtworkRenderer,
) {
    PlayerMiniPlayer(ui.state, ui.operations[PlayerViewModel.RequestKey].canSubmit, ui.onIntent, clock, artwork, lifecycleOwner)
}

@Composable
private fun PlayerHostNotice(ui: MviUiState<PlayerState, PlayerIntent>, onOpenLibrary: () -> Unit) {
    val state = ui.state
    // A new displayed message starts at its beginning; unrelated playback samples preserve scroll.
    val noticeKey = state.commandNotice?.id ?: (state.connection as? PlaybackConnection.Unavailable)?.reason ?: state.problemKey
    key(noticeKey) {
        PlayerNotice(
            state = state,
            canSubmit = ui.operations[PlayerViewModel.RequestKey].canSubmit,
            onIntent = ui.onIntent,
            onOpenLibrary = onOpenLibrary,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}
