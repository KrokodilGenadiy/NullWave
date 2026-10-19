package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class BaseMviViewModelLifecycleTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = ViewModelStore()
    }

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `construction never invokes an override even with eager Main dispatch`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = viewModel()

        assertTrue(viewModel.initialized)
        assertEquals(0, viewModel.starts)
        runCurrent()
        assertEquals(0, viewModel.starts)

        backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        assertEquals(1, viewModel.starts)
    }

    @Test
    fun `two UI bindings share one source group and cancelling one keeps it alive`() = runTest {
        val viewModel = viewModel()
        val first = backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()

        assertEquals(1, viewModel.starts)
        assertEquals(1, viewModel.activeGroups)

        first.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT * 2)
        runCurrent()

        assertEquals(1, viewModel.starts)
        assertEquals(1, viewModel.activeGroups)
        assertEquals(0, viewModel.stops)
    }

    @Test
    fun `return before timeout preserves the existing source group`() = runTest {
        val viewModel = viewModel()
        val first = backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        first.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT - 1)

        backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT * 2)
        runCurrent()

        assertEquals(1, viewModel.starts)
        assertEquals(1, viewModel.activeGroups)
        assertEquals(0, viewModel.stops)
    }

    @Test
    fun `last binding cancellation cleans sources up only after the grace period`() = runTest {
        var resourceRegistered = false
        val viewModel = viewModel {
            resourceRegistered = true
            try {
                awaitCancellation()
            } finally {
                resourceRegistered = false
            }
        }
        val collector = backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        collector.cancel()
        runCurrent()

        advanceTimeBy(STOP_TIMEOUT - 1)
        runCurrent()
        assertTrue(resourceRegistered)
        assertEquals(0, viewModel.stops)

        advanceTimeBy(1)
        runCurrent()
        assertFalse(resourceRegistered)
        assertEquals(0, viewModel.activeGroups)
        assertEquals(1, viewModel.stops)
    }

    @Test
    fun `return first exposes current canonical state then obtains a fresh source snapshot`() = runTest {
        val source = MutableStateFlow(3)
        val viewModel = viewModel { source.collect { writeSource(it) } }
        val first = backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        assertEquals(3, viewModel.state.value.source)
        first.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()

        viewModel.writeQuery("changed while hidden")
        source.value = 70
        runCurrent()
        assertEquals(3, viewModel.state.value.source)

        val returnedSnapshots = mutableListOf<TestState>()
        backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect { returnedSnapshots += it } } }
        runCurrent()

        assertEquals(TestState(query = "changed while hidden", source = 3), returnedSnapshots.first())
        assertEquals(TestState(query = "changed while hidden", source = 70), returnedSnapshots.last())
        assertEquals(2, viewModel.starts)
    }

    @Test
    fun `a selector alone does not start source observation`() = runTest {
        val viewModel = viewModel()
        val queries = mutableListOf<String>()
        backgroundScope.launch { viewModel.queries().collect { queries += it } }
        runCurrent()

        viewModel.writeQuery("new query")
        runCurrent()

        assertEquals(listOf("", "new query"), queries)
        assertEquals(0, viewModel.starts)
    }

    @Test
    fun `plain state collection remains live without starting sources`() = runTest {
        val viewModel = viewModel()
        val snapshots = mutableListOf<TestState>()
        backgroundScope.launch { viewModel.state.collect { snapshots += it } }
        runCurrent()

        viewModel.writeQuery("read only")
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT * 2)
        runCurrent()

        assertEquals(listOf("", "read only"), snapshots.map { it.query })
        assertEquals(0, viewModel.starts)
    }

    @Test
    fun `binding without a state collector starts and releases sources`() = runTest {
        val viewModel = viewModel()
        val binding = backgroundScope.launch {
            viewModel.withUiBinding { awaitCancellation() }
        }
        runCurrent()
        assertEquals(1, viewModel.starts)

        binding.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()

        assertEquals(0, viewModel.activeGroups)
        assertEquals(1, viewModel.stops)
    }

    @Test
    fun `a remaining state collector cannot keep sources alive after UI unbinds`() = runTest {
        val viewModel = viewModel()
        val snapshots = mutableListOf<TestState>()
        val plainCollector = backgroundScope.launch {
            viewModel.state.collect { snapshots += it }
        }
        val binding = backgroundScope.launch {
            viewModel.withUiBinding { awaitCancellation() }
        }
        runCurrent()
        assertEquals(1, viewModel.activeGroups)

        binding.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        viewModel.writeQuery("still readable")
        runCurrent()

        assertTrue(plainCollector.isActive)
        assertEquals("still readable", snapshots.last().query)
        assertEquals(0, viewModel.activeGroups)
        assertEquals(1, viewModel.stops)
    }

    @Test
    fun `sources stop only after every independent binding releases`() = runTest {
        val viewModel = viewModel()
        val first = backgroundScope.launch { viewModel.withUiBinding { awaitCancellation() } }
        val second = backgroundScope.launch { viewModel.withUiBinding { awaitCancellation() } }
        runCurrent()

        first.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT * 2)
        runCurrent()
        assertEquals(1, viewModel.activeGroups)
        assertEquals(0, viewModel.stops)

        second.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertEquals(0, viewModel.activeGroups)
        assertEquals(1, viewModel.starts)
        assertEquals(1, viewModel.stops)
    }

    @Test
    fun `returning normally from binding releases its source ownership`() = runTest {
        val viewModel = viewModel()
        val binding = backgroundScope.launch {
            viewModel.withUiBinding { delay(100) }
        }
        runCurrent()
        assertEquals(1, viewModel.activeGroups)

        advanceTimeBy(100)
        runCurrent()
        assertTrue(binding.isCompleted)
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertEquals(0, viewModel.activeGroups)
        assertEquals(1, viewModel.stops)
    }

    @Test
    fun `binding exception propagates and releases ownership`() = runTest {
        val viewModel = viewModel()
        val expected = IllegalStateException("UI collector failed")
        var caught: Throwable? = null
        backgroundScope.launch {
            try {
                viewModel.withUiBinding {
                    delay(100)
                    throw expected
                }
            } catch (failure: IllegalStateException) {
                caught = failure
            }
        }
        runCurrent()
        assertEquals(1, viewModel.activeGroups)

        advanceTimeBy(100)
        runCurrent()
        assertEquals(expected.message, caught?.message)
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertEquals(0, viewModel.activeGroups)
        assertEquals(1, viewModel.stops)

        // A failed caller must not poison subsequent bindings or leak its count.
        val retry = backgroundScope.launch { viewModel.withUiBinding { awaitCancellation() } }
        runCurrent()
        assertEquals(2, viewModel.starts)
        retry.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertEquals(0, viewModel.activeGroups)
        assertEquals(2, viewModel.stops)
    }

    @Test
    fun `binding after owner clear does not run the caller block or start sources`() = runTest {
        val viewModel = viewModel()
        store.clear()
        runCurrent()
        var entered = false

        viewModel.withUiBinding { entered = true }
        runCurrent()

        assertFalse(entered)
        assertEquals(0, viewModel.starts)
        assertEquals(0, viewModel.activeGroups)
    }

    @Test
    fun `an internal selector cannot keep its own source group alive`() = runTest {
        var selectorFinished = false
        val viewModel = viewModel {
            try {
                queries().collect {}
            } finally {
                selectorFinished = true
            }
        }
        val collector = backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        collector.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()

        assertTrue(selectorFinished)
        assertEquals(0, viewModel.activeGroups)
        assertEquals(1, viewModel.stops)
    }

    @Test
    fun `unrelated output updates do not feed back into selected inputs`() = runTest {
        val observedQueries = mutableListOf<String>()
        val viewModel = viewModel {
            queries().collect {
                observedQueries += it
                writeSource(it.length)
            }
        }
        backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()

        viewModel.writeQuery("music")
        runCurrent()
        viewModel.writeUnrelated(42)
        runCurrent()
        viewModel.writeQuery("music")
        runCurrent()

        assertEquals(listOf("", "music"), observedQueries)
        assertEquals(5, viewModel.state.value.source)
        assertEquals(42, viewModel.state.value.unrelated)
    }

    @Test
    fun `retry after a handled source failure works without resubscribing the UI`() = runTest {
        val source = SnapshotSource().apply { fail = true }
        val viewModel = viewModel {
            try {
                writeSource(source.read())
                writeFailure(false)
            } catch (_: ExpectedSourceFailure) {
                writeFailure(true)
            }
        }
        backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        assertTrue(viewModel.state.value.failed)
        assertEquals(1, viewModel.starts)

        source.fail = false
        source.snapshot = 8
        viewModel.retry()
        runCurrent()

        assertFalse(viewModel.state.value.failed)
        assertEquals(8, viewModel.state.value.source)
        assertEquals(2, viewModel.starts)
    }

    @Test
    fun `restart also reruns a successfully completed source group`() = runTest {
        val source = SnapshotSource()
        val viewModel = viewModel { writeSource(source.read()) }
        backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        assertEquals(1, viewModel.stops)

        source.snapshot = 12
        viewModel.retry()
        runCurrent()

        assertEquals(12, viewModel.state.value.source)
        assertEquals(2, viewModel.starts)
        assertEquals(2, viewModel.stops)
    }

    @Test
    fun `restart waits for child cleanup before starting another source group`() = runTest {
        val events = mutableListOf<String>()
        val viewModel = viewModel {
            val generation = starts
            events += "start $generation"
            coroutineScope {
                launch {
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            delay(100)
                            events += "child cleaned $generation"
                        }
                    }
                }
                awaitCancellation()
            }
        }
        backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        viewModel.retry()
        runCurrent()

        assertEquals(listOf("start 1"), events)
        assertEquals(1, viewModel.starts)
        advanceTimeBy(100)
        runCurrent()

        assertEquals(listOf("start 1", "child cleaned 1", "start 2"), events)
        assertEquals(1, viewModel.maximumActiveGroups)

        store.clear()
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(0, viewModel.activeGroups)
    }

    @Test
    fun `retry while fully hidden does not start observers`() = runTest {
        val viewModel = viewModel()
        viewModel.retry()
        runCurrent()
        assertEquals(0, viewModel.starts)

        val collector = backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        collector.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()

        viewModel.retry()
        viewModel.retry()
        runCurrent()
        assertEquals(1, viewModel.starts)
        assertEquals(0, viewModel.activeGroups)

        backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        assertEquals(2, viewModel.starts)
    }

    @Test
    fun `clearing the owner cancels every observer child without waiting for the timeout`() = runTest {
        var activeChildren = 0
        val viewModel = viewModel {
            coroutineScope {
                repeat(2) {
                    launch {
                        activeChildren++
                        try {
                            awaitCancellation()
                        } finally {
                            activeChildren--
                        }
                    }
                }
                awaitCancellation()
            }
        }
        backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        assertEquals(2, activeChildren)

        store.clear()
        runCurrent()

        assertEquals(0, activeChildren)
        assertEquals(0, viewModel.activeGroups)
        assertEquals(1, viewModel.stops)
    }

    private fun viewModel(
        observe: suspend ProbeViewModel.() -> Unit = { awaitCancellation() },
    ): ProbeViewModel = ProbeViewModel(observe).also { store.put("test", it) }

    private data class TestState(
        val query: String = "",
        val source: Int = 0,
        val unrelated: Int = 0,
        val failed: Boolean = false,
    ) : UiState

    private data object UnusedIntent : UiIntent

    private class ProbeViewModel(
        private val observe: suspend ProbeViewModel.() -> Unit,
    ) : MviTestViewModel<TestState, UnusedIntent, Nothing>(
        initialState = TestState(),
        sourceStopTimeoutMillis = STOP_TIMEOUT,
    ) {
        var initialized = false
            private set
        var starts = 0
            private set
        var stops = 0
            private set
        var activeGroups = 0
            private set
        var maximumActiveGroups = 0
            private set

        init {
            initialized = true
        }

        override fun onIntent(intent: UnusedIntent) = Unit

        override suspend fun observeSources() {
            check(initialized) { "Subclass initialization has not finished" }
            starts++
            activeGroups++
            maximumActiveGroups = maxOf(maximumActiveGroups, activeGroups)
            try {
                observe()
            } finally {
                activeGroups--
                stops++
            }
        }

        fun writeQuery(value: String) = setState { copy(query = value) }

        fun writeSource(value: Int) = setState { copy(source = value) }

        fun writeUnrelated(value: Int) = setState { copy(unrelated = value) }

        fun writeFailure(value: Boolean) = setState { copy(failed = value) }

        fun queries(): Flow<String> = selectState { it.query }

        fun retry() = restartSources()
    }

    private class ExpectedSourceFailure : Exception()

    private class SnapshotSource {
        var fail = false
        var snapshot = 1

        fun read(): Int {
            if (fail) throw ExpectedSourceFailure()
            return snapshot
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 1_000L
    }
}
