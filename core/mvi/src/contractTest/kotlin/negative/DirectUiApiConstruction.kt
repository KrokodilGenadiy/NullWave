// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.ui.MviUiApi
import kotlinx.coroutines.flow.MutableStateFlow

private fun bypass() = MviUiApi<ContractState, ContractIntent>(MutableStateFlow(ContractState())) {}
