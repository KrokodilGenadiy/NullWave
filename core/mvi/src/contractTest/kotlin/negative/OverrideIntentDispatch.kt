// EXPECT_DIAGNOSTIC: NOTHING_TO_OVERRIDE
package contract

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition

class OverrideIntentDispatch : BaseMviViewModel<ContractState, ContractIntent, ContractEffect>(ContractState()) {
    override fun MviDefinition<ContractState, ContractIntent, ContractEffect>.define() = Unit

    override fun onIntent(intent: ContractIntent) = Unit
}
