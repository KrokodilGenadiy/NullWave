// EXPECT_DIAGNOSTIC: UNRESOLVED_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition

class DirectStateWrite : BaseMviViewModel<ContractState, ContractIntent, ContractEffect>(ContractState()) {
    override fun MviDefinition<ContractState, ContractIntent, ContractEffect>.define() = Unit

    fun bypass() {
        setState { this }
    }
}
