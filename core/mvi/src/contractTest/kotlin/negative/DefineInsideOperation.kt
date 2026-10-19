// EXPECT_DIAGNOSTIC: DSL_SCOPE_VIOLATION
package contract

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition

class DefineInsideOperation : BaseMviViewModel<ContractState, ContractIntent, ContractEffect>(ContractState()) {
    override fun MviDefinition<ContractState, ContractIntent, ContractEffect>.define() {
        latest<Int, Int>("load") {
            on<ContractIntent.Load> { }
            execute { it }
        }
    }
}
