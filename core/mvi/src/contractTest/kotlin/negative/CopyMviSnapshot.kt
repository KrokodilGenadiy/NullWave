// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.state.MviSnapshot

fun bypass(snapshot: MviSnapshot<ContractState>) = snapshot.copy(state = ContractState())
