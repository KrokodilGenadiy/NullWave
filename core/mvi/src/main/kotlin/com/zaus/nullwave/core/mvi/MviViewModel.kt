package com.zaus.nullwave.core.mvi

import com.zaus.nullwave.core.mvi.ui.ObserveMviEffects
import com.zaus.nullwave.core.mvi.ui.collectMviStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/** An immutable screen snapshot, including immutable collections and nested values. */
interface UiState

/** A user or platform action; the ViewModel routes its processing to Main. */
interface UiIntent

/** An optional UI signal whose loss or delayed delivery is acceptable. */
interface UiEffect

interface MviViewModel<S : UiState, I : UiIntent, E : UiEffect> {
    /** Reading or collecting this flow does not activate source observations. */
    val state: StateFlow<S>

    /**
     * Handles optional signals sequentially on Main. Revoking an operation's effects cancels its
     * active handler and waits for cleanup before the next signal; unrelated effects do not cancel
     * one another. Cancellation is cooperative and cannot undo an already performed UI action.
     * Important results belong in [state] or persistent data, not this best-effort delivery.
     * Only one active collector is allowed. A cleared ViewModel returns normally without invoking
     * [onEffect] or initializing its definition. Compose screens use [ObserveMviEffects].
     */
    suspend fun collectEffects(onEffect: suspend (E) -> Unit)

    /**
     * Explicitly binds an active UI for the duration of [block], releasing it on completion,
     * cancellation or failure. The block runs on Main after initialization. Compose screens use
     * collectMviStateWithLifecycle; other hosts bind inside their own active lifecycle interval.
     * A cleared ViewModel ignores a new binding without running its block.
     */
    suspend fun withUiBinding(block: suspend () -> Unit)

    /** Admits intents in order for Main processing; background callers cannot await the outcome. */
    fun onIntent(intent: I)
}
