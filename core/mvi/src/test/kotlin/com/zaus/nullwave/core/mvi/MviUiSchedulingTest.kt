package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Models a platform coroutine awaiting a source, clearing its VM, then entering another VM's UI. */
@OptIn(ExperimentalCoroutinesApi::class)
class MviUiSchedulingTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(AndroidLikeTestMainDispatcher(dispatcher))
    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `queued platform continuation after source cleanup can bind and collect a new VM`() = runTest(dispatcher) {
        restoreFromUi(Dispatchers.Main)
    }

    @Test
    fun `immediate platform continuation after source cleanup can bind and collect a new VM`() = runTest(dispatcher) {
        restoreFromUi(Dispatchers.Main.immediate)
    }

    private suspend fun restoreFromUi(uiDispatcher: CoroutineDispatcher) = coroutineScope {
        val ui = launch(uiDispatcher) {
            val first = ProbeVm().also { store.put("screen", it); it.ui = it.uiApi() }
            val firstJob = first.viewModelScope.coroutineContext.job
            val binding = launch(start = CoroutineStart.UNDISPATCHED) {
                first.withUiBinding { awaitCancellation() }
            }
            withTimeout(5_000) { first.snapshot.first { it.state.ready } }
            binding.cancelAndJoin()
            store.clear()
            withTimeout(5_000) { firstJob.join() }
            assertEquals(0, first.activeSources)

            val restored = ProbeVm().also { store.put("screen", it); it.ui = it.uiApi() }
            val restoredJob = restored.viewModelScope.coroutineContext.job
            val delivered = CompletableDeferred<Signal>()
            val nextBinding = launch(start = CoroutineStart.UNDISPATCHED) {
                restored.withUiBinding { awaitCancellation() }
            }
            val effects = launch(start = CoroutineStart.UNDISPATCHED) {
                restored.collectEffects { delivered.complete(it) }
            }
            restored.ui.onIntent(Notify)
            assertEquals(Signal, withTimeout(5_000) { delivered.await() })
            withTimeout(5_000) { restored.snapshot.first { it.state.ready } }
            assertEquals(1, restored.activeSources)
            effects.cancelAndJoin()
            nextBinding.cancelAndJoin()
            store.clear()
            withTimeout(5_000) { restoredJob.join() }
            assertEquals(0, restored.activeSources)
        }
        ui.join()
    }

    private data class Snapshot(val ready: Boolean = false) : UiState
    private data object Notify : UiIntent
    private data object Signal : UiEffect

    private class ProbeVm : BaseMviViewModel<Snapshot, Notify, Signal>(Snapshot(), sourceStopTimeoutMillis = 0) {
        lateinit var ui: MviUiApi<Snapshot, Notify>

        var activeSources = 0
            private set

        override fun MviDefinition<Snapshot, Notify, Signal>.define() {
            source("data", flow = {
                flow {
                    activeSources++
                    try {
                        delay(1)
                        emit(Unit)
                        awaitCancellation()
                    } finally { activeSources-- }
                }
            }) { copy(ready = true) }
            on<Notify> { tryEffect(Signal) }
        }
    }

}
