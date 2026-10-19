package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.mvi.ui.toUiText
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypedResultMviIntegrationTest {
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
    fun `expected failure updates error state and a later success clears it`() = runTest(dispatcher) {
        val first = CompletableDeferred<AppResult<String, DataError>>()
        val second = CompletableDeferred<AppResult<String, DataError>>()
        val vm = operation { input -> if (input == 1) first.await() else second.await() }

        vm.onIntent(Load(1))
        runCurrent()
        assertTrue(vm.state.value.loading)
        first.complete(AppResult.Failure(DataError.AccessDenied, SecurityException("private file path")))
        runCurrent()
        assertEquals(ScreenState(error = DataError.AccessDenied.toUiText()), vm.state.value)

        vm.onIntent(Load(2))
        runCurrent()
        assertTrue(vm.state.value.loading)
        assertNull(vm.state.value.error)
        second.complete(AppResult.Success("restored"))
        runCurrent()

        assertEquals(ScreenState(value = "restored"), vm.state.value)
    }

    @Test
    fun `latest replacement ignores a stale typed failure from cancellation ignoring work`() = runTest(dispatcher) {
        val old = CompletableDeferred<AppResult<String, DataError>>()
        val applied = mutableListOf<Int>()
        val vm = own {
            val load = latest<Int, AppResult<String, DataError>>("load") {
                execute { input ->
                    if (input == 1) withContext(NonCancellable) { old.await() }
                    else AppResult.Success("current")
                }
                onResult { input, result ->
                    applied += input
                    withResult(result)
                }
            }
            on<Load> { submit(load, it.id) }
        }

        vm.onIntent(Load(1))
        runCurrent()
        vm.onIntent(Load(2))
        runCurrent()
        old.complete(AppResult.Failure(DataError.Io, IllegalStateException("obsolete")))
        runCurrent()

        assertEquals(listOf(2), applied)
        assertEquals(ScreenState(value = "current"), vm.state.value)
    }

    @Test
    fun `repository cancellation stays cancellation rather than becoming a typed UI failure`() = runTest(dispatcher) {
        var entered = false
        val vm = operation {
            entered = true
            throw CancellationException("repository cancelled")
        }

        vm.onIntent(Load(1))
        runCurrent()

        assertTrue(entered)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.value)
    }

    @Test
    fun `typed source failure and recovery share the same live subscription`() = runTest(dispatcher) {
        val values = Channel<AppResult<String, DataError>>(Channel.UNLIMITED)
        var subscriptions = 0
        val vm = own {
            source("data", flow = {
                subscriptions += 1
                values.receiveAsFlow()
            }) { result -> withResult(result) }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()

        values.send(AppResult.Failure(DataError.NotFound))
        runCurrent()
        assertEquals(ScreenState(error = DataError.NotFound.toUiText()), vm.state.value)
        values.send(AppResult.Success("available again"))
        runCurrent()

        assertEquals(1, subscriptions)
        assertEquals(ScreenState(value = "available again"), vm.state.value)
    }

    private fun operation(work: suspend (Int) -> AppResult<String, DataError>): ProbeViewModel = own {
        val load = latest<Int, AppResult<String, DataError>>("load") {
            execute(work)
            onAccepted { copy(loading = true, error = null) }
            onResult { _, result -> withResult(result) }
            onFinished { copy(loading = false) }
        }
        on<Load> { submit(load, it.id) }
    }

    private fun own(definition: MviDefinition<ScreenState, Load, Signal>.() -> Unit): ProbeViewModel =
        ProbeViewModel(definition).also { store.put("typed-result", it) }

    private data class ScreenState(
        val value: String? = null,
        val error: UiText? = null,
        val loading: Boolean = false,
    ) : UiState {
        fun withResult(result: AppResult<String, DataError>): ScreenState = when (result) {
            is AppResult.Success -> copy(value = result.value, error = null)
            is AppResult.Failure -> copy(error = result.error.toUiText())
        }
    }

    private data class Load(val id: Int) : UiIntent
    private data object Signal : UiEffect

    private class ProbeViewModel(
        private val definition: MviDefinition<ScreenState, Load, Signal>.() -> Unit,
    ) : BaseMviViewModel<ScreenState, Load, Signal>(ScreenState(), sourceStopTimeoutMillis = 0) {
        override fun MviDefinition<ScreenState, Load, Signal>.define() = definition()
    }
}
