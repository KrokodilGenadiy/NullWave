package com.zaus.nullwave.core.mvi.runtime

import com.zaus.nullwave.core.mvi.UiEffect
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.dsl.OperationKey
import com.zaus.nullwave.core.mvi.persistence.PreparedSavedState
import com.zaus.nullwave.core.mvi.persistence.SavedStateBinding
import com.zaus.nullwave.core.mvi.runtime.effect.EffectEpoch
import com.zaus.nullwave.core.mvi.runtime.effect.EffectMailbox
import com.zaus.nullwave.core.mvi.runtime.operation.MviOperation
import com.zaus.nullwave.core.mvi.runtime.operation.OperationRunner
import com.zaus.nullwave.core.mvi.runtime.source.PreparedSourceChanges
import com.zaus.nullwave.core.mvi.state.MviSnapshot
import com.zaus.nullwave.core.mvi.state.OperationStates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The shared, Main-confined engine behind the public definition-based contract. */
internal class MviRuntime<S : UiState, E : UiEffect>(
    initialState: S,
    scope: CoroutineScope,
    sourceStopTimeoutMillis: Long = 5_000,
    private val savedState: SavedStateBinding<S>? = null,
    observeSources: suspend () -> Unit,
) : AutoCloseable {
    // Preserve the owner's Job/dispatcher. Every runtime child starts and resumes in a known phase;
    // execute/source install their own Work context only around user work.
    private val runtimeScope = CoroutineScope(scope.coroutineContext + MviAccess.neutralContext())
    private val mutableSnapshot = MutableStateFlow(MviSnapshot(initialState, OperationStates.Uninitialized))
    private val intentIngress = MainIntentIngress(runtimeScope)
    val snapshot: StateFlow<MviSnapshot<S>> = mutableSnapshot.asStateFlow()
    val state: S get() = mutableSnapshot.value.state
    private val activeUiBindings = MutableStateFlow(0)
    private val effectMailbox = EffectMailbox<E>()
    private var closed = false
    private var writingSavedState = false
    val isOpen: Boolean get() = !closed && runtimeScope.isActive
    private var definitionReady = false
    private val operations = OperationRunner<S>(runtimeScope, ::update, ::publishActivity)

    /** Prepare input invalidation before publication; notify source collectors afterwards. */
    var prepareStateChange: ((S, String?, String?) -> PreparedSourceChanges)? = null

    suspend fun collectEffects(handler: suspend (E) -> Unit) = effectMailbox.collect(handler)

    private val sourceSharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = sourceStopTimeoutMillis)
    private val sourceController = runtimeScope.launch {
        sourceSharing.command(activeUiBindings).collectLatest { command ->
            if (command == SharingCommand.START) {
                // Lifecycle owns the group. A new START waits for the stopped group's cleanup;
                // explicit refresh is handled independently by each source's input revision.
                coroutineScope { observeSources() }
            }
        }
    }

    fun update(reducer: S.() -> S) {
        publish(sourceName = null, reducer)
    }

    /** Source output may update data, but cannot feed back into any declared source input. */
    fun updateFromSource(sourceName: String, reducer: S.() -> S) {
        publish(sourceName, reducer)
    }

    fun refreshSource(name: String, reducer: S.() -> S) {
        publish(sourceName = null, reducer, refreshName = name)
    }

    private fun publish(sourceName: String?, reducer: S.() -> S, refreshName: String? = null) {
        MviAccess.checkMutationAllowed(this)
        check(!writingSavedState) { "Saved-state observers cannot publish MVI State during persistence." }
        deferIntents {
            val next = MviAccess.reducing(this) { reducer(state) }
            val sources = MviAccess.reducing(this) { prepareStateChange?.invoke(next, sourceName, refreshName) }
            val saved = MviAccess.reducing(this) { savedState?.prepare(next) }
            writeSavedState(saved)
            if (!isOpen) return@deferIntents
            sources?.commit()
            MviAccess.publishing(this) {
                mutableSnapshot.value = MviSnapshot(next, activity())
                if (isOpen) sources?.notifyCollectors()
            }
        }
    }

    /** Called only after definition validation, before any sources or handlers can run. */
    fun initializeSavedState() {
        val prepared = MviAccess.reducing(this) { savedState?.prepare(state) }
        writeSavedState(prepared)
    }

    /** Called after the complete definition is installed; UI observers can safely enter it now. */
    fun activateOperations() {
        definitionReady = true
        publishActivity()
    }

    private fun activity(): OperationStates =
        if (definitionReady) operations.activity() else OperationStates.Uninitialized

    /** Activity-only transitions never run feature reducers, source selectors or persistence. */
    private fun publishActivity() {
        if (!definitionReady) return
        deferIntents {
            MviAccess.publishing(this) {
                mutableSnapshot.value = MviSnapshot(state, activity())
            }
        }
    }

    private fun writeSavedState(prepared: PreparedSavedState?) {
        if (prepared == null) return
        writingSavedState = true
        try {
            MviAccess.publishing(this) { prepared.write() }
        } catch (failure: Throwable) {
            // A storage observer may throw after the handle has already changed. There is no safe
            // two-object rollback: stop this runtime rather than continue with divergent snapshots.
            try {
                close()
            } catch (cleanup: Throwable) {
                if (cleanup !== failure) failure.addSuppressed(cleanup)
            }
            throw failure
        } finally {
            writingSavedState = false
        }
    }

    /** Main is immediate only when the ingress is idle and no earlier caller is waiting. */
    fun dispatch(block: () -> Unit) {
        intentIngress.dispatch(block)
    }

    /** Finish publication/cancellation before processing observer- or cleanup-triggered intents. */
    fun <T> deferIntents(block: () -> T): T = intentIngress.deferDispatch(block)

    suspend fun withUiBinding(block: suspend () -> Unit) = withContext(Dispatchers.Main.immediate) {
        currentCoroutineContext().ensureActive()
        if (!isOpen) return@withContext
        activeUiBindings.value += 1
        try {
            block()
        } finally {
            activeUiBindings.value -= 1
        }
    }

    fun latestOperation(key: OperationKey<*> = OperationKey<Unit>("latest")): MviOperation<S> =
        operations.latestOperation(key)

    fun queuedOperation(maxPending: Int = 16, key: OperationKey<*> = OperationKey<Unit>("queued")): MviOperation<S> =
        operations.queuedOperation(maxPending, key)

    fun idleOperation(key: OperationKey<*> = OperationKey<Unit>("idle")): MviOperation<S> =
        operations.idleOperation(key)

    suspend fun sendEffect(
        effect: E,
        epoch: EffectEpoch? = null,
        isCurrent: () -> Boolean = { true },
    ): Boolean {
        currentCoroutineContext().ensureActive()
        return offerEffect(effect, epoch, isCurrent)
    }

    fun offerEffect(
        effect: E,
        epoch: EffectEpoch? = null,
        isCurrent: () -> Boolean = { true },
    ): Boolean {
        if (!isOpen || !isCurrent()) return false
        return deferIntents {
            MviAccess.publishing(this) {
                effectMailbox.offer(effect, epoch) { isOpen && isCurrent() }
            }
        }
    }

    fun revokeEffects(epoch: EffectEpoch) = deferIntents {
        MviAccess.publishing(this) { effectMailbox.revoke(epoch) }
    }

    override fun close() {
        intentIngress.close()
        val main = Dispatchers.Main.immediate
        if (main.isDispatchNeeded(runtimeScope.coroutineContext)) {
            // VM clear has already cancelled its scope; cleanup must still be dispatched to Main.
            CoroutineScope(main + MviAccess.neutralContext()).launch { closeNow() }
        } else {
            closeNow()
        }
    }

    private fun closeNow() {
        if (closed) return
        closed = true
        prepareStateChange = null
        sourceController.cancel()
        operations.close()
        effectMailbox.close()
    }
}
