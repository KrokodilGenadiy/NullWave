package contract

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.dsl.OperationKey

val Save = OperationKey<Int>("save")

class ValidOperationSnapshot : BaseMviViewModel<ContractState, ContractIntent, ContractEffect>(ContractState()) {
    override fun MviDefinition<ContractState, ContractIntent, ContractEffect>.define() {
        val save = idle<Int, Int>(Save) {
            execute { it }
            onResult { _, result -> copy(value = result) }
        }
        on<ContractIntent.Load> { submit(save, it.value) }
        on<ContractIntent.Reset> { cancel(save) }
    }
}

fun canSave(vm: ValidOperationSnapshot): Boolean = vm.snapshot.value.operations[Save].canSubmit
