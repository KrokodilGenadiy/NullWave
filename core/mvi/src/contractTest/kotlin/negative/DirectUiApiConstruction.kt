// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.MviViewModel
import com.zaus.nullwave.core.mvi.ui.MviUiApi

private fun bypass(vm: MviViewModel<ContractState, ContractIntent, ContractEffect>) =
    MviUiApi<ContractState, ContractIntent>(vm.snapshot) {}
