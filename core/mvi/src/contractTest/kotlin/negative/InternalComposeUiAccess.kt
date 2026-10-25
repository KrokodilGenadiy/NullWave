// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.BaseMviViewModel

private fun bypass(vm: BaseMviViewModel<ContractState, ContractIntent, ContractEffect>) = vm.uiApiForCompose()
