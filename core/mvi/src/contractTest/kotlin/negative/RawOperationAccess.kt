// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.runtime.operation.MviOperation

private fun bypass(operation: MviOperation<ContractState>) = operation.cancel()
