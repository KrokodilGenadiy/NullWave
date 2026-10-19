package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Integrates intents, internal selectors, source switching and the final state reducer. */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseMviViewModelQueryIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

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
    fun `queued result from A cannot complete B before the source switch is processed`() = runTest {
        val source = ControlledSource()
        val vm = QueryViewModel(source).also { store.put("query", it) }
        val observed = mutableListOf<QueryState>()
        // Eager collection records even a briefly incorrect result, not only the final snapshot.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.withUiBinding { vm.state.collect { observed += it } }
        }
        runCurrent()
        val requestA = source.requests.single()

        requestA.results.send("result A")
        vm.onIntent(ChangeQuery("B"))
        runCurrent()

        assertEquals(listOf("A", "B"), source.requests.map { it.input.text })
        assertTrue(vm.state.value.loading)
        assertNull(vm.state.value.result)
        assertFalse(observed.any { it.input.text == "B" && it.result == "result A" })

        source.requests.last().results.send("result B")
        runCurrent()
        assertEquals("result B", vm.state.value.result)
        assertFalse(vm.state.value.loading)
        // Proves the regression actually exercised delivery across the input change.
        assertTrue(vm.delivered.any { it.first == requestA.input && it.second.text == "B" })
    }

    @Test
    fun `rapid A B A inputs still start a new generation and reject the first A result`() = runTest {
        val source = ControlledSource()
        val vm = QueryViewModel(source).also { store.put("query", it) }
        val observed = mutableListOf<QueryState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.withUiBinding { vm.state.collect { observed += it } }
        }
        runCurrent()
        val firstA = source.requests.single()

        firstA.results.send("first A result")
        vm.onIntent(ChangeQuery("B"))
        vm.onIntent(ChangeQuery("A"))
        runCurrent()

        assertEquals(listOf("A", "A"), source.requests.map { it.input.text })
        assertEquals(2, source.requests.last().input.revision)
        assertTrue(vm.state.value.loading)
        assertFalse(observed.any { it.input.revision == 2 && it.result == "first A result" })

        source.requests.last().results.send("second A result")
        runCurrent()
        assertEquals("second A result", vm.state.value.result)
        assertFalse(vm.state.value.loading)
    }

    private data class QueryInput(val text: String, val revision: Int)

    private data class QueryState(
        val input: QueryInput = QueryInput("A", 0),
        val result: String? = null,
        val loading: Boolean = true,
    ) : UiState

    private data class ChangeQuery(val text: String) : UiIntent

    private class QueryViewModel(
        private val source: ControlledSource,
    ) : MviTestViewModel<QueryState, ChangeQuery, Nothing>(QueryState()) {
        val delivered = mutableListOf<Pair<QueryInput, QueryInput>>()

        override fun onIntent(intent: ChangeQuery) {
            setState {
                copy(input = QueryInput(intent.text, input.revision + 1), result = null, loading = true)
            }
        }

        override suspend fun observeSources() {
            selectState { it.input }
                .flatMapLatest { input -> source.observe(input).map { input to it } }
                .collect { (input, result) ->
                    delivered += input to currentState.input
                    // Cancellation limits work; the input key decides whether a result is current.
                    setState {
                        if (this.input == input) copy(result = result, loading = false) else this
                    }
                }
        }
    }

    private class ControlledSource {
        class Request(val input: QueryInput) {
            val results = Channel<String>(Channel.UNLIMITED)
        }

        val requests = mutableListOf<Request>()

        fun observe(input: QueryInput): Flow<String> = Request(input).also(requests::add)
            .results.receiveAsFlow()
    }
}
