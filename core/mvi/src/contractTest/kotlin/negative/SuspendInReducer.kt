// EXPECT_DIAGNOSTIC: ILLEGAL_SUSPEND_FUNCTION_CALL
package contract

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import kotlinx.coroutines.delay

class SuspendInReducer : BaseMviViewModel<ContractState, ContractIntent, ContractEffect>(ContractState()) {
    override fun MviDefinition<ContractState, ContractIntent, ContractEffect>.define() {
        on<ContractIntent.Load> {
            reduce {
                delay(1)
                this
            }
        }
    }
}
