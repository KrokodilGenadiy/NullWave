package com.zaus.nullwave.core.mvi.ui

import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.state.MviSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * UI/platform capability for one ViewModel. Obtaining it does not initialize the definition or
 * bind sources. It does not own the ViewModel; callbacks become no-ops after ViewModel clear.
 *
 * Keep it at the UI boundary. Deliberately passing its callback into work grants that work the
 * same capability; this API is not a sandbox for arbitrary captured Kotlin functions.
 */
class MviUiApi<S : UiState, I : UiIntent> internal constructor(
    val snapshot: StateFlow<MviSnapshot<S>>,
    /**
     * Admits intents in FIFO order on Main. Idle Main calls run immediately; calls during a handler,
     * publication or cancellation wait for that boundary. Queued callers cannot read [snapshot] as
     * immediate confirmation of processing. No caller ThreadLocal is used to authorize dispatch.
     */
    val onIntent: (I) -> Unit,
)
