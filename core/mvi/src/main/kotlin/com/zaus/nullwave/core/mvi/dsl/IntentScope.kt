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
            return runtime.state.value
        }

    fun reduce(reducer: S.() -> S) {
        checkOpen()
        runtime.update(reducer)
    }

    /** True means accepted, not completed; false means the queue is full or the idle slot is busy. */
    fun <A> submit(operation: Operation<A>, input: A): Boolean {
        checkOwner(operation)
        // Main.immediate may drain queued UI observers before returning from the scheduler.
        // They are external callers, even if execution began inside this intent handler.
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

    fun restartSources() {
        checkOpen()
        MviAccess.publishing(runtime) { runtime.restartSources() }
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
