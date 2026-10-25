package contract

import androidx.lifecycle.SavedStateHandle
import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.persistence.savedState
import kotlinx.serialization.builtins.serializer

class ValidSavedState(handle: SavedStateHandle) : BaseMviViewModel<ContractState, ContractIntent, ContractEffect>(
    initialState = ContractState(),
    savedState = savedState(handle, "counter.inputs", Int.serializer(),
        select = { value }, restore = { copy(value = it) }),
) {
    override fun MviDefinition<ContractState, ContractIntent, ContractEffect>.define() {
        on<ContractIntent.Reset> { reduce { copy(value = 0) } }
    }
}
