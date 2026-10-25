package com.zaus.nullwave.core.mvi.runtime.source

import com.zaus.nullwave.core.mvi.UiEffect
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.runtime.MviAccess
import com.zaus.nullwave.core.mvi.runtime.MviRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/** A declared source owns its input revision and publishes only through the runtime. */
internal abstract class MviSource<S : UiState> {
    abstract fun initialize(state: S)
    abstract fun prepare(state: S, sourceName: String?, refreshName: String?): PreparedSourceChanges
    abstract suspend fun collect()
}

// Identity is deliberate: A -> B -> A is a new request even when keys compare equal again.
private class MviSourceRevision<K>(val key: K)

internal fun <S : UiState, E : UiEffect, K, T> keyedMviSource(
    runtime: MviRuntime<S, E>,
    name: String,
    input: S.() -> K,
    flow: (K) -> Flow<T>,
    reducer: S.(K, T) -> S,
): MviSource<S> = object : MviSource<S>() {
    private var current: MviSourceRevision<K>? = null
    private lateinit var revisions: MutableStateFlow<MviSourceRevision<K>>

    override fun initialize(state: S) {
        check(current == null) { "An MVI source can only be initialized once." }
        val revision = MviSourceRevision(MviAccess.reducing(runtime) { input(state) })
        current = revision
        revisions = MutableStateFlow(revision)
    }

    override fun prepare(state: S, sourceName: String?, refreshName: String?): PreparedSourceChanges {
        val previous = checkNotNull(current) { "The MVI source has not been initialized." }
        val next = MviAccess.reducing(runtime) {
            val key = input(state)
            if (key == previous.key && refreshName != name) previous else MviSourceRevision(key)
        }
        if (next === previous) return PreparedSourceChanges.None
        check(sourceName == null) {
            "Source '$sourceName' cannot change input of source '$name'. " +
                "Update query inputs from an intent/operation or compose dependent repository flows."
        }

        // Every selector is evaluated before any source commits its invalidation. A failing selector
        // therefore leaves the old State and every source revision intact.
        return PreparedSourceChanges(
            commit = { current = next },
            notifyCollectors = {
                // Notify only the revision still owned by this source.
                if (current === next) revisions.value = next
            },
        )
    }

    override suspend fun collect() {
        check(current != null) { "The MVI source has not been initialized." }
        // Capture ownership before upstream can enter NonCancellable or replace its context.
        val groupJob = currentCoroutineContext().job
        val groupGeneration = runtime.sourceGeneration
        withContext(MviAccess.workContext(runtime)) {
            revisions.collectLatest { revision ->
                val requestJob = currentCoroutineContext().job
                // collectLatest may have waited for an older input's cleanup while this revision
                // was superseded again. Never start work for that intermediate input.
                if (
                    !runtime.isOpen || !groupJob.isActive || !requestJob.isActive ||
                    runtime.sourceGeneration !== groupGeneration || current !== revision
                ) return@collectLatest
                flow(revision.key).collect { value ->
                    withContext(Dispatchers.Main.immediate) {
                        if (
                            runtime.isOpen && groupJob.isActive && requestJob.isActive &&
                            runtime.sourceGeneration === groupGeneration && current === revision
                        ) {
                            MviAccess.publishing(runtime) {
                                runtime.updateFromSource(name) { reducer(revision.key, value) }
                            }
                        }
                    }
                }
            }
        }
    }
}
