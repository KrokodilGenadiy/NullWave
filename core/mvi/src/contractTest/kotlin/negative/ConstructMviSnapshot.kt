// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.state.MviSnapshot
import com.zaus.nullwave.core.mvi.state.OperationStates

fun bypass(operations: OperationStates) = MviSnapshot(ContractState(), operations)
