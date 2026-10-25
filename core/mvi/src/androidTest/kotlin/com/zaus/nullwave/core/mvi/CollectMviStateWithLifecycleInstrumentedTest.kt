package com.zaus.nullwave.core.mvi

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.MviUiState
import com.zaus.nullwave.core.mvi.ui.collectMviUiWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Compose runtime calls Android tracing, so this composition test needs the Android runtime. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CollectMviStateWithLifecycleInstrumentedTest {
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
    fun compositionSwitchesViewModelAndOwnerWithoutReusingStaleStateOrBinding() = runTest {
        val firstOwner = TestOwner(Lifecycle.State.CREATED)
        val secondOwner = TestOwner(Lifecycle.State.STARTED)
        val firstViewModel = ProbeViewModel().also {
            store.put("first", it)
            it.uiApi().onIntent(Write(11))
        }
        val secondViewModel = ProbeViewModel().also {
            store.put("second", it)
            it.uiApi().onIntent(Write(22))
        }
        // Intents enter the substituted Main dispatcher before the CREATED owner seeds its snapshot.
        runCurrent()
        val selectedViewModel = mutableStateOf(firstViewModel)
        val selectedOwner = mutableStateOf(firstOwner)
        val frames = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frames)
        val composition = Composition(EmptyApplier(), recomposer)
        val runner = backgroundScope.launch(frames) { recomposer.runRecomposeAndApplyChanges() }
        lateinit var rendered: MviUiState<TestState, Write>

        try {
            composition.setContent {
                rendered = selectedViewModel.value.collectMviUiWithLifecycle(selectedOwner.value)
            }
            runCurrent()
            assertEquals(TestState(11), rendered.state)
            val firstCallback = rendered.onIntent
            assertEquals(0, firstViewModel.starts)

            selectedViewModel.value = secondViewModel
            Snapshot.sendApplyNotifications()
            runCurrent()
            frames.sendFrame(1L)
            runCurrent()
            assertEquals(TestState(22), rendered.state)
            val secondCallback = rendered.onIntent
            assertNotSame(firstCallback, secondCallback)
            assertEquals(0, secondViewModel.starts)
            assertEquals(1, firstOwner.registry.observerCount)

            // No collection while CREATED: a new owner must seed its snapshot afresh.
            // Callback lifetime follows the VM, so a CREATED owner may still accept platform input.
            rendered.onIntent(Write(23))
            runCurrent()
            assertEquals(TestState(22), rendered.state)
            selectedOwner.value = secondOwner
            Snapshot.sendApplyNotifications()
            runCurrent()
            frames.sendFrame(2L)
            runCurrent()
            assertEquals(TestState(23), rendered.state)
            assertSame(secondCallback, rendered.onIntent)
            assertEquals(0, firstOwner.registry.observerCount)
            assertEquals(1, secondOwner.registry.observerCount)
            assertEquals(1, secondViewModel.activeSources)

            selectedViewModel.value = firstViewModel
            Snapshot.sendApplyNotifications()
            runCurrent()
            frames.sendFrame(3L)
            runCurrent()
            assertEquals(TestState(11), rendered.state)
            assertSame(firstCallback, rendered.onIntent)
            assertEquals(1, firstViewModel.activeSources)
            rendered.onIntent(Write(12))
            runCurrent()
            assertEquals(TestState(12), rendered.state)
            assertEquals(TestState(23), secondViewModel.state.value)
            advanceTimeBy(STOP_TIMEOUT)
            runCurrent()
            assertEquals(0, secondViewModel.activeSources)

            composition.dispose()
            runCurrent()
            assertEquals(0, secondOwner.registry.observerCount)
            advanceTimeBy(STOP_TIMEOUT)
            runCurrent()
            assertEquals(0, firstViewModel.activeSources)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.cancel()
        }
    }

    private data class TestState(val value: Int = 0) : UiState
    private data class Write(val value: Int) : UiIntent

    private class ProbeViewModel :
        BaseMviViewModel<TestState, Write, Nothing>(
            initialState = TestState(),
            sourceStopTimeoutMillis = STOP_TIMEOUT,
        ) {
        var starts = 0
            private set
        var activeSources = 0
            private set

        override fun MviDefinition<TestState, Write, Nothing>.define() {
            on<Write> { intent -> reduce { copy(value = intent.value) } }
            source(
                "composition-probe",
                flow = {
                    flow<Unit> {
                        starts++
                        activeSources++
                        try {
                            awaitCancellation()
                        } finally {
                            activeSources--
                        }
                    }
                },
            ) {
                this
            }
        }

    }

    private class TestOwner(initialState: Lifecycle.State) : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry

        init {
            registry.currentState = initialState
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun onClear() = Unit
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
    }

    private companion object {
        const val STOP_TIMEOUT = 100L
    }
}
