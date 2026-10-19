package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.runtime.operation.MviOperation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MviOperationCancellationTest {
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
    fun `every policy revokes old commits before publishing cancellation state`() = runTest(dispatcher) {
        for (policy in Policy.entries) {
            val vm = own()
            val operation = vm.operation(policy)
            val cleanupAttempts = mutableListOf<Boolean>()
            val publicationAttempts = mutableListOf<Boolean>()
            val nestedCancellations = mutableListOf<Boolean>()
            lateinit var lateCommit: () -> Boolean
            assertTrue(operation.submit(onAccepted = { copy(loading = true) }) {
                lateCommit = { commitState { copy(value = "obsolete result") } }
                try {
                    awaitCancellation()
                } finally {
                    cleanupAttempts += commitState { copy(value = "obsolete cleanup") }
                }
            })
            runCurrent()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.state.collect { state ->
                    if (state.value == "cancelled" && publicationAttempts.isEmpty()) {
                        // The original Job is still active until this publication returns.
                        publicationAttempts += lateCommit()
                        nestedCancellations += operation.cancel {
                            error("An already selected cancellation must not publish again")
                        }
                    }
                }
            }

            assertTrue(operation.cancel { copy(value = "cancelled", loading = false) })
            assertEquals(OperationState(value = "cancelled"), vm.state.value)
            assertTrue(cleanupAttempts.isEmpty())
            runCurrent()

            assertEquals(listOf(false), publicationAttempts)
            assertEquals(listOf(false), nestedCancellations)
            assertEquals(listOf(false), cleanupAttempts)
            assertEquals(OperationState(value = "cancelled"), vm.state.value)
            assertFalse(operation.cancel { error("An idle operation must not publish state") })
        }
    }

    @Test
    fun `latest cancellation does not overwrite a new search submitted by old cleanup`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = own()
        val release = CompletableDeferred<Unit>()
        val cleanupAttempts = mutableListOf<Boolean>()
        var replacementAccepted = false
        var replacementStarted = false
        assertTrue(vm.latest.submit(
            onAccepted = { copy(value = "old search", loading = true) },
        ) {
            try {
                awaitCancellation()
            } finally {
                cleanupAttempts += commitState { copy(loading = false) }
                replacementAccepted = vm.latest.submit(
                    onAccepted = { copy(value = "new search", loading = true) },
                ) {
                    replacementStarted = true
                    release.await()
                    commitState { copy(value = "new result", loading = false) }
                }
            }
        })

        assertTrue(vm.latest.cancel { copy(loading = false) })
        runCurrent()

        assertEquals(listOf(false), cleanupAttempts)
        assertTrue(replacementAccepted)
        assertTrue(replacementStarted)
        assertEquals(OperationState(value = "new search", loading = true), vm.state.value)

        release.complete(Unit)
        runCurrent()
        assertEquals(OperationState(value = "new result"), vm.state.value)
    }

    @Test
    fun `latest cancellation preserves new work submitted during cancellation state publication`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = own()
        val events = mutableListOf<String>()
        var replacementAccepted = false
        var nestedSubmitted = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { state ->
                if (state.value == "cancelled" && !nestedSubmitted) {
                    nestedSubmitted = true
                    replacementAccepted = vm.latest.submit(
                        onAccepted = { copy(value = "new search", loading = true) },
                    ) {
                        events += "new:start"
                        awaitCancellation()
                    }
                }
            }
        }
        assertTrue(vm.latest.submit(
            onAccepted = { copy(value = "old search", loading = true) },
        ) {
            try {
                events += "old:start"
                awaitCancellation()
            } finally {
                events += "old:cleanup"
                assertFalse(commitState { copy(value = "obsolete cleanup", loading = false) })
            }
        })

        assertTrue(vm.latest.cancel { copy(value = "cancelled", loading = false) })
        runCurrent()

        assertTrue(nestedSubmitted)
        assertTrue(replacementAccepted)
        assertEquals(listOf("old:start", "old:cleanup", "new:start"), events)
        assertEquals(OperationState(value = "new search", loading = true), vm.state.value)
    }

    @Test
    fun `queue cancellation clears its snapshot and preserves work submitted during publication`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = own()
        val events = mutableListOf<String>()
        assertTrue(vm.queue.submit {
            try {
                events += "active"
                awaitCancellation()
            } finally {
                events += "cleanup"
                assertFalse(commitState { copy(value = "obsolete cleanup") })
            }
        })
        assertTrue(vm.queue.submit { events += "first pending" })
        assertTrue(vm.queue.submit { events += "second pending" })
        var freshAccepted = false
        var nestedSubmitted = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { state ->
                if (state.value == "cancelled" && !nestedSubmitted) {
                    nestedSubmitted = true
                    freshAccepted = vm.queue.submit {
                        events += "fresh"
                        commitState { copy(value = "fresh result") }
                    }
                    assertEquals(listOf("active"), events)
                }
            }
        }

        assertTrue(vm.queue.cancel { copy(value = "cancelled") })
        runCurrent()

        assertTrue(nestedSubmitted)
        assertTrue(freshAccepted)
        assertEquals(listOf("active", "cleanup", "fresh"), events)
        assertEquals(OperationState(value = "fresh result"), vm.state.value)
    }

    @Test
    fun `idle operation stays busy through cleanup and repeated cancellation is a no-op`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = own()
        val cleanupRelease = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        assertTrue(vm.idle.submit(onAccepted = { copy(loading = true) }) {
            try {
                awaitCancellation()
            } finally {
                events += "cleanup started"
                withContext(NonCancellable) { cleanupRelease.await() }
                events += "cleanup finished"
            }
        })

        assertTrue(vm.idle.cancel { copy(value = "cancelled", loading = false) })
        assertEquals(listOf("cleanup started"), events)
        assertFalse(vm.idle.cancel { error("Cancellation already selected this request") })
        assertFalse(vm.idle.submit { error("Cleanup still owns the operation") })
        assertEquals(OperationState(value = "cancelled"), vm.state.value)

        cleanupRelease.complete(Unit)
        runCurrent()
        assertTrue(vm.idle.submit {
            events += "fresh"
            commitState { copy(value = "fresh result") }
        })
        runCurrent()

        assertEquals(listOf("cleanup started", "cleanup finished", "fresh"), events)
        assertEquals(OperationState(value = "fresh result"), vm.state.value)
    }

    @Test
    fun `cancelling idle or cleared operations does not invoke the reducer`() = runTest(dispatcher) {
        val vm = own()
        for (policy in Policy.entries) {
            assertFalse(vm.operation(policy).cancel { error("There is no work to cancel") })
            assertTrue(vm.operation(policy).submit { awaitCancellation() })
        }
        runCurrent()
        store.clear()
        runCurrent()

        for (policy in Policy.entries) {
            assertFalse(vm.operation(policy).cancel { error("The ViewModel was cleared") })
            assertFalse(vm.operation(policy).submit { error("Cleared owners reject new work") })
        }
        assertEquals(OperationState(), vm.state.value)
    }

    @Test
    fun `throwing cancellation reducer still cancels all selected queue work`() = runTest(dispatcher) {
        val vm = own()
        val cleanupAttempts = mutableListOf<Boolean>()
        assertTrue(vm.queue.submit(onAccepted = { copy(loading = true) }) {
            try {
                awaitCancellation()
            } finally {
                cleanupAttempts += commitState { copy(value = "obsolete cleanup") }
            }
        })
        assertTrue(vm.queue.submit { error("Cancelled queued work must not run") })
        runCurrent()
        val expected = IllegalStateException("invalid cancellation reducer")

        val actual = assertThrows(IllegalStateException::class.java) {
            vm.queue.cancel { throw expected }
        }
        runCurrent()

        assertEquals(expected.message, actual.message)
        assertEquals(listOf(false), cleanupAttempts)
        assertEquals(OperationState(loading = true), vm.state.value)

        assertTrue(vm.queue.submit { commitState { copy(value = "fresh result", loading = false) } })
        runCurrent()
        assertEquals(OperationState(value = "fresh result"), vm.state.value)
    }

    private fun own(): OperationViewModel = OperationViewModel().also {
        store.put("cancellation-test-${nextKey++}", it)
    }

    private enum class Policy { Latest, Queue, IfIdle }

    private data class OperationState(val value: String = "", val loading: Boolean = false) : UiState

    private class OperationViewModel : MviTestViewModel<OperationState, Nothing, Nothing>(OperationState()) {
        val latest = latestOperation()
        val queue = queuedOperation(maxPending = 2)
        val idle = idleOperation()

        override fun onIntent(intent: Nothing) = Unit

        fun operation(policy: Policy): MviOperation<OperationState> = when (policy) {
            Policy.Latest -> latest
            Policy.Queue -> queue
            Policy.IfIdle -> idle
        }
    }
}
