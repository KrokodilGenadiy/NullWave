package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.runtime.operation.MviOperation
import com.zaus.nullwave.core.mvi.runtime.operation.OperationScope
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseMviViewModelOperationTest {
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
    fun `latest reserves before dispatch and runs only the last immediate request`() = runTest(dispatcher) {
        val vm = own()
        val started = mutableListOf<String>()

        val first = vm.latest { started += "first" }
        val second = vm.latest { started += "second" }
        val third = vm.latest {
            started += "third"
            assertTrue(commitState { copy(values = values + "third") })
        }
        assertTrue(started.isEmpty())
        runCurrent()

        assertTrue(first)
        assertTrue(second)
        assertTrue(third)
        assertEquals(listOf("third"), started)
        assertEquals(listOf("third"), vm.state.value.values)
    }

    @Test
    fun `latest keeps only the newest replacement and waits for child cleanup`() = runTest(dispatcher) {
        val vm = own()
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val commits = mutableListOf<Boolean>()
        val first = vm.latest {
            events += "first:start"
            // Attach directly to the operation Job: its body may finish before this child does.
            CoroutineScope(currentCoroutineContext()).launch {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        events += "child:cleanup"
                        cleanup.await()
                        commits += commitState { copy(values = values + "obsolete cleanup") }
                        events += "child:done"
                    }
                }
            }
            awaitCancellation()
        }
        runCurrent()

        val second = vm.latest { events += "second:start" }
        runCurrent()
        val third = vm.latest {
            events += "third:start"
            commitState { copy(values = values + "third") }
        }
        runCurrent()

        assertTrue(first)
        assertTrue(second)
        assertTrue(third)
        assertEquals(listOf("first:start", "child:cleanup"), events)

        cleanup.complete(Unit)
        runCurrent()

        assertEquals(listOf("first:start", "child:cleanup", "child:done", "third:start"), events)
        assertEquals(listOf(false), commits)
        assertEquals(listOf("third"), vm.state.value.values)
    }

    @Test
    fun `latest rejects a cancellation ignoring result and finally before starting replacement`() = runTest(dispatcher) {
        val vm = own()
        val callback = CallbackResult()
        val commits = mutableListOf<Boolean>()
        val started = mutableListOf<String>()
        vm.latest {
            started += "old"
            try {
                val result = callback.await()
                commits += commitState { copy(values = values + result) }
            } finally {
                commits += commitState { copy(values = values + "old finally") }
            }
        }
        runCurrent()
        val replacement = vm.latest {
            started += "new"
            commits += commitState { copy(values = values + "new result") }
        }
        runCurrent()
        assertEquals(listOf("old"), started)
        assertTrue(replacement)

        callback.complete("obsolete result")
        runCurrent()

        assertEquals(listOf(false, false, true), commits)
        assertEquals(listOf("old", "new"), started)
        assertEquals(listOf("new result"), vm.state.value.values)
    }

    @Test
    fun `cancelling a pending latest replacement does not resurrect earlier requests`() = runTest(dispatcher) {
        val vm = own()
        val cleanup = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        vm.latest {
            started += "first"
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { cleanup.await() }
            }
        }
        runCurrent()
        val middle = vm.latest { started += "middle" }
        val last = vm.latest { started += "last" }
        assertTrue(middle)
        assertTrue(last)
        assertTrue(vm.latestWork.cancel())
        runCurrent()

        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("first"), started)

        vm.latest { started += "fresh" }
        runCurrent()
        assertEquals(listOf("first", "fresh"), started)
    }

    @Test
    fun `queue is bounded FIFO and excludes the reserved active job from pending capacity`() = runTest(dispatcher) {
        val vm = own(maxPending = 2)
        val releases = List(3) { CompletableDeferred<Unit>() }
        val events = mutableListOf<String>()
        repeat(3) { index ->
            assertTrue(vm.queue {
                events += "start:$index"
                releases[index].await()
                assertTrue(commitState { copy(values = values + index.toString()) })
                events += "end:$index"
            })
        }
        var rejectedRan = false
        assertFalse(vm.queue { rejectedRan = true })
        assertTrue(events.isEmpty())
        runCurrent()
        assertEquals(listOf("start:0"), events)

        releases.forEachIndexed { index, release ->
            release.complete(Unit)
            runCurrent()
            assertEquals((0..index).map(Int::toString), vm.state.value.values)
        }

        assertFalse(rejectedRan)
        assertEquals(listOf("start:0", "end:0", "start:1", "end:1", "start:2", "end:2"), events)
        assertEquals(listOf("0", "1", "2"), vm.state.value.values)
    }

    @Test
    fun `cancelling a full queue discards pending work and accepts fresh work during cleanup`() = runTest(dispatcher) {
        val vm = own(maxPending = 1)
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        assertTrue(vm.queue {
            events += "active"
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    events += "cleanup:start"
                    cleanup.await()
                    events += "cleanup:end"
                }
            }
        })
        assertTrue(vm.queue { events += "cancelled" })
        assertFalse(vm.queue { events += "overflow" })
        runCurrent()

        assertTrue(vm.queuedWork.cancel())
        // Discarded waiting requests free capacity even before active cancellation is dispatched.
        assertTrue(vm.queue { events += "replacement" })
        runCurrent()
        assertEquals(listOf("active", "cleanup:start"), events)

        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("active", "cleanup:start", "cleanup:end", "replacement"), events)
    }

    @Test
    fun `queue waits for structured children after the operation body returns`() = runTest(dispatcher) {
        val vm = own()
        val childRelease = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        assertTrue(vm.queue {
            // Child belongs to the operation Job; there is no nested scope delaying body return.
            CoroutineScope(currentCoroutineContext()).launch {
                childRelease.await()
                events += "child:done"
            }
            events += "body:done"
        })
        assertTrue(vm.queue { events += "next:start" })
        runCurrent()

        assertEquals(listOf("body:done"), events)

        childRelease.complete(Unit)
        runCurrent()
        assertEquals(listOf("body:done", "child:done", "next:start"), events)
    }

    @Test
    fun `queue cancellation before dispatch skips old bodies and retains capacity until completion`() = runTest(dispatcher) {
        val vm = own(maxPending = 1)
        val started = mutableListOf<String>()
        assertTrue(vm.queue { started += "cancelled active" })
        assertTrue(vm.queue { started += "cancelled pending" })
        assertFalse(vm.queue { started += "overflow" })

        assertTrue(vm.queuedWork.cancel())
        assertTrue(vm.queue { started += "fresh pending" })
        // Cancellation is dispatched: the old active request still occupies its slot until then.
        assertFalse(vm.queue { started += "premature extra" })
        assertTrue(started.isEmpty())
        runCurrent()

        assertEquals(listOf("fresh pending"), started)
        assertTrue(vm.queue { started += "after completion" })
        runCurrent()
        assertEquals(listOf("fresh pending", "after completion"), started)
        assertFalse(vm.queuedWork.cancel())
    }

    @Test
    fun `ifIdle reserves synchronously rejects repeats and accepts again after completion`() = runTest(dispatcher) {
        val vm = own()
        val release = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        assertTrue(vm.ifIdle {
            started += "first"
            release.await()
            assertTrue(commitState { copy(values = values + "first") })
        })
        assertFalse(vm.ifIdle { started += "before dispatch duplicate" })
        runCurrent()
        assertFalse(vm.ifIdle { started += "running duplicate" })
        assertEquals(listOf("first"), started)

        release.complete(Unit)
        runCurrent()
        assertTrue(vm.ifIdle { started += "second" })
        runCurrent()

        assertEquals(listOf("first", "second"), started)
        assertEquals(listOf("first"), vm.state.value.values)
    }

    @Test
    fun `ifIdle remains busy during cancelled operation cleanup`() = runTest(dispatcher) {
        val vm = own()
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val commits = mutableListOf<Boolean>()
        assertTrue(vm.ifIdle {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    events += "cleanup:start"
                    cleanup.await()
                    commits += commitState { copy(values = values + "cancelled finally") }
                    events += "cleanup:end"
                }
            }
        })
        runCurrent()
        assertTrue(vm.idleWork.cancel())
        runCurrent()

        assertEquals(listOf("cleanup:start"), events)
        assertFalse(vm.ifIdle { events += "busy duplicate" })
        cleanup.complete(Unit)
        runCurrent()

        assertTrue(vm.ifIdle { events += "next" })
        runCurrent()
        assertEquals(listOf("cleanup:start", "cleanup:end", "next"), events)
        assertEquals(listOf(false), commits)
        assertTrue(vm.state.value.values.isEmpty())
    }

    @Test
    fun `explicit active cancellation rejects late result and finally for every policy`() = runTest(dispatcher) {
        for (policy in Policy.entries) {
            val vm = own()
            val callback = CallbackResult()
            val attempts = mutableListOf<Boolean>()
            var cancelledBeforeResult = false
            assertTrue(vm.run(policy) {
                try {
                    val result = callback.await()
                    cancelledBeforeResult = !currentCoroutineContext().isActive
                    attempts += commitState { copy(values = values + result) }
                } finally {
                    attempts += commitState { copy(values = values + "cancelled finally") }
                }
            })
            runCurrent()
            assertTrue(vm.operation(policy).cancel())
            callback.complete("late result")
            runCurrent()

            assertTrue("$policy did not cancel the active coroutine", cancelledBeforeResult)
            assertEquals("$policy accepted a cancelled commit", listOf(false, false), attempts)
            assertTrue(vm.state.value.values.isEmpty())
            assertTrue(vm.run(policy) { commitState { copy(values = listOf("fresh")) } })
            runCurrent()
            assertEquals(listOf("fresh"), vm.state.value.values)
        }
    }

    @Test
    fun `independent properties with different policies run together without UI bindings`() = runTest(dispatcher) {
        val vm = own()
        val release = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        var oldLatestCleaned = false
        assertTrue(vm.latest {
            started += "latest:old"
            try {
                awaitCancellation()
            } finally {
                oldLatestCleaned = true
            }
        })
        assertTrue(vm.queue {
            started += "queue"
            release.await()
            commitState { copy(values = values + "queue") }
        })
        assertTrue(vm.ifIdle {
            started += "idle"
            release.await()
            commitState { copy(values = values + "idle") }
        })
        runCurrent()
        vm.latest {
            started += "latest:new"
            commitState { copy(values = values + "latest") }
        }
        runCurrent()

        assertTrue(oldLatestCleaned)
        assertEquals(listOf("latest"), vm.state.value.values)
        assertEquals(setOf("latest:old", "queue", "idle", "latest:new"), started.toSet())
        assertEquals(0, vm.sourceStarts)

        release.complete(Unit)
        runCurrent()
        assertEquals(setOf("latest", "queue", "idle"), vm.state.value.values.toSet())
    }

    @Test
    fun `two latest properties have independent cancellation and remain reusable`() = runTest(dispatcher) {
        val vm = own()
        val started = mutableListOf<String>()
        val cleaned = mutableListOf<String>()
        assertTrue(vm.latest {
            started += "first"
            try {
                awaitCancellation()
            } finally {
                cleaned += "first"
            }
        })
        assertTrue(vm.secondaryLatest.submit {
            started += "independent"
            try {
                awaitCancellation()
            } finally {
                cleaned += "independent"
            }
        })
        runCurrent()

        vm.latest { started += "replacement" }
        runCurrent()
        assertEquals(listOf("first"), cleaned)

        vm.latest { started += "reused" }
        runCurrent()
        assertEquals(listOf("first", "independent", "replacement", "reused"), started)
        assertEquals(listOf("first"), cleaned)
        assertTrue(vm.secondaryLatest.cancel())
        runCurrent()
        assertEquals(listOf("first", "independent"), cleaned)
    }

    @Test
    fun `queue capacity is validated when the property is created and each queue has its own bound`() = runTest(dispatcher) {
        val vm = own()
        assertThrows(IllegalArgumentException::class.java) { vm.createQueue(maxPending = 0) }
        assertThrows(IllegalArgumentException::class.java) { vm.createQueue(maxPending = -1) }
        val oneWaiting = vm.createQueue(maxPending = 1)
        val twoWaiting = vm.createQueue(maxPending = 2)
        assertTrue(oneWaiting.submit { awaitCancellation() })
        assertTrue(oneWaiting.submit {})
        assertFalse(oneWaiting.submit {})
        assertTrue(twoWaiting.submit { awaitCancellation() })
        repeat(2) { assertTrue(twoWaiting.submit {}) }
        assertFalse(twoWaiting.submit {})
    }

    @Test
    fun `default queue property allows sixteen waiting requests in addition to active work`() = runTest(dispatcher) {
        val vm = own()
        assertTrue(vm.queue { awaitCancellation() })

        repeat(16) { assertTrue(vm.queue {}) }
        assertFalse(vm.queue { error("The default pending capacity must stay bounded") })
    }

    @Test
    fun `locally handled repository error permits later work for every policy`() = runTest(dispatcher) {
        for (policy in Policy.entries) {
            val vm = own()
            val release = CompletableDeferred<Unit>()
            assertTrue(vm.run(policy) {
                try {
                    release.await()
                    throw ExpectedRepositoryFailure()
                } catch (_: ExpectedRepositoryFailure) {
                    commitState { copy(values = values + "expected error") }
                }
            })
            if (policy == Policy.Queue) {
                assertTrue(vm.queue { commitState { copy(values = values + "next") } })
            }
            runCurrent()
            release.complete(Unit)
            runCurrent()
            if (policy != Policy.Queue) {
                assertTrue(vm.run(policy) { commitState { copy(values = values + "next") } })
            }
            runCurrent()
            assertEquals(listOf("expected error", "next"), vm.state.value.values)
        }
    }

    @Test
    fun `unexpected operation failure escapes while the queue continues with its next job`() {
        val expected = IllegalStateException("broken command invariant")
        val events = mutableListOf<String>()

        // runTest reports uncaught viewModelScope exceptions at its boundary, like source failures.
        val actual = assertThrows(IllegalStateException::class.java) {
            runTest(dispatcher) {
                val vm = own()
                try {
                    assertTrue(vm.queue {
                        events += "failed"
                        throw expected
                    })
                    assertTrue(vm.queue {
                        events += "next"
                        commitState { copy(values = listOf("next result")) }
                    })
                    runCurrent()

                    assertEquals(listOf("next result"), vm.state.value.values)
                } finally {
                    store.clear()
                    runCurrent()
                }
            }
        }

        assertEquals(expected.message, actual.message)
        assertEquals(listOf("failed", "next"), events)
    }

    @Test
    fun `last child finishing on a worker dispatches the next operation and its commit to Main`() = runTest(dispatcher) {
        val vm = own()
        val mainThread = Thread.currentThread()
        val childRelease = CompletableDeferred<Unit>()
        val completedOn = CompletableDeferred<Thread>()
        val nextBodyOn = CompletableDeferred<Thread>()
        val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            assertTrue(vm.queue {
                currentCoroutineContext().job.invokeOnCompletion {
                    completedOn.complete(Thread.currentThread())
                }
                CoroutineScope(currentCoroutineContext()).launch(worker) { childRelease.await() }
            })
            assertTrue(vm.queue {
                nextBodyOn.complete(Thread.currentThread())
                assertTrue(commitState {
                    assertSame("Reducer must execute on Main", mainThread, Thread.currentThread())
                    copy(values = listOf("next result"))
                })
            })
            runCurrent()
            assertFalse(completedOn.isCompleted)
            assertFalse(nextBodyOn.isCompleted)

            // Body already returned; completing the last child finishes its parent on the worker.
            childRelease.complete(Unit)
            val completionThread = completedOn.await()
            runCurrent()

            assertNotSame(mainThread, completionThread)
            assertSame(mainThread, nextBodyOn.await())
            assertEquals(listOf("next result"), vm.state.value.values)
        } finally {
            childRelease.complete(Unit)
            store.clear()
            worker.close()
        }
    }

    @Test
    fun `clearing the VM cancels running and pending work and rejects future commands`() = runTest(dispatcher) {
        val vm = own()
        val latestCallback = CallbackResult()
        val queueCallback = CallbackResult()
        val idleCallback = CallbackResult()
        val attempts = mutableListOf<Boolean>()
        val cancelled = mutableListOf<Boolean>()
        val started = mutableListOf<String>()
        assertTrue(vm.run(Policy.Latest) {
            started += "latest:active"
            val value = latestCallback.await()
            cancelled += !currentCoroutineContext().isActive
            attempts += commitState { copy(values = values + value) }
        })
        assertTrue(vm.run(Policy.Queue) {
            started += "queue:active"
            val value = queueCallback.await()
            cancelled += !currentCoroutineContext().isActive
            attempts += commitState { copy(values = values + value) }
        })
        assertTrue(vm.run(Policy.IfIdle) {
            started += "idle:active"
            val value = idleCallback.await()
            cancelled += !currentCoroutineContext().isActive
            attempts += commitState { copy(values = values + value) }
        })
        runCurrent()
        assertTrue(vm.latest { started += "latest:pending" })
        assertTrue(vm.queue { started += "queue:pending" })
        runCurrent()

        store.clear()
        runCurrent()
        assertFalse(vm.run(Policy.Latest) { started += "latest:after clear" })
        assertFalse(vm.queue { started += "queue:after clear" })
        assertFalse(vm.ifIdle { started += "idle:after clear" })
        latestCallback.complete("late latest")
        queueCallback.complete("late queue")
        idleCallback.complete("late idle")
        runCurrent()

        assertEquals(listOf(true, true, true), cancelled)
        assertEquals(listOf(false, false, false), attempts)
        assertEquals(setOf("latest:active", "queue:active", "idle:active"), started.toSet())
        assertTrue(vm.state.value.values.isEmpty())
    }

    @Test
    fun `completed operation scope cannot commit from a retained callback`() = runTest(dispatcher) {
        for (policy in Policy.entries) {
            val vm = own()
            lateinit var lateCommit: () -> Boolean
            assertTrue(vm.run(policy) {
                assertTrue(commitState { copy(values = listOf("accepted")) })
                lateCommit = { commitState { copy(values = listOf("too late")) } }
            })
            runCurrent()

            assertFalse("$policy allowed a commit after completion", lateCommit())
            assertEquals(listOf("accepted"), vm.state.value.values)
        }
    }

    @Test
    fun `reentrant latest submission from cancelled finally remains the newest request`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = own()
        val events = mutableListOf<String>()
        val attempts = mutableListOf<Boolean>()
        var nestedAccepted: Boolean? = null
        vm.latest {
            try {
                events += "first"
                awaitCancellation()
            } finally {
                attempts += commitState { copy(values = values + "old finally") }
                nestedAccepted = vm.latest {
                    events += "nested"
                    commitState { copy(values = values + "newest") }
                }
            }
        }

        val superseded = vm.latest { events += "superseded" }
        runCurrent()

        assertTrue(superseded)
        assertEquals(true, nestedAccepted)
        assertEquals(listOf(false), attempts)
        assertEquals(listOf("first", "nested"), events)
        assertEquals(listOf("newest"), vm.state.value.values)
    }

    private fun own(maxPending: Int = 16): OperationViewModel = OperationViewModel(maxPending).also {
        store.put("operation-test-${nextKey++}", it)
    }

    private enum class Policy { Latest, Queue, IfIdle }

    private data class OperationState(val values: List<String> = emptyList()) : UiState

    private class ExpectedRepositoryFailure : Exception()

    /** Deliberately models a broken callback adapter which ignores coroutine cancellation. */
    private class CallbackResult {
        private lateinit var continuation: Continuation<String>

        suspend fun await(): String = suspendCoroutine { continuation = it }

        fun complete(value: String) {
            continuation.resume(value)
        }
    }

    private class OperationViewModel(maxPending: Int) :
        MviTestViewModel<OperationState, Nothing, Nothing>(OperationState()) {
        val latestWork = latestOperation()
        val secondaryLatest = latestOperation()
        val queuedWork = queuedOperation(maxPending)
        val idleWork = idleOperation()

        var sourceStarts = 0
            private set

        override fun onIntent(intent: Nothing) = Unit

        override suspend fun observeSources() {
            sourceStarts++
            awaitCancellation()
        }

        fun latest(
            block: suspend OperationScope<OperationState>.() -> Unit,
        ): Boolean = latestWork.submit(block = block)

        fun queue(
            block: suspend OperationScope<OperationState>.() -> Unit,
        ): Boolean = queuedWork.submit(block = block)

        fun ifIdle(
            block: suspend OperationScope<OperationState>.() -> Unit,
        ): Boolean = idleWork.submit(block = block)

        fun createQueue(maxPending: Int): MviOperation<OperationState> = queuedOperation(maxPending)

        fun run(
            policy: Policy,
            block: suspend OperationScope<OperationState>.() -> Unit,
        ): Boolean = operation(policy).submit(block = block)

        fun operation(policy: Policy): MviOperation<OperationState> = when (policy) {
            Policy.Latest -> latestWork
            Policy.Queue -> queuedWork
            Policy.IfIdle -> idleWork
        }
    }
}
