package com.zaus.nullwave.core.mvi.dsl

import com.zaus.nullwave.core.mvi.UiEffect
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.runtime.MviAccess
import com.zaus.nullwave.core.mvi.runtime.MviRuntime
import com.zaus.nullwave.core.mvi.runtime.effect.EffectEpoch
import com.zaus.nullwave.core.mvi.runtime.operation.OperationPolicy
import com.zaus.nullwave.core.mvi.runtime.operation.withOperationFinalization
import kotlinx.coroutines.withContext

/**
 * Defines one operation's work and state transitions. Every clause may be declared once, and
 * [execute] is required. The builder closes when its definition callback returns, even on failure.
 * Retaining it cannot change an already declared operation.
 *
 * Execution has no mutable-state receiver. It receives an input and returns a result; the runtime
 * applies pure reducers only while the request still owns its slot. Expected domain failures can
 * be represented by the result type. Cancellation and unexpected exceptions propagate.
 */
@MviDsl
class OperationBuilder<S : UiState, E : UiEffect, A, R> internal constructor(private val scopeOwner: Any) {
    private var open = true
    private var built = false
    private var execution: (suspend (A) -> R)? = null
    private var accepted: (S.(A) -> S)? = null
    private var started: (S.(A) -> S)? = null
    private var result: (S.(A, R) -> S)? = null
    private var finished: (S.(A) -> S)? = null
    private var cancelled: (S.() -> S)? = null
    private var effectMapper: ((A, R) -> E?)? = null

    /** Required suspending work. Use structured concurrency and return the meaningful outcome. */
    fun execute(block: suspend (A) -> R) {
        checkClause("execute", execution != null)
        execution = block
    }

    /** Runs synchronously for accepted inputs, including those still waiting in the queue. */
    fun onAccepted(reducer: S.(A) -> S) {
        checkClause("onAccepted", accepted != null)
        accepted = reducer
    }

    /** Runs immediately before execution; rejected or cancelled pending inputs never enter it. */
    fun onStarted(reducer: S.(A) -> S) {
        checkClause("onStarted", started != null)
        started = reducer
    }

    /** Commits a completed result only while its request remains current. */
    fun onResult(reducer: S.(A, R) -> S) {
        checkClause("onResult", result != null)
        result = reducer
    }

    /**
     * Finalizes a started request while it still owns its slot, including when its execution cancels
     * itself or fails. Replacement, explicit cancellation and VM clear revoke this reducer; use
     * [onCancelled] for explicit cancellation state. Pending inputs never enter this finalizer.
     */
    fun onFinished(reducer: S.(A) -> S) {
        checkClause("onFinished", finished != null)
        finished = reducer
    }

    /** Runs once when explicit cancellation revokes current/pending work, before its cleanup. */
    fun onCancelled(reducer: S.() -> S) {
        checkClause("onCancelled", cancelled != null)
        cancelled = reducer
    }

    /**
     * Pure optional notification mapping, evaluated after result and finalization reducers.
     * A null result emits nothing. Delivery is bounded and best-effort; required outcomes belong in
     * state/data. Latest submissions and explicit cancellation invalidate older buffered effects
     * and cancel their managed UI handler, if it is still running.
     */
    fun effect(mapper: (A, R) -> E?) {
        checkClause("effect", effectMapper != null)
        effectMapper = mapper
    }

    internal fun build(
        owner: Any,
        runtime: MviRuntime<S, E>,
        policy: OperationPolicy,
        maxPending: Int,
    ): Operation<A> {
        MviAccess.checkDefinitionScope(scopeOwner)
        check(!built) { "An MVI operation builder can only be built once." }
        if (policy == OperationPolicy.Queued) {
            require(maxPending > 0) { "A queued MVI operation requires positive maxPending." }
        }
        val execute = checkNotNull(execution) { "An MVI operation must declare execute { ... }." }
        val onAccepted = accepted
        val onStarted = started
        val onResult = result
        val onFinished = finished
        val onCancelled = cancelled
        val toEffect = effectMapper
        built = true

        val scheduled = when (policy) {
            OperationPolicy.Latest -> runtime.latestOperation()
            OperationPolicy.Queued -> runtime.queuedOperation(maxPending)
            OperationPolicy.Idle -> runtime.idleOperation()
        }
        var effectEpoch = EffectEpoch()
        return Operation(
            owner = owner,
            submitRequest = request@ { input ->
                // Rotate before admission can publish state and synchronously submit newer work.
                val requestEpoch = if (policy == OperationPolicy.Latest) {
                    val previous = effectEpoch
                    EffectEpoch().also {
                        effectEpoch = it
                        runtime.revokeEffects(previous)
                    }
                } else {
                    effectEpoch
                }
                if (!requestEpoch.isCurrent) return@request false
                val acceptance: (S.() -> S)? = onAccepted?.let { reducer ->
                    { reducer(input) }
                }
                scheduled.submit(onAccepted = acceptance) {
                    val value = withOperationFinalization(block = {
                        if (onStarted != null && !commitState { onStarted(input) }) {
                            return@submit
                        }
                        // A child scope lets execution cancel itself without bypassing finalization
                        // in the still-current outer request. The context also forbids DSL writes.
                        val executed = withContext(MviAccess.workContext(runtime)) { execute(input) }
                        if (onResult != null) commitState { onResult(input, executed) }
                        executed
                    }, finalizer = {
                        if (onFinished != null) commitState { onFinished(input) }
                    })
                    if (isCurrent() && toEffect != null) {
                        val effect = MviAccess.reducing(runtime) { toEffect(input, value) }
                        if (effect != null && isCurrent()) {
                            runtime.sendEffect(effect, requestEpoch)
                        }
                    }
                }
            },
            cancelRequests = {
                // Also invalidate effects from completed requests. The return value describes work
                // cancellation only, so invalidating buffered effects alone still returns false.
                runtime.deferIntents {
                    val previous = effectEpoch
                    effectEpoch = EffectEpoch()
                    previous.revoke()
                    scheduled.cancel(onCancelled, beforeState = {
                        // Requests already lost their write rights. Cancel the handler before State
                        // publication; cleanup-submitted intents wait until this transition ends.
                        runtime.revokeEffects(previous)
                    })
                }
            },
        )
    }

    internal fun close() {
        MviAccess.checkDefinitionScope(scopeOwner)
        open = false
    }

    private fun checkClause(name: String, alreadyDeclared: Boolean) {
        MviAccess.checkDefinitionScope(scopeOwner)
        check(open && !built) { "MVI operation clauses are only available inside their definition." }
        check(!alreadyDeclared) { "MVI operation clause '$name' was declared more than once." }
    }
}
