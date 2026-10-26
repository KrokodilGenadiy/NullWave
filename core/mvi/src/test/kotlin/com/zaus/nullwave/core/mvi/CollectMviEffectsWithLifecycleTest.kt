package com.zaus.nullwave.core.mvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import com.zaus.nullwave.core.mvi.ui.collectEffectsWithLifecycle
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectMviEffectsWithLifecycleTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(ImmediateTestMainDispatcher(dispatcher))
    }

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `STOP cancels active handler and START receives only pending signals`() = runTest(dispatcher) {
        val vm = vm()
        val owner = Owner(Lifecycle.State.CREATED)
        val seen = mutableListOf<Int>()
        val finished = mutableListOf<Int>()
        backgroundScope.launch {
            vm.collectEffectsWithLifecycle(owner.lifecycle) { signal ->
                seen += signal.value
                try { awaitCancellation() } finally { finished += signal.value }
            }
        }
        runCurrent()
        vm.ui.onIntent(Notify(1))
        assertTrue(seen.isEmpty())

        owner.moveTo(Lifecycle.State.STARTED)
        runCurrent()
        assertEquals(listOf(1), seen)
        assertEquals(0, vm.sourceStarts)

        owner.moveTo(Lifecycle.State.CREATED)
        runCurrent()
        assertEquals(listOf(1), finished)
        vm.ui.onIntent(Notify(2))
        runCurrent()
        assertEquals(listOf(1), seen)

        owner.moveTo(Lifecycle.State.STARTED)
        runCurrent()
        assertEquals(listOf(1, 2), seen)
        assertEquals(0, vm.sourceStarts)
    }

    @Test
    fun `lifecycle restart waits for handler cleanup and does not acquire a second consumer`() = runTest(dispatcher) {
        val vm = vm()
        val owner = Owner(Lifecycle.State.STARTED)
        val cleanup = CompletableDeferred<Unit>()
        val seen = mutableListOf<Int>()
        backgroundScope.launch {
            vm.collectEffectsWithLifecycle(owner.lifecycle) { signal ->
                seen += signal.value
                if (signal.value == 1) {
                    try { awaitCancellation() } finally {
                        withContext(NonCancellable) { cleanup.await() }
                    }
                }
            }
        }
        runCurrent()
        try {
            vm.ui.onIntent(Notify(1))
            runCurrent()
            owner.moveTo(Lifecycle.State.CREATED)
            runCurrent()
            vm.ui.onIntent(Notify(2))
            owner.moveTo(Lifecycle.State.STARTED)
            runCurrent()
            assertEquals(listOf(1), seen)

            cleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf(1, 2), seen)
        } finally {
            cleanup.complete(Unit)
        }
    }

    @Test
    fun `RESUMED waits beyond STARTED and pauses on return to STARTED`() = runTest(dispatcher) {
        val vm = vm()
        val owner = Owner(Lifecycle.State.STARTED)
        val seen = mutableListOf<Int>()
        backgroundScope.launch {
            vm.collectEffectsWithLifecycle(owner.lifecycle, Lifecycle.State.RESUMED) { seen += it.value }
        }
        runCurrent()
        vm.ui.onIntent(Notify(1))
        assertTrue(seen.isEmpty())

        owner.moveTo(Lifecycle.State.RESUMED)
        runCurrent()
        assertEquals(listOf(1), seen)
        owner.moveTo(Lifecycle.State.STARTED)
        runCurrent()
        vm.ui.onIntent(Notify(2))
        runCurrent()
        assertEquals(listOf(1), seen)
        owner.moveTo(Lifecycle.State.RESUMED)
        runCurrent()
        assertEquals(listOf(1, 2), seen)
    }

    @Test
    fun `DESTROY removes observer and completes collection`() = runTest(dispatcher) {
        val vm = vm()
        val owner = Owner(Lifecycle.State.STARTED)
        var stopped = false
        val collection = backgroundScope.launch {
            vm.collectEffectsWithLifecycle(owner.lifecycle) {
                try { awaitCancellation() } finally { stopped = true }
            }
        }
        runCurrent()
        vm.ui.onIntent(Notify(1))
        runCurrent()
        owner.moveTo(Lifecycle.State.DESTROYED)
        runCurrent()
        assertTrue(stopped)
        assertTrue(collection.isCompleted)
        assertFalse(collection.isCancelled)
        assertEquals(0, owner.registry.observerCount)
    }

    @Test
    fun `destroyed owner and cleared VM never initialize the definition`() = runTest(dispatcher) {
        val vm = vm()
        val dead = Owner(Lifecycle.State.CREATED).apply { moveTo(Lifecycle.State.DESTROYED) }
        vm.collectEffectsWithLifecycle(dead.lifecycle) { error("No handler expected") }
        assertEquals(0, vm.definitions)
        store.clear()
        val owner = Owner(Lifecycle.State.STARTED)
        val collection = backgroundScope.launch {
            vm.collectEffectsWithLifecycle(owner.lifecycle) { error("No handler expected") }
        }
        runCurrent()
        assertEquals(0, vm.definitions)
        collection.cancel()
        runCurrent()
        assertEquals(0, owner.registry.observerCount)
    }

    @Test
    fun `unsupported lifecycle thresholds are rejected before registering observers`() = runTest(dispatcher) {
        val vm = vm()
        val owner = Owner(Lifecycle.State.CREATED)
        for (threshold in listOf(Lifecycle.State.INITIALIZED, Lifecycle.State.CREATED, Lifecycle.State.DESTROYED)) {
            val failure = runCatching {
                vm.collectEffectsWithLifecycle(owner.lifecycle, threshold) { }
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        }
        assertEquals(0, vm.definitions)
        assertEquals(0, owner.registry.observerCount)
    }

    @Test
    fun `handler runs on Main and can dispatch a UI response through the normal contract`() = runTest(dispatcher) {
        val vm = vm()
        val owner = Owner(Lifecycle.State.STARTED)
        val mainThread = Thread.currentThread()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
            vm.collectEffectsWithLifecycle(owner.lifecycle) {
                assertEquals(mainThread, Thread.currentThread())
                assertEquals(Dispatchers.Main.immediate, currentCoroutineContext()[ContinuationInterceptor])
                vm.ui.onIntent(Handled)
            }
        }
        runCurrent()
        vm.ui.onIntent(Notify(1))
        runCurrent()
        assertEquals(1, vm.snapshot.value.state.handled)
    }

    private suspend fun vm(): Vm = Vm().also { store.put("effects", it); it.ui = it.uiApi() }
    private data class Snapshot(val handled: Int = 0) : UiState
    private sealed interface Intent : UiIntent
    private data class Notify(val value: Int) : Intent
    private data object Handled : Intent
    private data class Signal(val value: Int) : UiEffect
    private class Vm : BaseMviViewModel<Snapshot, Intent, Signal>(Snapshot(), sourceStopTimeoutMillis = 0) {
        lateinit var ui: MviUiApi<Snapshot, Intent>

        var definitions = 0
        var sourceStarts = 0
        override fun MviDefinition<Snapshot, Intent, Signal>.define() {
            definitions++
            source("source", flow = { flow<Int> { sourceStarts++; awaitCancellation() } }) { this }
            on<Notify> { tryEffect(Signal(it.value)) }
            on<Handled> { reduce { copy(handled = handled + 1) } }
        }
    }

    private class Owner(initial: Lifecycle.State) : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        init { moveTo(initial) }
        fun moveTo(state: Lifecycle.State) { registry.currentState = state }
    }
}
