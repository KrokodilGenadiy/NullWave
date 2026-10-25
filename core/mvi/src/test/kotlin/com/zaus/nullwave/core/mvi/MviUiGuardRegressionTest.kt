package com.zaus.nullwave.core.mvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.collectEffectsWithLifecycle
import com.zaus.nullwave.core.mvi.ui.collectStateWithUiBinding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** External UI resumes inline from real work; it must not inherit that work's access restrictions. */
@OptIn(ExperimentalCoroutinesApi::class)
class MviUiGuardRegressionTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(AndroidLikeTestMainDispatcher(dispatcher))
    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `UI resumed by source can acquire an API and dispatch an ordinary callback`() = runTest(dispatcher) {
        onSourceResume { source, target ->
            val ui = source.uiApi()
            assertSame(ui, source.uiApi())
            ui.onIntent(Change)
            assertEquals(1, source.state.value.edits)
            // A different owner must also be safe, without initializing it just by acquisition.
            val otherUi = target.uiApi()
            assertEquals(0, target.definitions)
            otherUi.onIntent(Change)
            assertEquals(1, target.state.value.edits)
        }
    }

    @Test
    fun `UI resumed by source can enter another binding`() = runTest(dispatcher) {
        onSourceResume { _, target ->
            var entered = false
            target.withUiBinding { entered = true }
            assertTrue(entered)
            assertEquals(1, target.definitions)
        }
    }

    @Test
    fun `UI resumed by source can collect effects and dispatch from the handler`() = runTest(dispatcher) {
        onSourceResume { _, target ->
            coroutineScope {
                val ui = target.uiApi()
                ui.onIntent(Notify)
                val received = CompletableDeferred<Signal>()
                val collection = launch(start = CoroutineStart.UNDISPATCHED) {
                    target.collectEffects {
                        assertSame(ui, target.uiApi())
                        ui.onIntent(Change)
                        received.complete(it)
                    }
                }
                try {
                    assertEquals(Signal, received.await())
                    assertEquals(1, target.state.value.edits)
                } finally { collection.cancelAndJoin() }
            }
        }
    }

    @Test
    fun `UI resumed by source can enter production lifecycle state helper`() = runTest(dispatcher) {
        onSourceResume { _, target ->
            coroutineScope {
                val received = CompletableDeferred<Snapshot>()
                val collection = launch(start = CoroutineStart.UNDISPATCHED) {
                    target.collectStateWithUiBinding(Owner().lifecycle) { received.complete(it) }
                }
                try {
                    assertEquals(Snapshot(), received.await())
                    assertEquals(1, target.definitions)
                } finally { collection.cancelAndJoin() }
            }
        }
    }

    @Test
    fun `UI resumed by source can enter production lifecycle effect helper`() = runTest(dispatcher) {
        onSourceResume { _, target ->
            coroutineScope {
                target.uiApi().onIntent(Notify)
                val received = CompletableDeferred<Signal>()
                val collection = launch(start = CoroutineStart.UNDISPATCHED) {
                    target.collectEffectsWithLifecycle(Owner().lifecycle) { received.complete(it) }
                }
                try { assertEquals(Signal, received.await()) }
                finally { collection.cancelAndJoin() }
            }
        }
    }

    @Test
    fun `API acquisition is passive and stored callback becomes inert after clear`() = runTest(dispatcher) {
        val vm = ProbeVm(CompletableDeferred()).also { store.put("screen", it) }
        val ui = vm.uiApi()
        assertSame(ui, vm.uiApi())
        assertSame(vm.state, ui.state)
        runCurrent()
        assertEquals(0, vm.definitions)
        assertEquals(0, vm.activeSources)
        ui.onIntent(Change)
        assertEquals(1, ui.state.value.edits)
        assertEquals(0, vm.activeSources)
        store.clear()
        ui.onIntent(Change)
        assertSame(ui, vm.uiApi())
        assertEquals(1, ui.state.value.edits)
    }

    @Test
    fun `callback is available while lifecycle observation is stopped`() = runTest(dispatcher) {
        val vm = ProbeVm(CompletableDeferred()).also { store.put("screen", it) }
        val ui = vm.uiApi()
        val owner = Owner(Lifecycle.State.CREATED)
        val collection = backgroundScope.launch(Dispatchers.Main.immediate) {
            vm.collectStateWithUiBinding(owner.lifecycle, Lifecycle.State.RESUMED) {}
        }
        ui.onIntent(Change)
        runCurrent()
        assertEquals(1, ui.state.value.edits)
        assertEquals(0, vm.activeSources)
        collection.cancelAndJoin()
    }

    private suspend fun onSourceResume(action: suspend (ProbeVm, ProbeVm) -> Unit) = coroutineScope {
        val signal = CompletableDeferred<Unit>()
        val source = ProbeVm(signal).also { store.put("source", it) }
        val target = ProbeVm(CompletableDeferred()).also { store.put("target", it) }
        val sourceJob = source.viewModelScope.coroutineContext.job
        val targetJob = target.viewModelScope.coroutineContext.job
        val ui = launch(Dispatchers.Main.immediate) {
            val binding = launch(start = CoroutineStart.UNDISPATCHED) {
                source.withUiBinding { awaitCancellation() }
            }
            try {
                withTimeout(5_000) {
                    signal.await()
                    action(source, target)
                }
            } finally {
                binding.cancelAndJoin()
                store.clear()
                withTimeout(5_000) { sourceJob.join(); targetJob.join() }
            }
            assertEquals(0, source.activeSources)
            assertEquals(0, target.activeSources)
        }
        ui.join()
    }

    private data class Snapshot(val ready: Boolean = false, val edits: Int = 0) : UiState
    private sealed interface Intent : UiIntent
    private data object Change : Intent
    private data object Notify : Intent
    private data object Signal : UiEffect

    private class ProbeVm(private val signal: CompletableDeferred<Unit>) :
        BaseMviViewModel<Snapshot, Intent, Signal>(Snapshot(), sourceStopTimeoutMillis = 0) {
        var definitions = 0
            private set
        var activeSources = 0
            private set

        override fun MviDefinition<Snapshot, Intent, Signal>.define() {
            definitions++
            source("data", flow = {
                flow {
                    activeSources++
                    try {
                        delay(1)
                        // The unrelated UI continuation resumes while this source's Work is on Main.
                        signal.complete(Unit)
                        emit(Unit)
                        awaitCancellation()
                    } finally { activeSources-- }
                }
            }) { copy(ready = true) }
            on<Change> { reduce { copy(edits = edits + 1) } }
            on<Notify> { tryEffect(Signal) }
        }
    }

    private class Owner(state: Lifecycle.State = Lifecycle.State.RESUMED) : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = state }
    }
}
