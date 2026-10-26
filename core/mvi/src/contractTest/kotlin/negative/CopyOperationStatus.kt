// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.state.OperationStatus

fun bypass(status: OperationStatus) = status.copy(isBusy = false)
