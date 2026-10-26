// EXPECT_DIAGNOSTIC: VAL_REASSIGNMENT
package contract

import com.zaus.nullwave.core.mvi.MviViewModel

fun bypass(vm: MviViewModel<ContractState, ContractIntent, ContractEffect>) {
    vm.snapshot.value = vm.snapshot.value
}
