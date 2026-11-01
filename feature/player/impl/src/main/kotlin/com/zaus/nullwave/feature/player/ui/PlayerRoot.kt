package com.zaus.nullwave.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import com.zaus.nullwave.core.mvi.ui.MviUiState
import com.zaus.nullwave.core.mvi.ui.collectMviUiWithLifecycle
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
    content: @Composable () -> Unit,
) {
    val viewModel = metroViewModel<PlayerViewModel>(viewModelStoreOwner = owner, key = PlayerViewModelKey)
    val ui = viewModel.collectMviUiWithLifecycle(lifecycleOwner, minActiveState = Lifecycle.State.STARTED)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.weight(1f).fillMaxSize()) { content() }
        if (!WindowInsets.isImeVisible) {
            PlayerHostNotice(ui, onOpenLibrary)
        }
    }
}

/** Snapshot reads stay below navigation, so progress samples don't invalidate the navigation host. */
@Composable
private fun PlayerHostNotice(ui: MviUiState<PlayerState, PlayerIntent>, onOpenLibrary: () -> Unit) {
    PlayerNotice(
        state = ui.state,
        canSubmit = ui.operations[PlayerViewModel.RequestKey].canSubmit,
        onIntent = ui.onIntent,
        onOpenLibrary = onOpenLibrary,
    )
}
