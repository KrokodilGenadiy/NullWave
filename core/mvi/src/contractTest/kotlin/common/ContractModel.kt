package contract

import com.zaus.nullwave.core.mvi.UiEffect
import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.mvi.UiState

data class ContractState(val value: Int = 0, val loading: Boolean = false) : UiState

sealed interface ContractIntent : UiIntent {
    data class Load(val value: Int) : ContractIntent
    data object Reset : ContractIntent
}

sealed interface ContractEffect : UiEffect {
    data class Loaded(val value: Int) : ContractEffect
}
