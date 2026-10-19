package com.zaus.nullwave.core.mvi.runtime.operation

import androidx.annotation.MainThread
import com.zaus.nullwave.core.mvi.UiState

/** State access tied to one accepted operation. Use coroutineScope for structured child work. */
internal class OperationScope<S : UiState> internal constructor(
    private val commit: (S.() -> S) -> Boolean,
    private val canCommit: () -> Boolean,
) {
    internal fun isCurrent(): Boolean = canCommit()

    /**
     * Applies a pure synchronous reducer only while this operation still owns its slot and its
     * Job/ViewModel are active. Returns false after replacement, cancellation, completion or clear.
     * Use this for results AND UI cleanup in finally; resource cleanup must always run.
     * Non-inline deliberately prevents suspension between the check and the state update.
     * Call on Main, including callbacks; this is not runtime thread synchronization.
     */
    @MainThread
    fun commitState(reducer: S.() -> S): Boolean = commit(reducer)
}
