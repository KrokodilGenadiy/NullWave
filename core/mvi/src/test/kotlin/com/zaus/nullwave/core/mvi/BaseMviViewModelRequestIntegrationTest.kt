package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseMviViewModelRequestIntegrationTest {
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
    fun `old finally running during replacement cannot clear the new loading state`() = runTest(dispatcher) {
        // Cancellation on Main.immediate can resume cleanup before submit returns.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = SuspendingRepository()
        val vm = own(SearchViewModel(repository))
        vm.onIntent(SearchIntent.Search("old"))
        assertTrue(repository.requests.containsKey("old"))

        vm.onIntent(SearchIntent.Search("new"))

        val oldCleanup = vm.cleanups.single()
        assertEquals("old", oldCleanup.query)
        assertFalse(oldCleanup.applied)
        assertEquals(SearchState(query = "new", loading = true), oldCleanup.state)
        assertTrue(vm.events.indexOf("cleanup:old") < vm.events.indexOf("launch-returned:new"))
        assertTrue(repository.requests.containsKey("new"))
        assertTrue(vm.snapshot.value.state.loading)

        repository.requests.getValue("new").complete("new result")
        assertEquals(SearchState(query = "new", result = "new result"), vm.snapshot.value.state)
        assertEquals(listOf(ResultAttempt("new", applied = true)), vm.results)
    }

    @Test
    fun `cancelled callback request cannot publish a late result or clear new loading`() = runTest(dispatcher) {
        val repository = CallbackRepository()
        val vm = own(SearchViewModel(repository))
        vm.onIntent(SearchIntent.Search("old"))
        runCurrent()
        vm.onIntent(SearchIntent.Search("new"))
        runCurrent()
        assertEquals(listOf("old"), repository.started)

        // This callback API intentionally ignores Job cancellation and still resumes its caller.
        // Latest must wait for that old command to finish before starting the replacement.
        repository.complete("old", "obsolete result")
        runCurrent()

        assertEquals(listOf("old", "new"), repository.started)
        assertEquals(listOf(ResultAttempt("old", applied = false)), vm.results)
        assertEquals(SearchState(query = "new", loading = true), vm.snapshot.value.state)
        assertEquals(listOf(Cleanup("old", applied = false, vm.snapshot.value.state)), vm.cleanups)

        repository.complete("new", "current result")
        runCurrent()

        assertEquals(SearchState(query = "new", result = "current result"), vm.snapshot.value.state)
        assertEquals(
            listOf(ResultAttempt("old", applied = false), ResultAttempt("new", applied = true)),
            vm.results,
        )
        assertTrue(vm.cleanups.last().applied)
    }

    @Test
    fun `explicit cancellation rejects a callback result even without a replacement request`() = runTest(dispatcher) {
        val repository = CallbackRepository()
        val vm = own(SearchViewModel(repository))
        vm.onIntent(SearchIntent.Search("cancelled"))
        runCurrent()
        vm.onIntent(SearchIntent.Cancel)
        val cancelledState = vm.snapshot.value.state
        assertFalse(cancelledState.loading)

        repository.complete("cancelled", "late result")
        runCurrent()

        assertEquals(listOf(ResultAttempt("cancelled", applied = false)), vm.results)
        assertEquals(listOf(Cleanup("cancelled", applied = false, cancelledState)), vm.cleanups)
        assertEquals(cancelledState, vm.snapshot.value.state)
    }

    @Test
    fun `clearing the store rejects a callback that ignores ViewModel cancellation`() = runTest(dispatcher) {
        val repository = CallbackRepository()
        val vm = own(SearchViewModel(repository))
        vm.onIntent(SearchIntent.Search("cleared"))
        runCurrent()
        store.clear()
        val stateAtClear = vm.snapshot.value.state

        repository.complete("cleared", "late result")
        runCurrent()

        assertEquals(listOf(ResultAttempt("cleared", applied = false)), vm.results)
        assertEquals(listOf(Cleanup("cleared", applied = false, stateAtClear)), vm.cleanups)
        assertEquals(stateAtClear, vm.snapshot.value.state)

        vm.onIntent(SearchIntent.Search("after clear"))
        runCurrent()
        assertEquals(listOf("cleared" to true, "after clear" to false), vm.admissions)
        assertEquals(listOf("cleared"), repository.started)
        assertEquals(stateAtClear, vm.snapshot.value.state)
    }

    @Test
    fun `reentrant search remains cancellable through the stable operation property`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = CallbackRepository()
        val vm = own(SearchViewModel(repository))
        var nestedSubmitted = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.snapshot.map { it.state }.distinctUntilChanged().collect { state ->
                if (state.query == "outer" && !nestedSubmitted) {
                    nestedSubmitted = true
                    vm.onIntent(SearchIntent.Search("nested"))
                }
            }
        }

        vm.onIntent(SearchIntent.Search("outer"))
        assertTrue(nestedSubmitted)
        // Both were accepted; the nested intent superseded the outer request before it ran.
        assertEquals(listOf("nested" to true, "outer" to true), vm.admissions)
        assertEquals(listOf("nested"), repository.started)
        assertEquals(SearchState(query = "nested", loading = true), vm.snapshot.value.state)

        vm.onIntent(SearchIntent.Cancel)
        repository.complete("nested", "late nested result")

        assertEquals(listOf(ResultAttempt("nested", applied = false)), vm.results)
        assertEquals(SearchState(query = "nested"), vm.snapshot.value.state)
    }

    @Test
    fun `cancel intent during acceptance stops the request before submit returns`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = CallbackRepository()
        val vm = own(SearchViewModel(repository))
        var cancelled = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.snapshot.map { it.state }.distinctUntilChanged().collect { state ->
                if (state.loading && !cancelled) {
                    cancelled = true
                    vm.onIntent(SearchIntent.Cancel)
                }
            }
        }

        vm.onIntent(SearchIntent.Search("cancel before return"))
        runCurrent()

        assertTrue(cancelled)
        assertEquals(listOf("cancel before return" to true), vm.admissions)
        assertEquals(SearchState(query = "cancel before return"), vm.snapshot.value.state)
        assertTrue(repository.started.isEmpty())
        assertTrue(vm.results.isEmpty())
    }

    private fun own(vm: SearchViewModel): SearchViewModel = vm.also { store.put("search", it) }

    private data class SearchState(
        val query: String = "",
        val loading: Boolean = false,
        val result: String? = null,
    ) : UiState

    private sealed interface SearchIntent : UiIntent {
        data class Search(val query: String) : SearchIntent
        data object Cancel : SearchIntent
    }

    private data class ResultAttempt(val query: String, val applied: Boolean)

    private data class Cleanup(val query: String, val applied: Boolean, val state: SearchState)

    private interface SearchRepository {
        suspend fun search(query: String): String
    }

    private class SuspendingRepository : SearchRepository {
        val requests = mutableMapOf<String, CompletableDeferred<String>>()

        override suspend fun search(query: String): String =
            CompletableDeferred<String>().also { requests[query] = it }.await()
    }

    private class CallbackRepository : SearchRepository {
        private val callbacks = mutableMapOf<String, Continuation<String>>()
        val started = mutableListOf<String>()

        override suspend fun search(query: String): String = suspendCoroutine { continuation ->
            started += query
            callbacks[query] = continuation
        }

        fun complete(query: String, result: String) {
            checkNotNull(callbacks.remove(query)).resume(result)
        }
    }

    /** Exercises the whole intent -> suspended command -> result/finally path without UI collectors. */
    private class SearchViewModel(
        private val repository: SearchRepository,
    ) : MviTestViewModel<SearchState, SearchIntent, Nothing>(SearchState()) {
        private val search = latestOperation()
        val results = mutableListOf<ResultAttempt>()
        val cleanups = mutableListOf<Cleanup>()
        val events = mutableListOf<String>()
        val admissions = mutableListOf<Pair<String, Boolean>>()

        override fun onIntent(intent: SearchIntent) {
            when (intent) {
                is SearchIntent.Search -> {
                    val accepted = search.submit(
                        onAccepted = { copy(query = intent.query, loading = true, result = null) },
                    ) {
                        try {
                            val result = repository.search(intent.query)
                            val applied = commitState { copy(result = result) }
                            results += ResultAttempt(intent.query, applied)
                        } finally {
                            val applied = commitState { copy(loading = false) }
                            cleanups += Cleanup(intent.query, applied, currentState)
                            events += "cleanup:${intent.query}"
                        }
                    }
                    admissions += intent.query to accepted
                    events += "launch-returned:${intent.query}"
                }
                SearchIntent.Cancel -> {
                    search.cancel { copy(loading = false) }
                }
            }
        }
    }
}
