package com.zaus.nullwave.core.mvi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zaus.nullwave.core.mvi.MviViewModel
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.runtime.MviAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Collects screen state and keeps this ViewModel's sources active while the UI is visible.
 *
 * Use this once in a screen. Reading [MviViewModel.state] alone does not start sources.
 * Leaving composition, stopping the owner or switching ViewModels releases this UI binding;
 * the ViewModel applies its source stop timeout after the last binding leaves. Commands keep
 * their independent ViewModel lifetime.
 *
 * [minActiveState] must be [Lifecycle.State.STARTED] or [Lifecycle.State.RESUMED]. The returned
 * state initially contains the current snapshot, including when the owner is not yet active.
 * A different ViewModel or owner gets its own snapshot immediately, before collection starts.
 */
@Composable
@Suppress("StateFlowValueCalledInComposition") // Seed the lifecycle-aware collection.
fun <S : UiState> MviViewModel<S, *, *>.collectMviStateWithLifecycle(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): State<S> {
    requireVisibleState(minActiveState)
    val collected = remember(this, lifecycleOwner, minActiveState) { mutableStateOf(state.value) }
    LaunchedEffect(this, lifecycleOwner, minActiveState) {
        collectStateWithUiBinding(lifecycleOwner.lifecycle, minActiveState) {
            collected.value = it
        }
    }
    return collected
}

/** The lifecycle adapter is separate so its binding and cancellation contract is JVM-testable. */
internal suspend fun <S : UiState> MviViewModel<S, *, *>.collectStateWithUiBinding(
    lifecycle: Lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onState: (S) -> Unit,
) {
    requireVisibleState(minActiveState)
    MviAccess.checkUiEntryAllowed()
    withContext(Dispatchers.Main.immediate + MviAccess.neutralContext()) {
        lifecycle.repeatOnLifecycle(minActiveState) {
            withUiBinding {
                state.collect { onState(it) }
            }
        }
    }
}

internal fun requireVisibleState(state: Lifecycle.State) {
    require(state == Lifecycle.State.STARTED || state == Lifecycle.State.RESUMED) {
        "MVI UI collection requires STARTED or RESUMED, but was $state."
    }
}
