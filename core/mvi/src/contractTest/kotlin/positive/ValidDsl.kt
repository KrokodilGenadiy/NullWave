package contract

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

class ValidDsl : BaseMviViewModel<ContractState, ContractIntent, ContractEffect>(ContractState()) {
    override fun MviDefinition<ContractState, ContractIntent, ContractEffect>.define() {
        val load = latest<Int, Int>("load") {
            execute { input ->
                delay(1)
                input * 2
            }
            onAccepted { _ -> copy(loading = true) }
            onResult { _, result -> copy(value = result) }
            onFinished { _ -> copy(loading = false) }
            onCancelled { copy(loading = false) }
            effect { _, result -> ContractEffect.Loaded(result) }
        }
        queued<Int, Int>("save", maxPending = 2) {
            execute { it }
            onResult { _, result -> copy(value = result) }
        }
        idle<Unit, Int>("refresh") {
            execute { 1 }
            onResult { _, result -> copy(value = result) }
        }
        on<ContractIntent.Load> { intent -> submit(load, intent.value) }
        on<ContractIntent.Reset> {
            cancel(load)
            reduce { copy(value = 0) }
            tryEffect(ContractEffect.Loaded(0))
            restartSources()
        }
        source("counter", flow = { flowOf(1) }) { value -> copy(value = value) }
    }
}
