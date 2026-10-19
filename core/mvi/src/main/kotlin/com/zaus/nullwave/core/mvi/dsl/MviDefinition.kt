package com.zaus.nullwave.core.mvi.dsl

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.UiEffect
import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.runtime.MviAccess
import com.zaus.nullwave.core.mvi.runtime.MviRuntime
import com.zaus.nullwave.core.mvi.runtime.operation.OperationPolicy
import com.zaus.nullwave.core.mvi.runtime.source.MviSource
import com.zaus.nullwave.core.mvi.runtime.source.keyedMviSource
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * A screen's immutable behavior declaration. Register exact intent types, typed operations and
 * subscriptions here. This receiver expires when [BaseMviViewModel.define] returns.
 */
@MviDsl
class MviDefinition<S : UiState, I : UiIntent, E : UiEffect> internal constructor(
    private val runtime: MviRuntime<S, E>,
) {
    private val owner = Any()
    private var open = true
    private val names = mutableSetOf<String>()
    private val handlers = mutableMapOf<Class<*>, IntentScope<S, E>.(I) -> Unit>()
    private val sources = mutableListOf<MviSource<S>>()
    private var validator: ((S) -> Unit)? = null

    /** New input revokes the previous request; execution waits for its structured cleanup. */
    fun <A, R> latest(
        name: String,
        block: OperationBuilder<S, E, A, R>.() -> Unit,
    ): Operation<A> = operation(name, OperationPolicy.Latest, 0, block)

    /** FIFO with a bounded waiting queue, excluding the executing/reserved request. */
    fun <A, R> queued(
        name: String,
        maxPending: Int = 16,
        block: OperationBuilder<S, E, A, R>.() -> Unit,
    ): Operation<A> {
        require(maxPending > 0) { "maxPending must be positive." }
        return operation(name, OperationPolicy.Queued, maxPending, block)
    }

    /** Rejects new inputs until the previous execution and its cleanup have completed. */
    fun <A, R> idle(
        name: String,
        block: OperationBuilder<S, E, A, R>.() -> Unit,
    ): Operation<A> = operation(name, OperationPolicy.Idle, 0, block)

    /** Exact runtime type matching: every concrete intent needs one handler. */
    inline fun <reified T : I> on(noinline handler: IntentScope<S, E>.(T) -> Unit) {
        on(T::class, handler)
    }

    fun <T : I> on(type: KClass<T>, handler: IntentScope<S, E>.(T) -> Unit) {
        checkOpen()
        val key = type.java
        require(!key.isInterface && !Modifier.isAbstract(key.modifiers)) {
            "Intent '${key.simpleName}' must be a concrete class: handlers match exact runtime types."
        }
        check(key !in handlers) { "Intent '${key.simpleName}' already has a handler." }
        handlers[key] = { intent -> handler(checkNotNull(key.cast(intent))) }
    }

    /** A subscription independent of State inputs, active only while the UI is bound. */
    fun <T> source(name: String, flow: () -> Flow<T>, reducer: S.(T) -> S) {
        source<Unit, T>(name, input = {}, flow = { flow() }) { _, value -> reducer(value) }
    }

    /**
     * A subscription keyed by immutable State inputs. Input changes immediately revoke old results,
     * including A -> B -> A, before asynchronous cancellation/collection catches up.
     * Select the complete request: [flow] must receive every State-dependent query argument in [K].
     * The [reducer] may update data, but cannot change any declared source's selected input.
     */
    fun <K, T> source(
        name: String,
        input: S.() -> K,
        flow: (K) -> Flow<T>,
        reducer: S.(K, T) -> S,
    ) {
        registerName(name)
        sources += keyedMviSource(runtime, name, input, flow, reducer)
    }

    /** Optional screen invariant checked on the initial State and before every State publication. */
    fun validateState(validator: (S) -> Unit) {
        checkOpen()
        check(this.validator == null) { "State validation is already declared." }
        this.validator = validator
    }

    internal fun seal() {
        checkOpen()
        MviAccess.reducing(runtime) {
            validator?.invoke(runtime.state.value)
            sources.forEach { it.initialize(runtime.state.value) }
        }
        runtime.prepareStateChange = { next, sourceName ->
            validator?.invoke(next)
            // Evaluate all user selectors first, so a failing selector cannot partially invalidate
            // other sources while the previous State remains published.
            val prepared = sources.map { it.prepare(next, sourceName) }
            val notifications = prepared.map { it() }
            val notify: () -> Unit = { notifications.forEach { it() } }
            notify
        }
        open = false
    }

    internal fun abort() {
        open = false
    }

    internal fun dispatch(intent: I) {
        check(!open) { "The MVI definition has not been sealed." }
        val handler = checkNotNull(handlers[intent.javaClass]) {
            "No handler is registered for intent '${intent.javaClass.simpleName}'."
        }
        val scope = IntentScope(runtime, owner)
        try {
            MviAccess.handling(runtime, scope) { handler(scope, intent) }
        } finally {
            scope.close()
        }
    }

    internal suspend fun observe(): Unit = coroutineScope {
        sources.forEach { source -> launch { source.collect() } }
    }

    private fun <A, R> operation(
        name: String,
        policy: OperationPolicy,
        maxPending: Int,
        block: OperationBuilder<S, E, A, R>.() -> Unit,
    ): Operation<A> {
        registerName(name)
        val builder = OperationBuilder<S, E, A, R>(runtime)
        return try {
            builder.block()
            builder.build(owner, runtime, policy, maxPending)
        } finally {
            builder.close()
        }
    }

    private fun registerName(name: String) {
        checkOpen()
        require(name.isNotBlank()) { "MVI declaration names must not be blank." }
        check(names.add(name)) { "MVI declaration '$name' already exists." }
    }

    private fun checkOpen() {
        MviAccess.checkDefinitionScope(runtime)
        check(open) { "MVI declarations are only available while defining the screen." }
    }
}
