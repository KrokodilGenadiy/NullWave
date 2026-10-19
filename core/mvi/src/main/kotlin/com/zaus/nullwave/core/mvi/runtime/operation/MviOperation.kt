package com.zaus.nullwave.core.mvi.runtime.operation

import androidx.annotation.MainThread
import com.zaus.nullwave.core.mvi.UiState

/**
 * Internal scheduler handle with one fixed policy. The definition adapter owns its lifetime and
 * request closures; feature code cannot submit arbitrary work or access this low-level contract.
 */
internal class MviOperation<S : UiState> internal constructor(
    private val submitRequest: ((S.() -> S)?, suspend OperationScope<S>.() -> Unit) -> Boolean,
    private val cancelRequests: ((S.() -> S)?, () -> Unit) -> Boolean,
) {
    /**
     * Accepts work according to this property's policy. Returns false if busy (idle policy), full
     * (queue policy) or cleared. True means accepted, not started, completed or successful: publishing
     * [onAccepted] can synchronously trigger a newer intent that replaces/cancels this request before
     * submit returns. Use [cancel] to cancel this property's work; request Jobs are private.
     *
     * [onAccepted] is a pure synchronous reducer applied after acceptance and revoking any replaced
     * request's commits, before cancellation callbacks or a new body for this property can run.
     * Rejected requests never run it. Capture immutable arguments before submitting; use
     * [OperationScope.commitState] for results and UI cleanup. Accepted is not necessarily running:
     * a pending request cancelled before execution never enters its body/finally. No automatic
     * rollback of accepted state occurs. A throwing reducer cancels its request and propagates;
     * previously replaced work is not restored.
     *
     * Never wait for a successor's result from its predecessor: the successor waits for that
     * predecessor to end, including structured children and cancellation cleanup.
     */
    @MainThread
    fun submit(
        onAccepted: (S.() -> S)? = null,
        block: suspend OperationScope<S>.() -> Unit,
    ): Boolean = submitRequest(onAccepted, block)

    /**
     * Revokes commits and cancels current/pending requests present at entry. [onCancelled] is a pure
     * reducer published after revocation, before cancelling their Jobs. Use it for explicit Cancel
     * UI state instead of a separate setState after cancellation, which could overwrite newer work.
     * Newly accepted work from state publication or cleanup survives; it still waits for the
     * predecessor's full completion. Idle policy still rejects submissions during that cleanup.
     * The property remains reusable.
     *
     * Returns false without invoking the reducer when idle, cleared or only already revoked/cancelled
     * work remains. Returns true when cancellation was requested, not when cleanup completed. If the
     * reducer throws, captured requests are still cancelled and the exception propagates. This
     * does not roll back repository writes or automatically change UI state without a reducer.
     */
    @MainThread
    fun cancel(onCancelled: (S.() -> S)? = null): Boolean = cancelRequests(onCancelled, {})

    /** Adapter hook after request revocation, including the idle case, before any cancel state. */
    @MainThread
    fun cancel(onCancelled: (S.() -> S)?, beforeState: () -> Unit): Boolean =
        cancelRequests(onCancelled, beforeState)
}
