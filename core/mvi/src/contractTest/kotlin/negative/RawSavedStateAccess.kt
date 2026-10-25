// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.persistence.MviSavedState

private fun bypass(binding: MviSavedState<ContractState>) = binding.attach(ContractState())
