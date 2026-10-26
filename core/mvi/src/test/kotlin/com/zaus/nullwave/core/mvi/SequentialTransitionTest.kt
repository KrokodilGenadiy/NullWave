package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.runtime.MainIntentIngress
import com.zaus.nullwave.core.mvi.runtime.MviRuntime
import com.zaus.nullwave.core.mvi.runtime.source.PreparedSourceChanges
import com.zaus.nullwave.core.mvi.ui.MviUiApi
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SequentialTransitionTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(ImmediateTestMainDispatcher(dispatcher))
    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `observer intents wait for the whole handler including work after reduce`() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        val saved = mutableListOf<Int>()
        val vm = ProbeVm {
            on<Write> { intent ->
                events += "begin ${intent.value}"
                reduce { copy(value = intent.value) }
                saved += state.value
                events += "end ${intent.value}"
            }
        }.also { store.put("vm", it); it.ui = it.uiApi() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.snapshot.map { it.state }.distinctUntilChanged().collect {
                if (it.value == 1) {
                    vm.ui.onIntent(Write(2))
                    vm.ui.onIntent(Write(3))
                    events += "observer returned"
                    assertEquals(1, vm.snapshot.value.state.value)
                }
            }
        }

        vm.ui.onIntent(Write(1))

        assertEquals(listOf("begin 1", "observer returned", "end 1", "begin 2", "end 2", "begin 3", "end 3"), events)
        assertEquals(listOf(1, 2, 3), saved)
        assertEquals(3, vm.snapshot.value.state.value)
    }

    @Test
    fun `publication outside an intent finishes source notifications before nested dispatch`() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        val runtime = MviRuntime<Snapshot, Nothing>(Snapshot(), backgroundScope, observeSources = {})
        runtime.prepareStateChange = { _, _, _ ->
            PreparedSourceChanges(commit = {}, notifyCollectors = { events += "notified" })
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            runtime.snapshot.map { it.state }.distinctUntilChanged().collect {
                if (it.value == 1) {
                    events += "observed"
                    runtime.dispatch { events += "intent" }
                }
            }
        }
        try {
            runtime.updateFromSource("catalog") { copy(value = 1) }
            assertEquals(listOf("observed", "notified", "intent"), events)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `queued callbacks cannot recurse even when every callback submits another`() = runTest(dispatcher) {
        val ingress = MainIntentIngress(backgroundScope)
        var depth = 0
        var maximumDepth = 0
        var handled = 0
        fun submitNext() {
            ingress.dispatch {
                depth++
                maximumDepth = maxOf(maximumDepth, depth)
                handled++
                if (handled < 10_000) submitNext()
                depth--
            }
        }
        try {
            submitNext()
            assertEquals(10_000, handled)
            assertEquals(1, maximumDepth)
        } finally {
            ingress.close()
        }
    }

    @Test
    fun `throwing inline handler releases queued work and preserves its exception`() = runTest(dispatcher) {
        val ingress = MainIntentIngress(backgroundScope)
        val failure = IllegalArgumentException("handler")
        val events = mutableListOf<Int>()
        try {
            assertSame(failure, assertThrows(IllegalArgumentException::class.java) {
                ingress.dispatch {
                    ingress.dispatch { events += 2 }
                    events += 1
                    throw failure
                }
            })
            runCurrent()
            ingress.dispatch { events += 3 }
            assertEquals(listOf(1, 2, 3), events)
        } finally {
            ingress.close()
        }
    }

    @Test
    fun `clear after publication drops observer intents waiting for the handler to finish`() = runTest(dispatcher) {
        var nestedRuns = 0
        val vm = ProbeVm {
            on<Write> {
                if (it.value == 1) {
                    reduce { copy(value = 1) }
                    store.clear()
                } else nestedRuns++
            }
        }.also { store.put("vm", it); it.ui = it.uiApi() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.snapshot.map { it.state }.distinctUntilChanged().collect { if (it.value == 1) vm.ui.onIntent(Write(2)) }
        }

        vm.ui.onIntent(Write(1))
        runCurrent()

        assertEquals(0, nestedRuns)
        assertEquals(1, vm.snapshot.value.state.value)
    }

    private data class Snapshot(val value: Int = 0) : UiState
    private data class Write(val value: Int) : UiIntent
    private class ProbeVm(private val definition: MviDefinition<Snapshot, Write, Nothing>.() -> Unit) :
        BaseMviViewModel<Snapshot, Write, Nothing>(Snapshot()) {
        lateinit var ui: MviUiApi<Snapshot, Write>

        override fun MviDefinition<Snapshot, Write, Nothing>.define() = definition()
    }
}
