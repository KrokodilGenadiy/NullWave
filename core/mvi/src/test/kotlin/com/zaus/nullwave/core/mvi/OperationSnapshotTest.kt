package com.zaus.nullwave.core.mvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.dsl.OperationKey
import com.zaus.nullwave.core.mvi.persistence.MviSavedState
import com.zaus.nullwave.core.mvi.persistence.savedState
import com.zaus.nullwave.core.mvi.runtime.MviAccess
import com.zaus.nullwave.core.mvi.runtime.MviRuntime
import com.zaus.nullwave.core.mvi.runtime.operation.OperationPolicy
import com.zaus.nullwave.core.mvi.state.MviSnapshot
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import com.zaus.nullwave.core.mvi.ui.collectSnapshotWithUiBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationSnapshotTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextVm = 0
    private val save = OperationKey<Int>("save")
    private val other = OperationKey<Unit>("other")

    @Before fun setUp() = Dispatchers.setMain(AndroidLikeTestMainDispatcher(dispatcher))
    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun `reading activity does not define and unknown keys fail after definition`() = runTest(dispatcher) {
        var definitions = 0
        val vm = own {
            definitions++
            idle<Int, Int>(save) { execute { it } }
            on<Noop> {}
        }
        assertSame(vm.snapshot, vm.ui.snapshot)
        assertStatus(vm, busy = false, executing = false, pending = 0, canSubmit = false)
        assertEquals(0, definitions)
        vm.ui.onIntent(Noop)
        assertStatus(vm, busy = false, executing = false, pending = 0, canSubmit = true)
        assertThrows(IllegalStateException::class.java) { vm.snapshot.value.operations[OperationKey<Int>("save")] }
    }

    @Test fun `synchronous completion and later feature reducer cannot overwrite automatic activity`() = runTest(dispatcher) {
        val vm = own {
            val work = idle<Int, Int>(save) {
                execute { it * 2 }
                onResult { _, value -> copy(value = value) }
            }
            on<Start> {
                submit(work, it.value)
                reduce { copy(text = "after submit") }
            }
        }
        vm.ui.onIntent(Start(3))
        runCurrent()
        assertEquals(Data(6, "after submit"), vm.snapshot.value.state)
        assertStatus(vm, false, false, 0, true)
    }

    @Test fun `idle exposes acceptance result and completion without feature loading fields`() = runTest(dispatcher) {
        val response = CompletableDeferred<Int>()
        val accepted = mutableListOf<Boolean>()
        val seen = mutableListOf<MviSnapshot<Data>>()
        val vm = own {
            val work = idle<Int, Int>(save) {
                onAccepted { copy(text = "accepted") }
                execute { response.await() }
                onResult { _, value -> copy(value = value) }
            }
            on<Start> { accepted += submit(work, it.value) }
        }
        backgroundScope.launch(Dispatchers.Main.immediate) { vm.snapshot.collect { seen += it } }
        vm.ui.onIntent(Start(1))
        assertStatus(vm, true, true, 0, false)
        val busySnapshot = vm.snapshot.value
        vm.ui.onIntent(Start(2))
        assertSame(busySnapshot, vm.snapshot.value)
        assertEquals(listOf(true, false), accepted)
        response.complete(7)
        runCurrent()
        assertStatus(vm, false, false, 0, true)
        assertEquals(7, vm.snapshot.value.state.value)
        // Acceptance and result data never appear with an unrelated or stale activity map.
        val acceptedSnapshot = seen.first { it.state.text == "accepted" }
        assertTrue(acceptedSnapshot.operations[save].isBusy)
        assertEquals(1, acceptedSnapshot.operations[save].pendingCount)
        val resultSnapshot = seen.first { it.state.value == 7 }
        assertTrue(resultSnapshot.operations[save].isExecuting)
        assertTrue(busySnapshot.operations[save].isBusy) // Old snapshots are immutable.
    }

    @Test fun `latest keeps cleanup busy and replaces only the pending input`() = runTest(dispatcher) {
        val cleanup = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        val vm = own {
            val work = latest<Int, Int>(save) {
                execute { input ->
                    started += input
                    if (input == 1) try { awaitCancellation() } finally {
                        withContext(NonCancellable) { cleanup.await() }
                    }
                    input
                }
                onResult { _, value -> copy(value = value) }
            }
            on<Start> { submit(work, it.value) }
        }
        try {
            vm.ui.onIntent(Start(1))
            assertStatus(vm, true, true, 0, true)
            vm.ui.onIntent(Start(2))
            assertStatus(vm, true, false, 1, true)
            vm.ui.onIntent(Start(3))
            assertStatus(vm, true, false, 1, true)
            assertEquals(listOf(1), started)
            cleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf(1, 3), started)
            assertEquals(3, vm.snapshot.value.state.value)
            assertStatus(vm, false, false, 0, true)
        } finally { cleanup.complete(Unit) }
    }

    @Test fun `queued capacity and busy state follow real requests without flickering between them`() = runTest(dispatcher) {
        val responses = List(2) { CompletableDeferred<Int>() }
        val accepted = mutableListOf<Boolean>()
        val busyDuringHandoff = mutableListOf<Boolean>()
        val vm = own {
            val work = queued<Int, Int>(save, maxPending = 1) {
                execute { responses[it - 1].await() }
                onResult { _, result -> copy(value = result) }
            }
            on<Start> { accepted += submit(work, it.value) }
        }
        vm.ui.onIntent(Start(1))
        assertStatus(vm, true, true, 0, true)
        vm.ui.onIntent(Start(2))
        assertStatus(vm, true, true, 1, false)
        val full = vm.snapshot.value
        vm.ui.onIntent(Start(3))
        assertSame(full, vm.snapshot.value)
        assertEquals(listOf(true, true, false), accepted)
        val observer = backgroundScope.launch(Dispatchers.Main.immediate) {
            vm.snapshot.collect { busyDuringHandoff += it.operations[save].isBusy }
        }
        responses[0].complete(1)
        runCurrent()
        assertStatus(vm, true, true, 0, true)
        assertTrue(busyDuringHandoff.all { it })
        observer.cancel()
        responses[1].complete(2)
        runCurrent()
        assertStatus(vm, false, false, 0, true)
    }

    @Test fun `queued status reserves the first input without consuming waiting capacity`() = runTest(dispatcher) {
        for (capacity in listOf(1, 3)) {
            val seen = mutableListOf<MviSnapshot<Data>>()
            val accepted = mutableListOf<Boolean>()
            val vm = own {
                val work = queued<Int, Unit>(save, maxPending = capacity) {
                    onAccepted { input -> copy(value = input) }
                    execute { awaitCancellation() }
                }
                on<Start> { accepted += submit(work, it.value) }
            }
            val collector = backgroundScope.launch(Dispatchers.Main.immediate) {
                vm.snapshot.collect { seen += it }
            }
            vm.ui.onIntent(Start(1))
            val reserved = seen.first { it.state.value == 1 }.operations[save]
            assertEquals(1, reserved.pendingCount)
            assertFalse(reserved.isExecuting)
            assertTrue(reserved.canSubmit)

            for (input in 2..capacity + 1) {
                assertTrue(vm.snapshot.value.operations[save].canSubmit)
                vm.ui.onIntent(Start(input))
                assertEquals(input - 1, vm.snapshot.value.operations[save].pendingCount)
            }
            assertFalse(vm.snapshot.value.operations[save].canSubmit)
            val full = vm.snapshot.value
            vm.ui.onIntent(Start(capacity + 2))
            assertSame(full, vm.snapshot.value)
            assertEquals(List(capacity + 1) { true } + false, accepted)
            collector.cancel()
        }
    }

    @Test fun `explicit cancellation keeps idle occupied until cleanup finishes`() = runTest(dispatcher) {
        val cleanup = CompletableDeferred<Unit>()
        var starts = 0
        val accepted = mutableListOf<Boolean>()
        val vm = own {
            val work = idle<Int, Unit>(save) {
                execute {
                    starts++
                    try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
                }
                onCancelled { copy(text = "cancelled") }
            }
            on<Start> { accepted += submit(work, it.value) }
            on<Stop> { cancel(work) }
        }
        try {
            vm.ui.onIntent(Start(1))
            vm.ui.onIntent(Stop)
            assertStatus(vm, true, false, 0, false)
            assertEquals("cancelled", vm.snapshot.value.state.text)
            vm.ui.onIntent(Start(2))
            assertEquals(listOf(true, false), accepted)
            cleanup.complete(Unit)
            runCurrent()
            assertEquals(1, starts)
            assertStatus(vm, false, false, 0, true)
        } finally { cleanup.complete(Unit) }
    }

    @Test fun `independent operations and VM instances never share activity`() = runTest(dispatcher) {
        val definition: MviDefinition<Data, Intent, Nothing>.() -> Unit = {
            val first = idle<Int, Unit>(save) { execute { awaitCancellation() } }
            val second = idle<Unit, Unit>(other) { execute { awaitCancellation() } }
            on<Start> { submit(first, it.value) }
            on<Other> { submit(second, Unit) }
            on<Stop> { cancel(first) }
            on<Noop> {}
        }
        val a = own(definition = definition)
        val b = own(definition = definition)
        b.ui.onIntent(Noop)
        a.ui.onIntent(Start(1))
        a.ui.onIntent(Other)
        assertTrue(a.snapshot.value.operations[other].isBusy)
        assertStatus(b, false, false, 0, true)
        a.ui.onIntent(Stop)
        runCurrent()
        assertStatus(a, false, false, 0, true)
        assertTrue(a.snapshot.value.operations[other].isBusy)
    }

    @Test fun `queued cancellation removes pending work while retaining active cleanup`() = runTest(dispatcher) {
        val cleanup = CompletableDeferred<Unit>()
        val starts = mutableListOf<Int>()
        val vm = own {
            val work = queued<Int, Unit>(save, maxPending = 1) {
                execute {
                    starts += it
                    try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
                }
            }
            on<Start> { submit(work, it.value) }
            on<Stop> { cancel(work) }
        }
        try {
            vm.ui.onIntent(Start(1))
            vm.ui.onIntent(Start(2))
            assertStatus(vm, true, true, 1, false)
            vm.ui.onIntent(Stop)
            assertStatus(vm, true, false, 0, true)
            cleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf(1), starts)
            assertStatus(vm, false, false, 0, true)
        } finally { cleanup.complete(Unit) }
    }

    @Test fun `clear from an accepted snapshot prevents the work body from starting`() = runTest(dispatcher) {
        var executions = 0
        val vm = own {
            val work = idle<Int, Unit>(save) { execute { executions++ } }
            on<Start> { submit(work, it.value) }
        }
        backgroundScope.launch(Dispatchers.Main.immediate) {
            vm.snapshot.collect { if (it.operations[save].isBusy) store.clear() }
        }
        vm.ui.onIntent(Start(1))
        runCurrent()
        assertEquals(0, executions)
        assertStatus(vm, false, false, 0, false)
    }

    @Test fun `status observer intents wait for the synchronous handler to finish`() = runTest(dispatcher) {
        var executions = 0
        val vm = own {
            val work = idle<Int, Unit>(save) { execute { executions++ } }
            on<Start> { submit(work, it.value) }
            on<Stop> { cancel(work) }
        }
        backgroundScope.launch(Dispatchers.Main.immediate) {
            vm.snapshot.collect { if (it.operations[save].isExecuting) vm.ui.onIntent(Stop) }
        }
        vm.ui.onIntent(Start(1))
        runCurrent()
        // An intent already being handled owns its transition. Queued cancellation cannot recurse.
        // The synchronous body is allowed to finish before Stop; no stale busy status may survive.
        assertEquals(1, executions)
        assertStatus(vm, false, false, 0, true)
    }

    @Test fun `clear publishes unavailable activity and late cleanup cannot resurrect it`() = runTest(dispatcher) {
        val cleanup = CompletableDeferred<Unit>()
        val vm = own {
            val work = latest<Int, Int>(save) {
                execute { input ->
                    withContext(NonCancellable) { cleanup.await() }
                    input
                }
                onResult { _, result -> copy(value = result) }
            }
            on<Start> { submit(work, it.value) }
        }
        try {
            vm.ui.onIntent(Start(1))
            vm.ui.onIntent(Start(2))
            store.clear()
            assertStatus(vm, false, false, 0, false)
            val closed = vm.snapshot.value
            cleanup.complete(Unit)
            runCurrent()
            vm.ui.onIntent(Start(3))
            assertSame(closed, vm.snapshot.value)
            assertEquals(Data(), closed.state)
        } finally { cleanup.complete(Unit) }
    }

    @Test fun `activity changes do not evaluate source selectors or write saved payloads`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        var payloadSelections = 0
        var sourceSelections = 0
        var starts = 0
        val response = CompletableDeferred<Unit>()
        val adapter = savedState<Data, String>(handle, "inputs", String.serializer(),
            select = { payloadSelections++; text }, restore = { copy(text = it) })
        val vm = own(adapter) {
            source("catalog", input = { sourceSelections++; text }, flow = {
                flow<Unit> { starts++; awaitCancellation() }
            }) { _, _ -> this }
            val work = idle<Int, Unit>(save) { execute { response.await() } }
            on<Start> { submit(work, it.value) }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        val selections = payloadSelections to sourceSelections
        val payload = handle.get<String>("inputs")
        vm.ui.onIntent(Start(1))
        response.complete(Unit)
        runCurrent()
        assertEquals(selections, payloadSelections to sourceSelections)
        assertEquals(payload, handle.get<String>("inputs"))
        assertEquals(1, starts)
        assertStatus(vm, false, false, 0, true)
    }

    @Test fun `restoration keeps feature inputs and restores no running or pending operation`() = runTest(dispatcher) {
        fun adapter(handle: SavedStateHandle) = savedState<Data, String>(handle, "inputs", String.serializer(),
            select = { text }, restore = { copy(text = it) })
        val definition: MviDefinition<Data, Intent, Nothing>.() -> Unit = {
            val work = queued<Int, Unit>(save) { execute { awaitCancellation() } }
            on<Start> { submit(work, it.value) }
            on<Edit> { reduce { copy(text = it.text) } }
            on<Noop> {}
        }
        val handle = SavedStateHandle()
        val old = own(adapter(handle), definition)
        old.ui.onIntent(Edit("draft"))
        old.ui.onIntent(Start(1))
        old.ui.onIntent(Start(2))
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        store.clear()
        val restored = own(adapter(restoredHandle), definition)
        assertEquals(Data(text = "draft"), restored.snapshot.value.state)
        assertStatus(restored, false, false, 0, false)
        restored.ui.onIntent(Noop)
        assertStatus(restored, false, false, 0, true)
    }

    @Test fun `lifecycle collector resumes with the same canonical data and finished activity`() = runTest(dispatcher) {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.STARTED
        val response = CompletableDeferred<Int>()
        val vm = own {
            val work = idle<Int, Int>(save) { execute { response.await() }; onResult { _, result -> copy(value = result) } }
            on<Start> { submit(work, it.value) }
        }
        val seen = mutableListOf<MviSnapshot<Data>>()
        backgroundScope.launch { vm.collectSnapshotWithUiBinding(owner.lifecycle) { seen += it } }
        runCurrent()
        vm.ui.onIntent(Start(1))
        runCurrent()
        assertTrue(seen.last().operations[save].isBusy)
        owner.registry.currentState = Lifecycle.State.CREATED
        runCurrent()
        response.complete(42)
        runCurrent()
        assertTrue(seen.last().operations[save].isBusy)
        owner.registry.currentState = Lifecycle.State.STARTED
        runCurrent()
        assertSame(vm.snapshot.value, seen.last())
        assertEquals(42, seen.last().state.value)
        assertFalse(seen.last().operations[save].isBusy)
    }

    @Test fun `execution and reducer failures settle every policy without hiding the original error`() = runTest(dispatcher) {
        for (policy in OperationPolicy.entries) for (stage in listOf("accepted", "execute", "result", "finished", "selfCancel")) {
            val errors = mutableListOf<Throwable>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> errors += e })
            val runtime = MviRuntime<Data, Nothing>(Data(), scope, observeSources = {})
            val failure = IllegalStateException(stage)
            try {
                val schema = MviDefinition<Data, Intent, Nothing>(runtime)
                MviAccess.defining(runtime) {
                    with(schema) {
                        val clauses: com.zaus.nullwave.core.mvi.dsl.OperationBuilder<Data, Nothing, Int, Int>.() -> Unit = {
                            onAccepted { if (stage == "accepted") throw failure; this }
                            execute { if (stage == "execute") throw failure; if (stage == "selfCancel") throw CancellationException(); it }
                            onResult { _, value -> if (stage == "result") throw failure; copy(value = value) }
                            onFinished { if (stage == "finished") throw failure; this }
                        }
                        val work = when (policy) {
                            OperationPolicy.Latest -> latest(save, clauses)
                            OperationPolicy.Queued -> queued(save, block = clauses)
                            OperationPolicy.Idle -> idle(save, clauses)
                        }
                        on<Start> { submit(work, it.value) }
                        seal()
                    }
                }
                runtime.activateOperations()
                if (stage == "accepted") assertSame(failure, assertThrows(IllegalStateException::class.java) { schema.dispatch(Start(1)) })
                else schema.dispatch(Start(1))
                runCurrent()
                val status = runtime.snapshot.value.operations[save]
                assertFalse("$policy/$stage remained busy", status.isBusy)
                assertTrue(status.canSubmit)
                assertEquals(if (stage in listOf("accepted", "selfCancel")) 0 else 1, errors.size)
                if (errors.isNotEmpty()) assertTrue(generateSequence(errors.single()) { it.cause }.any { it === failure })
            } finally { runtime.close(); scope.cancel() }
        }
    }

    private fun assertStatus(vm: Vm, busy: Boolean, executing: Boolean, pending: Int, canSubmit: Boolean) {
        val status = vm.snapshot.value.operations[save]
        assertEquals("busy: $status", busy, status.isBusy)
        assertEquals("executing: $status", executing, status.isExecuting)
        assertEquals("pending: $status", pending, status.pendingCount)
        assertEquals("admission: $status", canSubmit, status.canSubmit)
    }

    private suspend fun own(adapter: MviSavedState<Data>? = null, definition: MviDefinition<Data, Intent, Nothing>.() -> Unit): Vm =
        Vm(adapter, definition).also { store.put("vm-${nextVm++}", it); it.ui = it.uiApi() }
    private data class Data(val value: Int = 0, val text: String = "") : UiState
    private sealed interface Intent : UiIntent
    private data class Start(val value: Int) : Intent
    private data class Edit(val text: String) : Intent
    private data object Stop : Intent
    private data object Other : Intent
    private data object Noop : Intent
    private class Vm(adapter: MviSavedState<Data>?, private val definition: MviDefinition<Data, Intent, Nothing>.() -> Unit) :
        BaseMviViewModel<Data, Intent, Nothing>(Data(), savedState = adapter, sourceStopTimeoutMillis = 0) {
        lateinit var ui: MviUiApi<Data, Intent>
        override fun MviDefinition<Data, Intent, Nothing>.define() = definition()
    }
}
