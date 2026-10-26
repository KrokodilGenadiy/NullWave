// EXPECT_DIAGNOSTIC: VAL_REASSIGNMENT
package contract

import com.zaus.nullwave.core.mvi.state.OperationStatus

fun bypass(status: OperationStatus) {
    status.isBusy = false
}
