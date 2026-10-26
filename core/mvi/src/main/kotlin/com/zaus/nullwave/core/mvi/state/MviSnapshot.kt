package com.zaus.nullwave.core.mvi.state

import com.zaus.nullwave.core.mvi.UiState

/** One canonical publication: feature data and runtime-owned operation activity. */
@ConsistentCopyVisibility
data class MviSnapshot<out S : UiState> internal constructor(
    val state: S,
    val operations: OperationStates,
)
