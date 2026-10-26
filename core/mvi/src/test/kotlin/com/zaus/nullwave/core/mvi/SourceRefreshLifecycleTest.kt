package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Source restart guarantees exercised through the same public DSL and UI binding as features. */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceRefreshLifecycleTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(AndroidLikeTestMainDispatcher(dispatcher))
    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `retry after a handled source failure succeeds without replacing the UI binding`() = runTest(dispatcher) {
        var reads = 0
        val vm = own {
            val catalog = source("catalog", flow = {
                flow {
                    reads++
                    // Expected failures are values; the stream completes normally after reporting one.
                    emit(if (reads == 1) Result.failure<Int>(ReadFailure()) else Result.success(8))
                }
            }) { copy(value = it.getOrDefault(0), failed = it.isFailure) }
            on<Refresh> { refresh(catalog) }
        }
        val binding = backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        assertTrue(vm.state.value.failed)
        assertEquals(1, reads)

        vm.ui.onIntent(Refresh)
        runCurrent()

        assertFalse(vm.state.value.failed)
        assertEquals(8, vm.state.value.value)
        assertEquals(2, reads)
        assertTrue(binding.isActive)
    }

    @Test
    fun `refresh waits for structured child cleanup without restarting a sibling source`() = runTest(dispatcher) {
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var reads = 0
        var activeChildren = 0
        var siblingStarts = 0
        val vm = own {
            val catalog = source("catalog", flow = {
                flow<Unit> {
                    val read = ++reads
                    events += "start $read"
                    coroutineScope {
                        launch {
                            activeChildren++
                            try { awaitCancellation() } finally {
                                withContext(NonCancellable) {
                                    cleanup.await()
                                    activeChildren--
                                    events += "child cleaned $read"
                                }
                            }
                        }
                        awaitCancellation()
                    }
                }
            }) { this }
            source("sibling", flow = { flow<Unit> { siblingStarts++; awaitCancellation() } }) { this }
            on<Refresh> { refresh(catalog) }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        try {
            vm.ui.onIntent(Refresh)
            runCurrent()
            assertEquals(listOf("start 1"), events)
            assertEquals(1, activeChildren)

            cleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf("start 1", "child cleaned 1", "start 2"), events)
            assertEquals(1, activeChildren)
            assertEquals(1, siblingStarts)

            store.clear()
            runCurrent()
            assertEquals(0, activeChildren)
        } finally { cleanup.complete(Unit) }
    }

    @Test
    fun `refresh during stop grace rereads but after timeout waits for another binding`() = runTest(dispatcher) {
        var reads = 0
        val vm = own {
            val catalog = source("catalog", flow = { flow { emit(++reads) } }) { copy(value = it) }
            on<Refresh> { refresh(catalog) }
        }
        val first = backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        first.cancel()
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT - 1)
        vm.ui.onIntent(Refresh)
        runCurrent()
        assertEquals(2, reads)

        advanceTimeBy(1)
        runCurrent()
        repeat(3) { vm.ui.onIntent(Refresh) }
        runCurrent()
        assertEquals(2, reads)

        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        assertEquals(3, reads)
        assertEquals(3, vm.state.value.value)
    }

    @Test
    fun `rebind with equal input waits for stopped collection and rejects its late output before reducer`() = runTest(dispatcher) {
        val responses = mutableListOf<CompletableDeferred<Int>>()
        val reduced = mutableListOf<Int>()
        val vm = own {
            source("catalog", flow = { callbackRead(responses) }) {
                reduced += it
                copy(value = it)
            }
        }
        val first = backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        try {
            first.cancel()
            runCurrent()
            advanceTimeBy(STOP_TIMEOUT)
            runCurrent()
            backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
            runCurrent()
            assertEquals(1, responses.size)

            // Same input and revision: cancelled group/request ownership must reject this callback.
            responses[0].complete(99)
            runCurrent()
            assertTrue(reduced.isEmpty())
            assertEquals(0, vm.state.value.value)
            assertEquals(2, responses.size)

            responses[1].complete(7)
            runCurrent()
            assertEquals(listOf(7), reduced)
            assertEquals(7, vm.state.value.value)
        } finally { responses.toList().forEach { it.complete(-1) } }
    }

    @Test
    fun `clear while refresh awaits cleanup rejects late output and never starts replacement`() = runTest(dispatcher) {
        val responses = mutableListOf<CompletableDeferred<Int>>()
        val reduced = mutableListOf<Int>()
        val vm = own {
            val catalog = source("catalog", flow = { callbackRead(responses) }) {
                reduced += it
                copy(value = it)
            }
            on<Refresh> { refresh(catalog) }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        try {
            vm.ui.onIntent(Refresh)
            runCurrent()
            store.clear()
            responses.single().complete(99)
            runCurrent()

            assertEquals(1, responses.size)
            assertTrue(reduced.isEmpty())
            assertEquals(0, vm.state.value.value)
            vm.ui.onIntent(Refresh)
            runCurrent()
            assertEquals(1, responses.size)
        } finally { responses.toList().forEach { it.complete(-1) } }
    }

    /** A callback already in progress can finish despite cancellation; the runtime must discard it. */
    private fun callbackRead(responses: MutableList<CompletableDeferred<Int>>): Flow<Int> =
        object : Flow<Int> {
            override suspend fun collect(collector: FlowCollector<Int>) {
                val response = CompletableDeferred<Int>().also(responses::add)
                withContext(NonCancellable) { collector.emit(response.await()) }
            }
        }

    private suspend fun own(definition: MviDefinition<Snapshot, Refresh, Nothing>.() -> Unit): ProbeVm =
        ProbeVm(definition).also { store.put("screen", it); it.ui = it.uiApi() }

    private data class Snapshot(val value: Int = 0, val failed: Boolean = false) : UiState
    private data object Refresh : UiIntent
    private class ReadFailure : Exception()
    private class ProbeVm(private val definition: MviDefinition<Snapshot, Refresh, Nothing>.() -> Unit) :
        BaseMviViewModel<Snapshot, Refresh, Nothing>(Snapshot(), sourceStopTimeoutMillis = STOP_TIMEOUT) {
        lateinit var ui: MviUiApi<Snapshot, Refresh>
        override fun MviDefinition<Snapshot, Refresh, Nothing>.define() = definition()
    }

    private companion object { const val STOP_TIMEOUT = 1_000L }
}
