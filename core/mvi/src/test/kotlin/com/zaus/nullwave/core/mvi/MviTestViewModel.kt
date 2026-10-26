package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaus.nullwave.core.mvi.runtime.MviRuntime
import com.zaus.nullwave.core.mvi.runtime.operation.MviOperation
import com.zaus.nullwave.core.mvi.state.MviSnapshot
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Test-only access to the same engine used by BaseMviViewModel. Historical scheduler/lifecycle
 * regressions exercise its low-level boundaries without keeping unsafe APIs in the public Base.
 */
internal abstract class MviTestViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
    sourceStopTimeoutMillis: Long = 5_000,
) : ViewModel(), MviViewModel<S, I, E> {
    private val runtime = MviRuntime<S, E>(
        initialState = initialState,
        scope = viewModelScope,
        sourceStopTimeoutMillis = sourceStopTimeoutMillis,
        observeSources = { observeSources() },
    )

    init {
        addCloseable(runtime)
    }

    final override val snapshot: StateFlow<MviSnapshot<S>> get() = runtime.snapshot
    final override suspend fun collectEffects(onEffect: suspend (E) -> Unit) = runtime.collectEffects(onEffect)
    // Low-level fixture dispatch is intentionally separate from production Base's public API.
    abstract fun onIntent(intent: I)
    final override suspend fun uiApi(): MviUiApi<S, I> = MviUiApi(snapshot, ::onIntent)
    protected val currentState: S get() = runtime.state

    final override suspend fun withUiBinding(block: suspend () -> Unit) = runtime.withUiBinding(block)

    protected fun setState(reducer: S.() -> S) = runtime.update(reducer)
    protected fun latestOperation(): MviOperation<S> = runtime.latestOperation()
    protected fun queuedOperation(maxPending: Int = 16): MviOperation<S> = runtime.queuedOperation(maxPending)
    protected fun idleOperation(): MviOperation<S> = runtime.idleOperation()
    protected fun <T> selectState(selector: (S) -> T): Flow<T> = snapshot.map { selector(it.state) }.distinctUntilChanged()
    protected suspend fun sendEffect(effect: E): Boolean = runtime.sendEffect(effect)
    protected open suspend fun observeSources() = Unit
}
