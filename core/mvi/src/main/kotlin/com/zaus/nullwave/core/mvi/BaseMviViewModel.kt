package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.persistence.MviSavedState
import com.zaus.nullwave.core.mvi.runtime.MviAccess
import com.zaus.nullwave.core.mvi.runtime.MviRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Define screen behavior once in [define]. The final dispatcher and private runtime own all writes,
 * operation policies and source lifetimes; feature code receives only scoped DSL capabilities.
 *
 * The definition is initialized on first intent, effect collection or UI binding, after subclass
 * construction. Reading [state] does not initialize it or start sources. Definition errors are sticky.
 * Commands outlive UI bindings; sources share one group until the last binding's stop grace expires.
 * Optional [savedState] restores selected inputs before the initial State is exposed and saves each
 * validated transition before publication. It is independent of UI binding and owns its handle key.
 */
abstract class BaseMviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
    sourceStopTimeoutMillis: Long = 5_000,
    savedState: MviSavedState<S>? = null,
) : ViewModel(), MviViewModel<S, I, E> {
    private var definition: MviDefinition<S, I, E>? = null
    @Volatile private var definitionFailure: Throwable? = null
    @Volatile private var cleared = false
    private var defining = false
    private val persistence = savedState?.attach(initialState)
    private val runtimeScope = viewModelScope
    private val runtime = MviRuntime<S, E>(
        initialState = persistence?.initialState ?: initialState,
        scope = runtimeScope,
        sourceStopTimeoutMillis = sourceStopTimeoutMillis,
        savedState = persistence,
        observeSources = { requireDefinition().observe() },
    )

    init {
        addCloseable {
            cleared = true
            runtime.close()
        }
    }

    final override val state: StateFlow<S> = runtime.state
    final override suspend fun collectEffects(onEffect: suspend (E) -> Unit) {
        if (cleared || !runtimeScope.isActive) return
        MviAccess.checkUiEntryAllowed()
        withContext(Dispatchers.Main.immediate) {
            currentCoroutineContext().ensureActive()
            if (!runtime.isOpen) return@withContext
            requireDefinition()
            runtime.collectEffects(onEffect)
        }
    }

    protected abstract fun MviDefinition<S, I, E>.define()

    final override fun onIntent(intent: I) {
        // Lifecycle cancels this scope before invoking our unkeyed closeable. Cleanup callbacks
        // can reenter here during that interval, before the explicit cleared flag becomes true.
        if (cleared || !runtimeScope.isActive) return
        MviAccess.checkDispatchAllowed(runtime)
        definitionFailure?.let { throw it }
        runtime.dispatch {
            if (cleared) return@dispatch
            val schema = requireDefinition()
            if (!runtime.isOpen) return@dispatch
            schema.dispatch(intent)
        }
    }

    final override suspend fun withUiBinding(block: suspend () -> Unit) {
        if (cleared || !runtimeScope.isActive) return
        MviAccess.checkUiEntryAllowed()
        withContext(Dispatchers.Main.immediate) {
            currentCoroutineContext().ensureActive()
            if (!runtime.isOpen) return@withContext
            requireDefinition()
            runtime.withUiBinding(block)
        }
    }

    private fun requireDefinition(): MviDefinition<S, I, E> {
        definitionFailure?.let { throw it }
        definition?.let { return it }
        check(!defining) { "MVI definition cannot be entered recursively." }
        check(runtime.isOpen) { "Cannot define a cleared ViewModel." }
        defining = true
        val candidate = MviDefinition<S, I, E>(runtime)
        try {
            runtime.deferIntents {
                MviAccess.defining(runtime) {
                    with(candidate) { define() }
                    candidate.seal()
                    runtime.initializeSavedState()
                }
                definition = candidate
            }
            return candidate
        } catch (failure: Throwable) {
            definitionFailure = failure
            candidate.abort()
            runtime.close()
            throw failure
        } finally {
            defining = false
        }
    }
}
