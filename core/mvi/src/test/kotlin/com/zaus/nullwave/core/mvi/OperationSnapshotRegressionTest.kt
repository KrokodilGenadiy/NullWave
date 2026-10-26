package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.dsl.OperationKey
import com.zaus.nullwave.core.mvi.state.OperationStates
import com.zaus.nullwave.core.mvi.state.OperationStatus
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationSnapshotRegressionTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val key = OperationKey<Int>("save")

    @Before fun setUp() = Dispatchers.setMain(AndroidLikeTestMainDispatcher(dispatcher))
    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun `queued rejected acceptance preserves active request and frees pending capacity`() = runTest(dispatcher) {
        val first = CompletableDeferred<Int>()
        val failure = IllegalStateException("acceptance")
        val executed = mutableListOf<Int>()
        val vm = own {
            val save = queued<Int, Int>(key, maxPending = 1) {
                onAccepted { input -> if (input == 2) throw failure; this }
                execute { input -> executed += input; if (input == 1) first.await() else input }
                onResult { _, result -> copy(results = results + result) }
            }
            on<Start> { submit(save, it.value) }
        }
        vm.ui.onIntent(Start(1))
        assertSame(failure, assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Start(2)) })
        checkStatus(vm, true, true, 0, true)
        vm.ui.onIntent(Start(3))
        checkStatus(vm, true, true, 1, false)
        first.complete(1)
        runCurrent()
        assertEquals(listOf(1, 3), executed)
        assertEquals(listOf(1, 3), vm.snapshot.value.state.results)
        checkStatus(vm, false, false, 0, true)
    }

    @Test fun `latest failed acceptance still revokes old result and tracks its cleanup`() = runTest(dispatcher) {
        val cleanup = CompletableDeferred<Unit>()
        val failure = IllegalStateException("acceptance")
        val executed = mutableListOf<Int>()
        val vm = own {
            val save = latest<Int, Int>(key) {
                onAccepted { input -> if (input == 2) throw failure; this }
                execute { input ->
                    executed += input
                    if (input == 1) withContext(NonCancellable) { cleanup.await() }
                    input
                }
                onResult { _, result -> copy(results = results + result) }
            }
            on<Start> { submit(save, it.value) }
        }
        try {
            vm.ui.onIntent(Start(1))
            assertSame(failure, assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Start(2)) })
            checkStatus(vm, true, false, 0, true)
            vm.ui.onIntent(Start(3))
            checkStatus(vm, true, false, 1, true)
            cleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf(1, 3), executed)
            assertEquals(listOf(3), vm.snapshot.value.state.results)
            checkStatus(vm, false, false, 0, true)
        } finally { cleanup.complete(Unit) }
    }

    @Test fun `throwing cancellation reducer still drops queue and permits new input behind cleanup`() = runTest(dispatcher) {
        val cleanup = CompletableDeferred<Unit>()
        val failure = IllegalStateException("cancellation")
        val executed = mutableListOf<Int>()
        val vm = own {
            val save = queued<Int, Int>(key, maxPending = 1) {
                execute { input ->
                    executed += input
                    if (input == 1) try { awaitCancellation() } finally {
                        withContext(NonCancellable) { cleanup.await() }
                    }
                    input
                }
                onResult { _, result -> copy(results = results + result) }
                onCancelled { throw failure }
            }
            on<Start> { submit(save, it.value) }
            on<Stop> { cancel(save) }
        }
        try {
            vm.ui.onIntent(Start(1))
            vm.ui.onIntent(Start(2))
            assertSame(failure, assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Stop) })
            checkStatus(vm, true, false, 0, true)
            vm.ui.onIntent(Start(3))
            checkStatus(vm, true, false, 1, false)
            cleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf(1, 3), executed)
            assertEquals(listOf(3), vm.snapshot.value.state.results)
            checkStatus(vm, false, false, 0, true)
        } finally { cleanup.complete(Unit) }
    }

    @Test fun `status wrapper takes a defensive map snapshot`() {
        val status = OperationStatus(false, false, 0, true)
        val input = mutableMapOf<OperationKey<*>, OperationStatus>(key to status)
        val snapshot = OperationStates(true, input)
        val hash = snapshot.hashCode()
        input.clear()
        assertSame(status, snapshot[key])
        assertEquals(hash, snapshot.hashCode())
    }

    private fun checkStatus(vm: Vm, busy: Boolean, executing: Boolean, pending: Int, canSubmit: Boolean) {
        val status = vm.snapshot.value.operations[key]
        assertEquals(busy, status.isBusy)
        assertEquals(executing, status.isExecuting)
        assertEquals(pending, status.pendingCount)
        assertEquals(canSubmit, status.canSubmit)
    }
    private suspend fun own(block: MviDefinition<Data, Intent, Nothing>.() -> Unit): Vm =
        Vm(block).also { store.put("probe", it); it.ui = it.uiApi() }
    private data class Data(val results: List<Int> = emptyList()) : UiState
    private sealed interface Intent : UiIntent
    private data class Start(val value: Int) : Intent
    private data object Stop : Intent
    private class Vm(private val definition: MviDefinition<Data, Intent, Nothing>.() -> Unit) :
        BaseMviViewModel<Data, Intent, Nothing>(Data()) {
        lateinit var ui: MviUiApi<Data, Intent>
        override fun MviDefinition<Data, Intent, Nothing>.define() = definition()
    }
}
