package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** UI ownership is an external entry contract, even when the target is another ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
class MviUiEntryContractTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextKey = 0

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
    fun `own execute cannot start sources through UI binding`() = runTest(dispatcher) {
        var sourceStarts = 0
        var enteredBinding = false
        var rejected: IllegalStateException? = null
        lateinit var vm: ProbeViewModel
        vm = own {
            source(
                "data",
                flow = { flow<Unit> { sourceStarts++; awaitCancellation() } },
                reducer = { this },
            )
            val work = idle<Unit, Unit>("work") {
                execute {
                    try {
                        vm.withUiBinding { enteredBinding = true }
                    } catch (failure: IllegalStateException) {
                        rejected = failure
                    }
                }
                onResult { _, _ -> copy(value = 1) }
            }
            on<Start> { submit(work, Unit) }
        }

        vm.ui.onIntent(Start)
        runCurrent()

        assertNotNull(rejected)
        assertFalse(enteredBinding)
        assertEquals(0, sourceStarts)
        assertEquals(1, vm.snapshot.value.state.value)

        val ui = backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        assertEquals(1, sourceStarts)
        ui.cancel()
        runCurrent()
    }

    @Test
    fun `cross VM binding is rejected before initializing target definition`() = runTest(dispatcher) {
        var targetDefinitions = 0
        var enteredBinding = false
        var rejected: IllegalStateException? = null
        val target = own { targetDefinitions++; on<Start> {} }
        val caller = own {
            val work = idle<Unit, Unit>("work") {
                execute {
                    try {
                        target.withUiBinding { enteredBinding = true }
                    } catch (failure: IllegalStateException) {
                        rejected = failure
                    }
                }
            }
            on<Start> { submit(work, Unit) }
        }

        caller.ui.onIntent(Start)
        runCurrent()

        assertNotNull(rejected)
        assertEquals(0, targetDefinitions)
        assertFalse(enteredBinding)
        target.withUiBinding { enteredBinding = true }
        assertEquals(1, targetDefinitions)
        assertTrue(enteredBinding)
    }

    @Test
    fun `own managed effect collection is rejected and leaves UI lease available`() = runTest(dispatcher) {
        var rejected: IllegalStateException? = null
        lateinit var vm: ProbeViewModel
        vm = own {
            val work = idle<Unit, Unit>("work") {
                execute {
                    try {
                        vm.collectEffects { error("Work must not own the UI handler") }
                    } catch (failure: IllegalStateException) {
                        rejected = failure
                    }
                }
            }
            on<Start> { submit(work, Unit) }
            on<Notify> { tryEffect(Signal(7)) }
        }

        vm.ui.onIntent(Start)
        runCurrent()
        assertNotNull(rejected)

        val received = mutableListOf<Signal>()
        backgroundScope.launch { vm.collectEffects { received += it } }
        vm.ui.onIntent(Notify)
        runCurrent()
        assertEquals(listOf(Signal(7)), received)
    }

    @Test
    fun `cross VM collection cannot clear work ownership and dispatch to caller`() = runTest(dispatcher) {
        var rejected: IllegalStateException? = null
        val target = own { on<Notify> { tryEffect(Signal(99)) } }
        lateinit var caller: ProbeViewModel
        caller = own {
            val work = idle<Unit, Unit>("work") {
                execute {
                    try {
                        target.collectEffects { caller.ui.onIntent(Change(it.value)) }
                    } catch (failure: IllegalStateException) {
                        rejected = failure
                    }
                }
            }
            on<Start> { submit(work, Unit) }
            on<Change> { reduce { copy(value = it.value) } }
        }
        target.ui.onIntent(Notify)

        caller.ui.onIntent(Start)
        runCurrent()

        assertNotNull(rejected)
        assertEquals(0, caller.snapshot.value.state.value)

        // An actual external UI handler may dispatch into either VM, using the untouched signal.
        backgroundScope.launch { target.collectEffects { caller.ui.onIntent(Change(it.value)) } }
        runCurrent()
        assertEquals(99, caller.snapshot.value.state.value)
    }

    @Test
    fun `managed collection rejects work before initializing the target definition`() = runTest(dispatcher) {
        var rejected = 0
        var targetDefinitions = 0
        val target = own { targetDefinitions++; on<Notify> { tryEffect(Signal(7)) } }
        lateinit var caller: ProbeViewModel
        caller = own {
            val work = idle<Unit, Unit>("work") {
                execute {
                    for (vm in listOf(caller, target)) {
                        try {
                            vm.collectEffects { error("Work must not collect UI effects") }
                        } catch (_: IllegalStateException) {
                            rejected++
                        }
                    }
                }
            }
            on<Start> { submit(work, Unit) }
        }

        caller.ui.onIntent(Start)
        runCurrent()

        assertEquals(2, rejected)
        assertEquals(0, targetDefinitions)
        val received = mutableListOf<Signal>()
        backgroundScope.launch { target.collectEffects { received += it } }
        target.ui.onIntent(Notify)
        runCurrent()
        assertEquals(listOf(Signal(7)), received)
    }

    @Test
    fun `source work cannot acquire a second VMs UI binding`() = runTest(dispatcher) {
        var targetDefinitions = 0
        var rejected: IllegalStateException? = null
        val target = own { targetDefinitions++; on<Start> {} }
        val caller = own {
            source(
                "data",
                flow = {
                    flow {
                        try {
                            target.withUiBinding { error("Source must not own UI") }
                        } catch (failure: IllegalStateException) {
                            rejected = failure
                        }
                        emit(7)
                        awaitCancellation()
                    }
                },
                reducer = { copy(value = it) },
            )
        }

        backgroundScope.launch { caller.withUiBinding { awaitCancellation() } }
        runCurrent()

        assertNotNull(rejected)
        assertEquals(0, targetDefinitions)
        assertEquals(7, caller.snapshot.value.state.value)
    }

    @Test
    fun `cleared managed UI entry remains a no-op without initializing definition`() = runTest(dispatcher) {
        var definitions = 0
        var enteredBinding = false
        val vm = own { definitions++; on<Start> {} }
        store.clear()

        vm.withUiBinding { enteredBinding = true }
        vm.collectEffects { error("Cleared VM must not deliver effects") }

        assertFalse(enteredBinding)
        assertEquals(0, definitions)
    }

    private suspend fun own(definition: MviDefinition<Snapshot, Intent, Signal>.() -> Unit): ProbeViewModel =
        ProbeViewModel(definition).also { store.put("ui-entry-${nextKey++}", it); it.ui = it.uiApi() }

    private data class Snapshot(val value: Int = 0) : UiState
    private sealed interface Intent : UiIntent
    private data object Start : Intent
    private data object Notify : Intent
    private data class Change(val value: Int) : Intent
    private data class Signal(val value: Int) : UiEffect
    private class ProbeViewModel(private val definition: MviDefinition<Snapshot, Intent, Signal>.() -> Unit) :
        BaseMviViewModel<Snapshot, Intent, Signal>(Snapshot(), sourceStopTimeoutMillis = 0) {
        lateinit var ui: MviUiApi<Snapshot, Intent>

        override fun MviDefinition<Snapshot, Intent, Signal>.define() = definition()
    }
}
