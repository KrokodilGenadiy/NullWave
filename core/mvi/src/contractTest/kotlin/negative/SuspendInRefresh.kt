// EXPECT_DIAGNOSTIC: ILLEGAL_SUSPEND_FUNCTION_CALL
package contract

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

class SuspendInRefresh : BaseMviViewModel<ContractState, ContractIntent, ContractEffect>(ContractState()) {
    override fun MviDefinition<ContractState, ContractIntent, ContractEffect>.define() {
        val source = source("data", flow = { flowOf(1) }) { copy(value = it) }
        on<ContractIntent.Reset> {
            refresh(source) {
                delay(1)
                this
            }
        }
    }
}
