package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.runtime.operation.OperationScope
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
class BaseMviViewModelAcceptanceTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextKey = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `acceptance updates state synchronously before dispatch for every policy`() = runTest(dispatcher) {
        for (policy in Policy.entries) {
            val vm = own()
            var started = false
            val accepted = vm.run(policy, onAccepted = { copy(query = "accepted", loading = true) }) {
                started = true
                assertEquals(AcceptanceState(query = "accepted", loading = true), vm.state.value)
            }

            assertTrue(accepted)
            assertEquals(AcceptanceState(query = "accepted", loading = true), vm.state.value)
            assertFalse("$policy dispatched before its scheduled turn", started)
            runCurrent()
            assertTrue(started)
        }
    }

    @Test
    fun `eager completion cannot be overwritten by acceptance for every policy`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        for (policy in Policy.entries) {
            val vm = own()
            val accepted = vm.run(policy, onAccepted = { copy(query = "accepted", loading = true) }) {
                assertEquals("accepted", vm.state.value.query)
                assertTrue(vm.state.value.loading)
                assertTrue(commitState { copy(result = "finished", loading = false) })
            }

            assertTrue(accepted)
            assertEquals(AcceptanceState(query = "accepted", result = "finished"), vm.state.value)
        }
    }

    @Test
    fun `latest revokes old commits before publishing the newly accepted input`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = own()
        lateinit var oldCallback: () -> Boolean
        vm.run(Policy.Latest, onAccepted = { copy(query = "old", loading = true) }) {
            oldCallback = { commitState { copy(result = "old result", loading = false) } }
            awaitCancellation()
        }
        var callbackResult: Boolean? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { state ->
                if (state.query == "new" && callbackResult == null) {
                    callbackResult = oldCallback()
                }
            }
        }

        vm.run(Policy.Latest, onAccepted = { copy(query = "new", loading = true) }) {
            awaitCancellation()
        }

        assertEquals(false, callbackResult)
        assertEquals(AcceptanceState(query = "new", loading = true), vm.state.value)
    }

    @Test
    fun `newer latest submitted during acceptance supersedes the outer request`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = own()
        val started = mutableListOf<String>()
        var nestedAccepted: Boolean? = null
        vm.run(Policy.Latest, onAccepted = { copy(query = "A") }) {
            started += "A"
            awaitCancellation()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { state ->
                if (state.query == "B") {
                    nestedAccepted = vm.run(Policy.Latest, onAccepted = { copy(query = "C") }) {
                        started += "C"
                        commitState { copy(result = "C result") }
                    }
                }
            }
        }

        val outer = vm.run(Policy.Latest, onAccepted = { copy(query = "B") }) { started += "B" }
        runCurrent()

        // Acceptance is historical: the nested intent may supersede this request before return.
        assertTrue(outer)
        assertEquals(true, nestedAccepted)
        assertEquals(listOf("A", "C"), started)
        assertEquals(AcceptanceState(query = "C", result = "C result"), vm.state.value)
    }

    @Test
    fun `queued acceptance is immediate and a full queue leaves state unchanged`() = runTest(dispatcher) {
        val vm = own()
        val release = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        vm.run(Policy.Queue, onAccepted = { copy(query = "active") }) {
            started += "active"
            release.await()
        }
        runCurrent()
        val pending = vm.run(Policy.Queue, onAccepted = { copy(query = "waiting") }) {
            started += "waiting"
            assertEquals("waiting", vm.state.value.query)
        }

        assertTrue(pending)
        assertEquals("waiting", vm.state.value.query)
        assertEquals(listOf("active"), started)
        assertFalse(vm.run(Policy.Queue, onAccepted = { copy(query = "overflow") }) {
            error("Rejected queue work must not execute")
        })
        assertEquals("waiting", vm.state.value.query)

        release.complete(Unit)
        runCurrent()
        assertEquals(listOf("active", "waiting"), started)
    }

    @Test
    fun `busy ifIdle does not apply acceptance before or after dispatch`() = runTest(dispatcher) {
        val vm = own()
        vm.run(Policy.IfIdle, onAccepted = { copy(query = "accepted") }) { awaitCancellation() }

        repeat(2) {
            assertFalse(vm.run(Policy.IfIdle, onAccepted = { copy(query = "duplicate") }) {
                error("Rejected duplicate must not execute")
            })
            assertEquals("accepted", vm.state.value.query)
            runCurrent()
        }
    }

    @Test
    fun `cleared ViewModel does not apply acceptance for any policy`() = runTest(dispatcher) {
        val viewModels = Policy.entries.associateWith { own() }
        store.clear()

        for ((policy, vm) in viewModels) {
            val accepted = vm.run(policy, onAccepted = { copy(query = "after clear") }) {
                error("Cleared ViewModel must not execute work")
            }
            assertFalse(accepted)
            assertEquals(AcceptanceState(), vm.state.value)
        }
    }

    @Test
    fun `independent idle properties each reserve without sharing acceptance or rejection`() = runTest(dispatcher) {
        val vm = own()
        assertTrue(vm.run(Policy.IfIdle, onAccepted = { copy(query = "first") }) {
            awaitCancellation()
        })
        assertTrue(vm.independentIdle.submit(onAccepted = { copy(query = "second") }) {
            awaitCancellation()
        })

        assertFalse(vm.run(Policy.IfIdle, onAccepted = { copy(query = "rejected first") }) {})
        assertFalse(vm.independentIdle.submit(onAccepted = { copy(query = "rejected second") }) {})
        assertEquals("second", vm.state.value.query)
    }

    @Test
    fun `throwing acceptance propagates and releases reservation for every policy`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        for (policy in Policy.entries) {
            val vm = own()
            val expected = IllegalStateException("invalid accepted state")
            var failedBodyStarted = false
            val actual = assertThrows(IllegalStateException::class.java) {
                vm.run(policy, onAccepted = { throw expected }) { failedBodyStarted = true }
            }

            assertSame(expected, actual)
            assertFalse(failedBodyStarted)
            assertEquals(AcceptanceState(), vm.state.value)
            val recovered = vm.run(policy, onAccepted = { copy(query = "recovered") }) {
                commitState { copy(result = "success") }
            }
            assertTrue(recovered)
            assertEquals(AcceptanceState(query = "recovered", result = "success"), vm.state.value)
        }
    }

    @Test
    fun `failed latest acceptance still revokes and cancels its predecessor`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = own()
        var cleanupCommit: Boolean? = null
        assertTrue(vm.run(Policy.Latest, onAccepted = { copy(query = "old") }) {
            try {
                awaitCancellation()
            } finally {
                cleanupCommit = commitState { copy(result = "old cleanup") }
            }
        })

        assertThrows(IllegalStateException::class.java) {
            vm.run(Policy.Latest, onAccepted = { error("reducer failed") }) {
                error("Failed acceptance must not execute work")
            }
        }

        assertEquals(false, cleanupCommit)
        assertEquals(AcceptanceState(query = "old"), vm.state.value)
        assertTrue(vm.run(Policy.Latest, onAccepted = { copy(query = "recovered") }) {})
        assertEquals("recovered", vm.state.value.query)
    }

    @Test
    fun `failed queued acceptance frees capacity without cancelling active work`() = runTest(dispatcher) {
        val vm = own()
        val release = CompletableDeferred<Unit>()
        var activeFinished = false
        assertTrue(vm.run(Policy.Queue) {
            release.await()
            activeFinished = true
        })
        runCurrent()

        assertThrows(IllegalStateException::class.java) {
            vm.run(Policy.Queue, onAccepted = { error("reducer failed") }) {
                error("Failed acceptance must not execute work")
            }
        }
        assertFalse(activeFinished)
        val replacement = vm.run(Policy.Queue, onAccepted = { copy(query = "replacement") }) {
            commitState { copy(result = "success") }
        }
        assertTrue(replacement)
        release.complete(Unit)
        runCurrent()

        assertTrue(activeFinished)
        assertEquals(AcceptanceState(query = "replacement", result = "success"), vm.state.value)
    }

    @Test
    fun `latest cancellation cleanup sees accepted input and its nested submission wins`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = own()
        val started = mutableListOf<String>()
        var cleanupState: AcceptanceState? = null
        var cleanupCommit: Boolean? = null
        vm.run(Policy.Latest, onAccepted = { copy(query = "A") }) {
            started += "A"
            try {
                awaitCancellation()
            } finally {
                cleanupState = vm.state.value
                cleanupCommit = commitState { copy(result = "A cleanup") }
                vm.run(Policy.Latest, onAccepted = { copy(query = "C") }) { started += "C" }
            }
        }

        val superseded = vm.run(Policy.Latest, onAccepted = { copy(query = "B") }) { started += "B" }
        runCurrent()

        assertEquals(AcceptanceState(query = "B"), cleanupState)
        assertEquals(false, cleanupCommit)
        assertTrue(superseded)
        assertEquals(listOf("A", "C"), started)
        assertEquals("C", vm.state.value.query)
    }

    @Test
    fun `ifIdle reservation rejects a duplicate submitted by state publication`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = own()
        var duplicateAttempted = false
        var duplicateAccepted: Boolean? = null
        var acceptedStarted = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { state ->
                if (state.query == "accepted" && !duplicateAttempted) {
                    duplicateAttempted = true
                    duplicateAccepted = vm.run(Policy.IfIdle, onAccepted = { copy(query = "duplicate") }) {
                        error("Reentrant duplicate must not execute")
                    }
                    assertFalse(acceptedStarted)
                }
            }
        }

        vm.run(Policy.IfIdle, onAccepted = { copy(query = "accepted") }) {
            acceptedStarted = true
            awaitCancellation()
        }

        assertTrue(duplicateAttempted)
        assertEquals(false, duplicateAccepted)
        assertTrue(acceptedStarted)
        assertEquals("accepted", vm.state.value.query)
    }

    @Test
    fun `reentrant queue admission preserves FIFO capacity and waits for acceptance publication`() =
        runTest(dispatcher) {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val vm = own()
            val started = mutableListOf<String>()
            var nestedAttempted = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.state.collect { state ->
                    if (state.query == "outer" && !nestedAttempted) {
                        nestedAttempted = true
                        assertTrue(vm.run(Policy.Queue, onAccepted = { copy(query = "nested") }) {
                            started += "nested"
                        })
                        // The outer request still reserves the execution slot during publication.
                        assertFalse(vm.run(Policy.Queue, onAccepted = { copy(query = "overflow") }) {
                            error("Full reentrant queue must not execute work")
                        })
                        assertTrue(started.isEmpty())
                    }
                }
            }

            vm.run(Policy.Queue, onAccepted = { copy(query = "outer") }) { started += "outer" }
            runCurrent()

            assertTrue(nestedAttempted)
            assertEquals(listOf("outer", "nested"), started)
            assertEquals("nested", vm.state.value.query)
        }

    @Test
    fun `cancelling accepted queued work does not roll back state or enter its finally`() = runTest(dispatcher) {
        val vm = own()
        vm.run(Policy.Queue) { awaitCancellation() }
        runCurrent()
        var bodyStarted = false
        var cleanupRan = false
        val waiting = vm.run(Policy.Queue, onAccepted = { copy(query = "accepted waiting") }) {
            bodyStarted = true
            try {
                awaitCancellation()
            } finally {
                cleanupRan = true
            }
        }

        assertTrue(waiting)
        assertTrue(vm.cancel(Policy.Queue))
        runCurrent()

        assertFalse(bodyStarted)
        assertFalse(cleanupRan)
        assertEquals("accepted waiting", vm.state.value.query)
    }

    @Test
    fun `submit remains accepted when state publication immediately cancels its property`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        for (policy in Policy.entries) {
            val vm = own()
            var cancellationAccepted: Boolean? = null
            var bodyStarted = false
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.state.collect { state ->
                    if (state.query == "accepted" && cancellationAccepted == null) {
                        cancellationAccepted = vm.cancel(policy)
                    }
                }
            }

            val accepted = vm.run(policy, onAccepted = { copy(query = "accepted") }) {
                bodyStarted = true
            }

            assertTrue("$policy must report historical acceptance", accepted)
            assertEquals(true, cancellationAccepted)
            assertFalse("$policy ran a request cancelled during acceptance", bodyStarted)
            assertEquals("accepted", vm.state.value.query)
            observer.cancel()
            assertTrue(vm.run(policy) { commitState { copy(result = "reused") } })
            assertEquals("reused", vm.state.value.result)
        }
    }

    @Test
    fun `submit remains accepted when publication clears the ViewModel before its body starts`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        for (policy in Policy.entries) {
            val vm = own()
            var bodyStarted = false
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.state.collect { state ->
                    if (state.query == "accepted") store.clear()
                }
            }

            val accepted = vm.run(policy, onAccepted = { copy(query = "accepted") }) {
                bodyStarted = true
            }

            assertTrue(accepted)
            assertFalse(bodyStarted)
            assertFalse(vm.run(policy) { error("Cleared ViewModel must reject new work") })
            observer.cancel()
        }
    }

    private fun own(): AcceptanceViewModel = AcceptanceViewModel().also {
        store.put("acceptance-${nextKey++}", it)
    }

    private enum class Policy { Latest, Queue, IfIdle }

    private data class AcceptanceState(
        val query: String = "",
        val result: String? = null,
        val loading: Boolean = false,
    ) : UiState

    private class AcceptanceViewModel : MviTestViewModel<AcceptanceState, Nothing, Nothing>(AcceptanceState()) {
        private val latest = latestOperation()
        private val queue = queuedOperation(maxPending = 1)
        private val idle = idleOperation()
        val independentIdle = idleOperation()

        override fun onIntent(intent: Nothing) = Unit

        fun run(
            policy: Policy,
            onAccepted: (AcceptanceState.() -> AcceptanceState)? = null,
            block: suspend OperationScope<AcceptanceState>.() -> Unit,
        ): Boolean = when (policy) {
            Policy.Latest -> latest.submit(onAccepted = onAccepted, block = block)
            Policy.Queue -> queue.submit(onAccepted = onAccepted, block = block)
            Policy.IfIdle -> idle.submit(onAccepted = onAccepted, block = block)
        }

        fun cancel(policy: Policy): Boolean = when (policy) {
            Policy.Latest -> latest.cancel()
            Policy.Queue -> queue.cancel()
            Policy.IfIdle -> idle.cancel()
        }
    }
}
