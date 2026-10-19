// EXPECT_DIAGNOSTIC: UNRESOLVED_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.MviViewModel

private fun bypass(viewModel: MviViewModel<ContractState, ContractIntent, ContractEffect>) =
    viewModel.effects
