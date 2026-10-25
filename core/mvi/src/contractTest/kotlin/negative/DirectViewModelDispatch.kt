// EXPECT_DIAGNOSTIC: UNRESOLVED_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition

class DirectViewModelDispatch : BaseMviViewModel<ContractState, ContractIntent, ContractEffect>(ContractState()) {
    override fun MviDefinition<ContractState, ContractIntent, ContractEffect>.define() {
        val load = latest<Unit, Unit>("load") {
            execute { this@DirectViewModelDispatch.onIntent(ContractIntent.Reset) }
        }
        on<ContractIntent.Reset> { submit(load, Unit) }
    }
}
