package com.zaus.nullwave.core.mvi.dsl

import com.zaus.nullwave.core.mvi.UiState

/**
 * A source declared by one ViewModel. Input changes and binding after a stop collect it automatically.
 * Use IntentScope.refresh for an explicit reread even if the input is unchanged, such as Retry.
 * The handle exposes no collection or mutation API outside a live intent handler.
 */
class Source<S : UiState> internal constructor(
    internal val owner: Any,
    private val requestRefresh: (S.() -> S) -> Unit,
) {
    internal fun refresh(reducer: S.() -> S) = requestRefresh(reducer)
}
