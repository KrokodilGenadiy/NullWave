package com.zaus.nullwave.core.mvi.state

/**
 * Read-only activity, not a business outcome or a claim that external writes were rolled back.
 * Busy includes cancellation cleanup. Executing means a started, still-current request, including
 * its result/finalization; revoked cleanup is busy but not executing. Pending counts accepted inputs
 * that have not started, including a reserved first input. canSubmit is advisory; submit is decisive.
 */
@ConsistentCopyVisibility
data class OperationStatus internal constructor(
    val isBusy: Boolean,
    val isExecuting: Boolean,
    val pendingCount: Int,
    val canSubmit: Boolean,
) {
    internal companion object {
        val Unavailable = OperationStatus(false, false, 0, false)
    }
}
