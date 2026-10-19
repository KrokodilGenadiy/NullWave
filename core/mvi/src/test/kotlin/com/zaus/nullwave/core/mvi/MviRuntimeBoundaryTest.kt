package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.runtime.MviRuntime
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Boundaries introduced by Main routing and transactional source input preparation. */
@OptIn(ExperimentalCoroutinesApi::class)
class MviRuntimeBoundaryTest {
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
    fun `worker cancellation releases the managed effect consumer on Main`() = runTest(dispatcher) {
        // The worker owns the caller, while collection and lease cleanup stay entirely on Main.
        Dispatchers.setMain(dispatcher)
        val runtime = MviRuntime<Snapshot, Signal>(Snapshot(), backgroundScope, observeSources = {})
        val worker = QueuedDispatcher()
        try {
            val first = backgroundScope.launch(worker) { runtime.collectEffects {} }
            worker.runAll()
            runCurrent()
            assertTrue(first.isActive)
            assertTrue("Collection must stay on Main while waiting for effects", !worker.hasTasks)

            first.cancel()
            worker.runAll()
            runCurrent()
            worker.runAll()

            val received = mutableListOf<Signal>()
            val second = backgroundScope.launch { runtime.collectEffects(received::add) }
            runCurrent()
            assertTrue(runtime.offerEffect(Signal(7)))
            runCurrent()
            second.cancelAndJoin()

            assertTrue(first.isCancelled)
            assertTrue(first.isCompleted)
            assertTrue(second.isCompleted)
            assertEquals(listOf(Signal(7)), received)
        } finally {
            runtime.close()
            runCurrent()
            worker.runAll()
        }
    }

    @Test
    fun `failing later selector preserves state and every earlier source revision`() = runTest(dispatcher) {
        val sourceValues = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        val observedKeys = mutableListOf<String>()
        val vm = ProbeViewModel {
            source(
                "first",
                input = { first },
                flow = { key ->
                    observedKeys += key
                    sourceValues
                },
                reducer = { _, result -> copy(value = result) },
            )
            source(
                "second",
                input = {
                    check(second != "invalid") { "Invalid source input" }
                    second
                },
                flow = { emptyFlow<Unit>() },
                reducer = { _, _ -> this },
            )
            on<Change> { intent -> reduce { copy(first = intent.first, second = intent.second) } }
        }.also { store.put("sources", it) }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        assertEquals(listOf("A"), observedKeys)

        assertThrows(IllegalStateException::class.java) { vm.onIntent(Change("B", "invalid")) }
        assertEquals(Snapshot(), vm.state.value)
        assertTrue(sourceValues.tryEmit(42))
        runCurrent()
        assertEquals(Snapshot(value = 42), vm.state.value)

        vm.onIntent(Change("B", "B"))
        runCurrent()
        assertEquals(listOf("A", "B"), observedKeys)
    }

    @Test
    fun `a superseded restart waiting for cleanup never starts another source group`() = runTest(dispatcher) {
        val firstCleanup = CompletableDeferred<Unit>()
        val laterCleanup = CompletableDeferred<Unit>()
        var starts = 0
        val active = mutableSetOf<Int>()
        val runtime = MviRuntime<Snapshot, Nothing>(
            Snapshot(),
            backgroundScope,
            sourceStopTimeoutMillis = 0,
            observeSources = {
                val generation = ++starts
                active += generation
                try {
                    awaitCancellation()
                } finally {
                    active -= generation
                    withContext(NonCancellable) {
                        if (generation == 1) firstCleanup.await() else laterCleanup.await()
                    }
                }
            },
        )
        try {
            backgroundScope.launch { runtime.withUiBinding { awaitCancellation() } }
            runCurrent()
            assertEquals(setOf(1), active)

            runtime.restartSources()
            runCurrent()
            assertTrue(active.isEmpty())
            runtime.restartSources()
            firstCleanup.complete(Unit)
            runCurrent()

            assertEquals(2, starts)
            assertEquals(setOf(2), active)
        } finally {
            firstCleanup.complete(Unit)
            laterCleanup.complete(Unit)
            runtime.close()
            runCurrent()
        }
    }

    private data class Snapshot(val first: String = "A", val second: String = "A", val value: Int = 0) : UiState
    private data class Change(val first: String, val second: String) : UiIntent
    private data class Signal(val value: Int) : UiEffect

    private class ProbeViewModel(
        private val definition: MviDefinition<Snapshot, Change, Nothing>.() -> Unit,
    ) : BaseMviViewModel<Snapshot, Change, Nothing>(Snapshot(), sourceStopTimeoutMillis = 0) {
        override fun MviDefinition<Snapshot, Change, Nothing>.define() = definition()
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        val hasTasks: Boolean get() = tasks.isNotEmpty()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
