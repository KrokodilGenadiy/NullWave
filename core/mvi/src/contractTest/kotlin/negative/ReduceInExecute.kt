// EXPECT_DIAGNOSTIC: UNRESOLVED_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition

class ReduceInExecute : BaseMviViewModel<ContractState, ContractIntent, ContractEffect>(ContractState()) {
    override fun MviDefinition<ContractState, ContractIntent, ContractEffect>.define() {
        latest<Int, Int>("load") {
            execute { input ->
                reduce { this }
                input
            }
        }
    }
}
