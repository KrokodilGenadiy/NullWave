package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EffectCancellationTransactionTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextKey = 0

    @Before fun setUp() { Dispatchers.setMain(ImmediateTestMainDispatcher(dispatcher)) }
    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `onCancelled observer cannot resume a revoked handler into its UI action`() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val actions = mutableListOf<Int>()
        var handlerCancelled = false
        val vm = own {
            val work = queued<Int, Int>("work") {
                execute { if (it == 2) awaitCancellation() else it }
                onCancelled { copy(value = -1) }
                effect { input, _ -> Signal(input) }
            }
            on<Run> { submit(work, it.value) }
            on<Cancel> { cancel(work) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {
                if (it.value == -1) {
                    assertTrue(handlerCancelled)
                    release.complete(Unit)
                }
            }
        }
        backgroundScope.launch {
            vm.collectEffects { effect ->
                try {
                    release.await()
                    actions += effect.value
                } finally {
                    handlerCancelled = true
                }
            }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Run(2))
        runCurrent()
        vm.ui.onIntent(Cancel)
        runCurrent()

        assertTrue(handlerCancelled)
        assertTrue(release.isCompleted)
        assertEquals(emptyList<Int>(), actions)
    }

    @Test
    fun `handler cleanup submit runs after cancel state and survives its request snapshot`() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        val vm = own {
            val work = queued<Int, Int>("work") {
                onAccepted { events += "accepted:$it"; copy(value = it, loading = true) }
                execute { if (it == 1) it else awaitCancellation() }
                onCancelled { events += "cancelled"; copy(value = -1, loading = false) }
                effect { input, _ -> Signal(input) }
            }
            on<Run> { submit(work, it.value) }
            on<Cancel> { cancel(work) }
        }
        backgroundScope.launch {
            vm.collectEffects {
                try {
                    awaitCancellation()
                } finally {
                    events += "cleanup"
                    vm.ui.onIntent(Run(3))
                }
            }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Run(2))
        runCurrent()
        vm.ui.onIntent(Cancel)
        runCurrent()

        assertEquals(listOf("accepted:1", "accepted:2", "cleanup", "cancelled", "accepted:3"), events)
        assertEquals(Snapshot(3, loading = true), vm.state.value)
    }

    @Test
    fun `clear from handler cleanup discards deferred intents and cancel reducer`() = runTest(dispatcher) {
        var cancelReducerCalls = 0
        val vm = own {
            val work = queued<Int, Int>("work") {
                execute { if (it == 1) it else awaitCancellation() }
                onResult { _, value -> copy(value = value) }
                onCancelled { cancelReducerCalls++; copy(value = -1) }
                effect { input, _ -> Signal(input) }
            }
            on<Run> { submit(work, it.value) }
            on<Cancel> { cancel(work) }
        }
        backgroundScope.launch {
            vm.collectEffects {
                try { awaitCancellation() } finally {
                    vm.ui.onIntent(Run(3))
                    store.clear()
                }
            }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Run(2))
        runCurrent()
        vm.ui.onIntent(Cancel)
        runCurrent()

        assertEquals(0, cancelReducerCalls)
        assertEquals(1, vm.state.value.value)
    }

    @Test
    fun `throwing cancel reducer still cancels handler and releases deferred intents`() = runTest(dispatcher) {
        val expected = IllegalArgumentException("cancel reducer")
        var secondCancelled = false
        val vm = own {
            val work = queued<Int, Int>("work") {
                onAccepted { copy(value = it) }
                execute {
                    if (it == 2) {
                        try { awaitCancellation() } finally { secondCancelled = true }
                    } else it
                }
                onCancelled { throw expected }
                effect { input, _ -> Signal(input) }
            }
            on<Run> { submit(work, it.value) }
            on<Cancel> { cancel(work) }
        }
        backgroundScope.launch {
            vm.collectEffects {
                if (it.value == 1) {
                    try { awaitCancellation() } finally { vm.ui.onIntent(Run(3)) }
                }
            }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Run(2))
        runCurrent()
        assertSame(expected, assertThrows(IllegalArgumentException::class.java) { vm.ui.onIntent(Cancel) })
        runCurrent()

        assertTrue(secondCancelled)
        assertEquals(3, vm.state.value.value)
    }

    @Test
    fun `effect-only cancel returns false without cancelling work submitted by cleanup`() = runTest(dispatcher) {
        val results = mutableListOf<Boolean>()
        var replacementCancelled = false
        val vm = own {
            val work = queued<Int, Int>("work") {
                onAccepted { copy(value = it) }
                execute {
                    if (it == 2) {
                        try { awaitCancellation() } finally { replacementCancelled = true }
                    } else it
                }
                effect { input, _ -> Signal(input) }
            }
            on<Run> { submit(work, it.value) }
            on<Cancel> { results += cancel(work) }
        }
        backgroundScope.launch {
            vm.collectEffects {
                try { awaitCancellation() } finally { vm.ui.onIntent(Run(2)) }
            }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Cancel)
        runCurrent()

        assertEquals(listOf(false), results)
        assertEquals(2, vm.state.value.value)
        assertFalse(replacementCancelled)
    }

    @Test
    fun `handler cleanup cannot restore result rights by completing the old work`() = runTest(dispatcher) {
        val finishWork = CompletableDeferred<Int>()
        val results = mutableListOf<Int>()
        val finalizers = mutableListOf<Int>()
        val vm = own {
            val work = queued<Int, Int>("work") {
                execute { if (it == 2) finishWork.await() else it }
                onResult { _, value -> results += value; copy(value = value) }
                onFinished { finalizers += it; this }
                onCancelled { copy(value = -1) }
                effect { input, _ -> Signal(input) }
            }
            on<Run> { submit(work, it.value) }
            on<Cancel> { cancel(work) }
        }
        backgroundScope.launch {
            vm.collectEffects {
                try { awaitCancellation() } finally { finishWork.complete(2) }
            }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Run(2))
        runCurrent()
        vm.ui.onIntent(Cancel)
        runCurrent()

        assertTrue(finishWork.isCompleted)
        assertEquals(listOf(1), results)
        assertEquals(listOf(1), finalizers)
        assertEquals(-1, vm.state.value.value)
    }

    private suspend fun own(definition: MviDefinition<Snapshot, Intent, Signal>.() -> Unit): Vm =
        Vm(definition).also { store.put("cancel-transaction-${nextKey++}", it); it.ui = it.uiApi() }
    private data class Snapshot(val value: Int = 0, val loading: Boolean = false) : UiState
    private sealed interface Intent : UiIntent
    private data class Run(val value: Int) : Intent
    private data object Cancel : Intent
    private data class Signal(val value: Int) : UiEffect
    private class Vm(private val definition: MviDefinition<Snapshot, Intent, Signal>.() -> Unit) :
        BaseMviViewModel<Snapshot, Intent, Signal>(Snapshot()) {
        lateinit var ui: MviUiApi<Snapshot, Intent>

        override fun MviDefinition<Snapshot, Intent, Signal>.define() = definition()
    }
}
