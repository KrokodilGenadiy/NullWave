// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.state.OperationStatus

fun bypass() = OperationStatus(false, false, 0, true)
