package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.IntentScope
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import com.zaus.nullwave.core.mvi.dsl.Source
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourceRefreshTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextKey = 0

    @Before fun setUp() = Dispatchers.setMain(ImmediateTestMainDispatcher(dispatcher))
    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `input edits restart automatically and explicit refresh rereads an equal input`() = runTest(dispatcher) {
        val reads = mutableListOf<String>()
        val vm = own {
            val catalog = source("catalog", input = { query }, flow = {
                reads += it
                flowOf(reads.size)
            }) { _, value -> copy(result = value) }
            on<Query> { reduce { copy(query = it.value) } }
            on<Unrelated> { reduce { copy(other = other + 1) } }
            on<Refresh> { refresh(catalog) }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()

        vm.ui.onIntent(Unrelated)
        vm.ui.onIntent(Query("A"))
        runCurrent()
        assertEquals(listOf("A"), reads)
        vm.ui.onIntent(Query("B"))
        runCurrent()
        assertEquals(listOf("A", "B"), reads)
        vm.ui.onIntent(Refresh)
        runCurrent()
        assertEquals(listOf("A", "B", "B"), reads)
        assertEquals(3, vm.state.value.result)
    }

    @Test
    fun `refresh with a changed input collects once and cannot overwrite immediate Ready with Loading`() = runTest(dispatcher) {
        val reads = mutableListOf<String>()
        var siblingReads = 0
        val vm = own {
            val catalog = source("catalog", input = { query }, flow = {
                reads += it
                flowOf(reads.size)
            }) { _, value -> copy(result = value, loading = false) }
            source("sibling", flow = { siblingReads++; flowOf(Unit) }) { this }
            on<Query> { refresh(catalog) { copy(query = it.value, loading = true) } }
            on<Refresh> { refresh(catalog) { copy(loading = true) } }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()

        vm.ui.onIntent(Query("B"))
        runCurrent()
        assertEquals(listOf("A", "B"), reads)
        assertEquals(2, vm.state.value.result)
        assertFalse(vm.state.value.loading)
        vm.ui.onIntent(Refresh)
        runCurrent()
        assertEquals(listOf("A", "B", "B"), reads)
        assertEquals(3, vm.state.value.result)
        assertFalse(vm.state.value.loading)
        assertEquals(1, siblingReads)
    }

    @Test
    fun `repeated refresh revokes late output waits for cleanup and skips superseded requests`() = runTest(dispatcher) {
        val requests = mutableListOf<CompletableDeferred<Int>>()
        val vm = own {
            val catalog = source("catalog", flow = {
                val response = CompletableDeferred<Int>().also(requests::add)
                object : Flow<Int> {
                    override suspend fun collect(collector: FlowCollector<Int>) {
                        // Models a callback adapter which cannot retract an already-running read.
                        withContext(NonCancellable) { collector.emit(response.await()) }
                    }
                }
            }) { copy(result = it, loading = false) }
            on<Refresh> { refresh(catalog) { copy(loading = true) } }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        try {
            repeat(3) {
                vm.ui.onIntent(Refresh)
                runCurrent()
            }
            assertEquals(1, requests.size)
            requests[0].complete(42)
            runCurrent()
            assertEquals(0, vm.state.value.result)
            assertEquals(2, requests.size)
            requests[1].complete(7)
            runCurrent()
            assertEquals(7, vm.state.value.result)
            assertFalse(vm.state.value.loading)
        } finally {
            requests.toList().forEach { it.complete(-1) }
        }
    }

    @Test
    fun `refresh while stopped stores the latest request until the next UI binding`() = runTest(dispatcher) {
        val reads = mutableListOf<String>()
        val vm = own {
            val catalog = source("catalog", input = { query }, flow = {
                reads += it
                flowOf(reads.size)
            }) { _, value -> copy(result = value) }
            on<Query> { refresh(catalog) { copy(query = it.value) } }
        }
        vm.ui.onIntent(Query("B"))
        runCurrent()
        assertEquals(emptyList<String>(), reads)
        val binding = backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        assertEquals(listOf("B"), reads)
        binding.cancel()
        runCurrent()

        vm.ui.onIntent(Query("C"))
        vm.ui.onIntent(Query("D"))
        runCurrent()
        assertEquals(listOf("B"), reads)
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        assertEquals(listOf("B", "D"), reads)
    }

    @Test
    fun `failed validation or selector leaves State and the existing subscription intact`() = runTest(dispatcher) {
        val output = MutableSharedFlow<Int>()
        var reads = 0
        val failure = IllegalArgumentException("invalid input")
        val vm = own {
            validateState { if (it.other < 0) throw failure }
            val catalog = source("catalog", flow = { reads++; output }) { copy(result = it) }
            // Deliberately declared after catalog: its failure must not partly refresh catalog.
            source("validated", input = {
                if (query == "invalid") throw failure
                query
            }, flow = { flowOf(Unit) }) { _, _ -> this }
            on<Query> { refresh(catalog) { copy(query = it.value) } }
            on<Unrelated> { refresh(catalog) { copy(other = -1) } }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()

        assertSame(failure, assertThrows(IllegalArgumentException::class.java) { vm.ui.onIntent(Query("invalid")) })
        assertSame(failure, assertThrows(IllegalArgumentException::class.java) { vm.ui.onIntent(Unrelated) })
        runCurrent()
        output.emit(7)
        runCurrent()
        assertEquals(1, reads)
        assertEquals(Snapshot(result = 7), vm.state.value)
    }

    @Test
    fun `source handle belongs to its VM and a retained intent receiver cannot refresh it`() = runTest(dispatcher) {
        lateinit var source: Source<Snapshot>
        lateinit var escaped: IntentScope<Snapshot, Nothing>
        var reads = 0
        val owner = own {
            source = source("catalog", flow = { reads++; flowOf(Unit) }) { this }
            on<Refresh> { escaped = this }
        }
        val other = own { on<Refresh> { refresh(source) } }
        backgroundScope.launch { owner.withUiBinding { awaitCancellation() } }
        runCurrent()
        owner.ui.onIntent(Refresh)

        assertThrows(IllegalStateException::class.java) { other.ui.onIntent(Refresh) }
        assertThrows(IllegalStateException::class.java) { escaped.refresh(source) }
        runCurrent()
        assertEquals(1, reads)
    }

    @Test
    fun `refresh cannot be called from its own reducer and after clear intents do nothing`() = runTest(dispatcher) {
        var reducerCalls = 0
        val vm = own {
            val catalog = source("catalog", flow = { flowOf(Unit) }) { this }
            on<Refresh> {
                refresh(catalog) {
                    reducerCalls++
                    refresh(catalog)
                    this
                }
            }
        }
        assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Refresh) }
        assertEquals(Snapshot(), vm.state.value)
        store.clear()
        vm.ui.onIntent(Refresh)
        runCurrent()
        assertEquals(1, reducerCalls)
    }

    private suspend fun own(definition: MviDefinition<Snapshot, Intent, Nothing>.() -> Unit): ProbeVm =
        ProbeVm(definition).also { store.put("source-${nextKey++}", it); it.ui = it.uiApi() }

    private data class Snapshot(
        val query: String = "A",
        val result: Int = 0,
        val loading: Boolean = false,
        val other: Int = 0,
    ) : UiState

    private sealed interface Intent : UiIntent
    private data class Query(val value: String) : Intent
    private data object Refresh : Intent
    private data object Unrelated : Intent

    private class ProbeVm(
        private val definition: MviDefinition<Snapshot, Intent, Nothing>.() -> Unit,
    ) : BaseMviViewModel<Snapshot, Intent, Nothing>(Snapshot(), sourceStopTimeoutMillis = 0) {
        lateinit var ui: MviUiApi<Snapshot, Intent>

        override fun MviDefinition<Snapshot, Intent, Nothing>.define() = definition()
    }
}
