package com.zaus.nullwave.core.mvi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zaus.nullwave.core.mvi.MviViewModel
import com.zaus.nullwave.core.mvi.UiEffect
import com.zaus.nullwave.core.mvi.runtime.MviAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Handles optional effects while this screen is visible, using the latest [onEffect] callback.
 *
 * Use once per ViewModel. Stopping the owner, leaving composition or changing ViewModels cancels
 * the active handler; it is not replayed. Operation replacement/cancellation can also revoke its
 * handler. Detached work and completed external actions cannot be cancelled by this adapter.
 * Switching the owner, ViewModel or active state waits for the previous handler's cleanup.
 * This does not bind sources: pair it with collectMviStateWithLifecycle when rendering State.
 */
@Composable
fun <E : UiEffect> ObserveMviEffects(
    viewModel: MviViewModel<*, *, E>,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: suspend (E) -> Unit,
) {
    requireVisibleState(minActiveState)
    val currentHandler by rememberUpdatedState(onEffect)
    val collectionHandoff = remember { Mutex() }
    LaunchedEffect(viewModel, lifecycleOwner, minActiveState) {
        // LaunchedEffect cancels its predecessor without joining non-cancellable cleanup.
        // Keep one collection lease through key changes, including a return to the same VM.
        collectionHandoff.withLock {
            viewModel.collectEffectsWithLifecycle(lifecycleOwner.lifecycle, minActiveState) {
                currentHandler(it)
            }
        }
    }
}

/** Lifecycle adapter for non-Compose hosts; requires STARTED or RESUMED and handles on Main. */
suspend fun <E : UiEffect> MviViewModel<*, *, E>.collectEffectsWithLifecycle(
    lifecycle: Lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: suspend (E) -> Unit,
) {
    requireVisibleState(minActiveState)
    MviAccess.checkUiEntryAllowed()
    withContext(Dispatchers.Main.immediate + MviAccess.neutralContext()) {
        lifecycle.repeatOnLifecycle(minActiveState) {
            collectEffects(onEffect)
        }
    }
}
