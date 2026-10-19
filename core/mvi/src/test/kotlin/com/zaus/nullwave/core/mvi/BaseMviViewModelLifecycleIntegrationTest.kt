package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseMviViewModelLifecycleIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val cleanupGates = mutableListOf<CompletableDeferred<Unit>>()
    private lateinit var store: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = ViewModelStore()
    }

    @After
    fun tearDown() {
        store.clear()
        cleanupGates.forEach { it.complete(Unit) }
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `UI returning during stopped source cleanup waits and receives a fresh snapshot`() = runTest {
        val cleanup = CompletableDeferred<Unit>().also { cleanupGates += it }
        val source = MutableStateFlow(10)
        val events = mutableListOf<String>()
        val viewModel = viewModel {
            val generation = starts
            events += "start $generation"
            try {
                source.collect { writeSource(it) }
            } finally {
                if (generation == 1) {
                    withContext(NonCancellable) {
                        events += "cleanup started $generation"
                        cleanup.await()
                        events += "cleanup finished $generation"
                    }
                }
            }
        }
        val firstUi = backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        assertEquals(10, viewModel.state.value.source)

        firstUi.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertEquals(listOf("start 1", "cleanup started 1"), events)

        source.value = 20
        val returnedSnapshots = mutableListOf<TestState>()
        backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect { returnedSnapshots += it } } }
        runCurrent()

        assertEquals(1, viewModel.starts)
        assertEquals(1, viewModel.activeGroups)
        assertEquals(10, returnedSnapshots.single().source)

        cleanup.complete(Unit)
        runCurrent()

        assertEquals(
            listOf("start 1", "cleanup started 1", "cleanup finished 1", "start 2"),
            events,
        )
        assertEquals(2, viewModel.starts)
        assertEquals(1, viewModel.maximumActiveGroups)
        assertEquals(1, viewModel.activeGroups)
        assertEquals(20, returnedSnapshots.last().source)

        source.value = 30
        runCurrent()
        assertEquals(30, returnedSnapshots.last().source)

        store.clear()
        runCurrent()
        assertEquals(0, viewModel.activeGroups)
        assertEquals(2, viewModel.stops)
    }

    @Test
    fun `slow selector receives latest rapid update without feedback or retaining sources`() = runTest {
        val allowNextInput = CompletableDeferred<Unit>()
        val selectedQueries = mutableListOf<String>()
        var selectorFinished = false
        val viewModel = viewModel {
            try {
                queries().collect { query ->
                    selectedQueries += query
                    writeSource(query.length)
                    if (selectedQueries.size == 1) allowNextInput.await()
                }
            } finally {
                selectorFinished = true
            }
        }

        viewModel.writeQuery("first")
        viewModel.writeQuery("before collection")
        val ui = backgroundScope.launch { viewModel.withUiBinding { viewModel.state.collect {} } }
        runCurrent()
        assertEquals(listOf("before collection"), selectedQueries)

        viewModel.writeQuery("intermediate")
        viewModel.writeQuery("latest")
        viewModel.writeSource(-1)
        viewModel.writeQuery("latest")
        runCurrent()
        assertEquals("latest", viewModel.state.value.query)
        assertEquals(listOf("before collection"), selectedQueries)

        allowNextInput.complete(Unit)
        runCurrent()

        assertEquals(listOf("before collection", "latest"), selectedQueries)
        assertEquals("latest".length, viewModel.state.value.source)
        assertEquals(1, viewModel.starts)

        ui.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()

        assertTrue(selectorFinished)
        assertEquals(0, viewModel.activeGroups)
        assertEquals(1, viewModel.stops)

        viewModel.writeQuery("while hidden")
        runCurrent()
        assertEquals(listOf("before collection", "latest"), selectedQueries)
        assertEquals(1, viewModel.starts)
    }

    private fun viewModel(observe: suspend ProbeViewModel.() -> Unit): ProbeViewModel =
        ProbeViewModel(observe).also { store.put("integration", it) }

    private data class TestState(val query: String = "", val source: Int = 0) : UiState

    private data object UnusedIntent : UiIntent

    private class ProbeViewModel(
        private val observe: suspend ProbeViewModel.() -> Unit,
    ) : MviTestViewModel<TestState, UnusedIntent, Nothing>(
        initialState = TestState(),
        sourceStopTimeoutMillis = STOP_TIMEOUT,
    ) {
        var starts = 0
            private set
        var stops = 0
            private set
        var activeGroups = 0
            private set
        var maximumActiveGroups = 0
            private set

        override fun onIntent(intent: UnusedIntent) = Unit

        override suspend fun observeSources() {
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

        fun queries(): Flow<String> = selectState { it.query }
    }

    private companion object {
        const val STOP_TIMEOUT = 1_000L
    }
}
