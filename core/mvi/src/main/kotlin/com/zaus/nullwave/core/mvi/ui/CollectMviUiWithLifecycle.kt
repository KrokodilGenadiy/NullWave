package com.zaus.nullwave.core.mvi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.state.MviSnapshot
import com.zaus.nullwave.core.mvi.state.OperationStates

/** Observable screen snapshot and stable callback; neither owns the ViewModel. */
@Stable
class MviUiState<S : UiState, I : UiIntent> internal constructor(
    private val collected: State<MviSnapshot<S>>,
    val onIntent: (I) -> Unit,
) {
    val snapshot: MviSnapshot<S> get() = collected.value
    val state: S get() = snapshot.state
    val operations: OperationStates get() = snapshot.operations
}

/**
 * Acquires the UI callback without binding sources. Use this when a permission/platform observer
 * must be registered before collectMviUiWithLifecycle. The callback survives lifecycle pauses;
 * the ViewModel, rather than a STARTED/RESUMED lease, controls its final lifetime.
 */
@Composable
fun <S : UiState, I : UiIntent> rememberMviUiApi(
    viewModel: BaseMviViewModel<S, I, *>,
): MviUiApi<S, I> = remember(viewModel) { viewModel.uiApiForCompose() }

/**
 * Collects one canonical data/activity snapshot and exposes the UI intent callback.
 * Leaving composition or stopping the owner releases observation, not the ViewModel's commands.
 * Reading [MviUiState.state] or [MviUiState.operations] participates in Compose snapshot observation.
 */
@Composable
fun <S : UiState, I : UiIntent> BaseMviViewModel<S, I, *>.collectMviUiWithLifecycle(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): MviUiState<S, I> {
    val api = rememberMviUiApi(this)
    val collected = collectMviSnapshotWithLifecycle(lifecycleOwner, minActiveState)
    return remember(api, collected) { MviUiState(collected, api.onIntent) }
}
