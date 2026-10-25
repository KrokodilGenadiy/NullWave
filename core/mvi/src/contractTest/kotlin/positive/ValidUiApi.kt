package contract

import com.zaus.nullwave.core.mvi.MviViewModel

/** A non-Compose host acquires its capability before installing ordinary UI callbacks. */
suspend fun connectUi(
    vm: MviViewModel<ContractState, ContractIntent, ContractEffect>,
    installCallback: ((ContractIntent) -> Unit) -> Unit,
) {
    val ui = vm.uiApi()
    installCallback(ui.onIntent)
    vm.withUiBinding { ui.state.collect {} }
}
