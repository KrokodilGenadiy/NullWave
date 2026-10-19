package com.zaus.nullwave.core.mvi.runtime.operation

import kotlinx.coroutines.CancellationException

/**
 * Keeps the work failure when finalization also fails. A non-cancellation finalizer failure must
 * remain observable even when the work was cancelled; cancellation alone is not a reported fault.
 */
internal inline fun <T> withOperationFinalization(block: () -> T, finalizer: () -> Unit): T {
    var primary: Throwable? = null
    try {
        return block()
    } catch (failure: Throwable) {
        primary = failure
        throw failure
    } finally {
        try {
            finalizer()
        } catch (failure: Throwable) {
            val original = primary
            when {
                original == null -> throw failure
                original === failure -> Unit
                original is CancellationException && failure !is CancellationException -> {
                    failure.addSuppressed(original)
                    throw failure
                }
                else -> original.addSuppressed(failure)
            }
        }
    }
}
