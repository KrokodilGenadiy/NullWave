package com.zaus.nullwave.core.mvi.dsl

import com.zaus.nullwave.core.mvi.UiEffect
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.runtime.MviAccess
import com.zaus.nullwave.core.mvi.runtime.MviRuntime

/** Synchronous capabilities belonging to one handler invocation; never retain this receiver. */
@MviDsl
class IntentScope<S : UiState, E : UiEffect> internal constructor(
    private val runtime: MviRuntime<S, E>,
    private val owner: Any,
) {
    private var open = true

    /** Current immutable snapshot. Capture the required input before submitting asynchronous work. */
    val state: S
        get() {
            checkOpen()
            return runtime.state
        }

    /**
     * Computes and validates the next State, saves selected inputs if configured, then publishes it
     * and notifies changed sources. Keep this transform pure and synchronous; submitting work belongs
     * outside it. Configure BaseMviViewModel.savedState once instead of manually saving in handlers.
     * Intents from observers wait for this entire handler to return, including work after reduce.
     */
    fun reduce(reducer: S.() -> S) {
        checkOpen()
        runtime.update(reducer)
    }

    /** True means accepted, not completed; false means the queue is full or the idle slot is busy. */
    fun <A> submit(operation: Operation<A>, input: A): Boolean {
        checkOwner(operation)
        // Main.immediate may invoke UI observers before the scheduler returns. They may enqueue
        // external intents, even though this handler keeps ownership until it returns.
        return MviAccess.publishing(runtime) { operation.submit(input) }
    }

    /** Revokes buffered operation effects too; Boolean reports whether unfinished work was cancelled. */
    fun <A> cancel(operation: Operation<A>): Boolean {
        checkOwner(operation)
        return MviAccess.publishing(runtime) { operation.cancel() }
    }

    /** Optional signal. False means the bounded buffer was full; required outcomes belong in State. */
    fun tryEffect(effect: E): Boolean {
        checkOpen()
        return runtime.offerEffect(effect)
    }

    /**
     * Updates State and invalidates this source in one publication, then notifies its collector.
     * Use for Retry/Refresh/fresh access checks; ordinary input edits already restart automatically.
     * A changed input plus refresh causes one invalidation. Stopped sources wait for a UI binding;
     * a still-active stop grace period permits collection, just like ordinary input changes.
     */
    fun refresh(source: Source<S>, reducer: S.() -> S = { this }) {
        checkOpen()
        check(source.owner === owner) { "An MVI source belongs to a different ViewModel." }
        source.refresh(reducer)
    }

    internal fun close() {
        open = false
    }

    private fun checkOwner(operation: Operation<*>) {
        checkOpen()
        check(operation.owner === owner) { "An MVI operation belongs to a different ViewModel." }
    }

    private fun checkOpen() {
        MviAccess.checkIntentScope(runtime, this)
        check(open && runtime.isOpen) { "The intent handler is no longer active." }
    }
}
