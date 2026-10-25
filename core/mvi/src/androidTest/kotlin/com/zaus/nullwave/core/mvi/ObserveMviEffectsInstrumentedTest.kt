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
import com.zaus.nullwave.core.mvi.ui.ObserveMviEffects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.junit.runner.RunWith

/** Compose runtime calls Android tracing; this checks actual effect/recomposition ownership. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMviEffectsInstrumentedTest {
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
    fun callbacksStayCurrentAndCompositionKeysWaitForPreviousHandlerCleanup() = runTest {
        val firstOwner = Owner(Lifecycle.State.STARTED)
        val secondOwner = Owner(Lifecycle.State.STARTED)
        val firstVm = Vm().also { store.put("first", it) }
        val secondVm = Vm().also { store.put("second", it) }
        val firstUi = firstVm.uiApi()
        val secondUi = secondVm.uiApi()
        val selectedVm = mutableStateOf(firstVm)
        val selectedOwner = mutableStateOf(firstOwner)
        val threshold = mutableStateOf(Lifecycle.State.STARTED)
        val seen = mutableListOf<String>()
        val onEffect = mutableStateOf<suspend (Signal) -> Unit>({ seen += "old:${it.value}" })
        val cleanup = CompletableDeferred<Unit>()
        val frames = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frames)
        val composition = Composition(EmptyApplier(), recomposer)
        val runner = backgroundScope.launch(frames) { recomposer.runRecomposeAndApplyChanges() }

        try {
            composition.setContent {
                ObserveMviEffects(selectedVm.value, selectedOwner.value, threshold.value, onEffect.value)
            }
            runCurrent()
            firstUi.onIntent(Notify(1))
            runCurrent()
            assertEquals(listOf("old:1"), seen)

            onEffect.value = { signal ->
                seen += "new:${signal.value}"
                if (signal.value == 3) {
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) { cleanup.await() }
                    }
                }
            }
            Snapshot.sendApplyNotifications()
            runCurrent()
            frames.sendFrame(1L)
            runCurrent()
            firstUi.onIntent(Notify(2))
            runCurrent()
            assertEquals(listOf("old:1", "new:2"), seen)
            assertEquals(1, firstOwner.registry.observerCount)

            firstUi.onIntent(Notify(3))
            runCurrent()
            selectedOwner.value = secondOwner
            Snapshot.sendApplyNotifications()
            runCurrent()
            frames.sendFrame(2L)
            runCurrent()
            firstUi.onIntent(Notify(4))
            runCurrent()
            assertEquals(listOf("old:1", "new:2", "new:3"), seen)
            assertEquals(0, secondOwner.registry.observerCount)

            cleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf("old:1", "new:2", "new:3", "new:4"), seen)
            assertEquals(0, firstOwner.registry.observerCount)
            assertEquals(1, secondOwner.registry.observerCount)

            threshold.value = Lifecycle.State.RESUMED
            Snapshot.sendApplyNotifications()
            runCurrent()
            frames.sendFrame(3L)
            runCurrent()
            firstUi.onIntent(Notify(5))
            runCurrent()
            assertEquals("new:4", seen.last())
            secondOwner.registry.currentState = Lifecycle.State.RESUMED
            runCurrent()
            assertEquals("new:5", seen.last())

            selectedVm.value = secondVm
            Snapshot.sendApplyNotifications()
            runCurrent()
            frames.sendFrame(4L)
            runCurrent()
            firstUi.onIntent(Notify(6))
            secondUi.onIntent(Notify(7))
            runCurrent()
            assertEquals("new:7", seen.last())

            selectedVm.value = firstVm
            Snapshot.sendApplyNotifications()
            runCurrent()
            frames.sendFrame(5L)
            runCurrent()
            assertEquals(listOf("old:1", "new:2", "new:3", "new:4", "new:5", "new:7", "new:6"), seen)
            assertEquals(1, secondOwner.registry.observerCount)

            composition.dispose()
            runCurrent()
            assertEquals(0, secondOwner.registry.observerCount)
        } finally {
            cleanup.complete(Unit)
            composition.dispose()
            recomposer.cancel()
            runner.cancel()
        }
    }

    @Test
    fun recreatedCompositionWithRetainedViewModelWaitsForOldEffectCleanup() = runTest {
        val vm = Vm().also { store.put("retained", it) }
        val ui = vm.uiApi()
        val firstOwner = Owner(Lifecycle.State.STARTED)
        val secondOwner = Owner(Lifecycle.State.STARTED)
        val cleanup = CompletableDeferred<Unit>()
        var cleanupStarted = false
        val seen = mutableListOf<String>()
        val frames = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frames)
        val firstComposition = Composition(EmptyApplier(), recomposer)
        val secondComposition = Composition(EmptyApplier(), recomposer)
        val runner = backgroundScope.launch(frames) { recomposer.runRecomposeAndApplyChanges() }

        try {
            firstComposition.setContent {
                ObserveMviEffects(vm, firstOwner) {
                    seen += "old:${it.value}"
                    try { awaitCancellation() } finally {
                        cleanupStarted = true
                        withContext(NonCancellable) { cleanup.await() }
                    }
                }
            }
            runCurrent()
            ui.onIntent(Notify(1))
            runCurrent()
            firstComposition.dispose()
            runCurrent()
            assertTrue(cleanupStarted)

            // This is a new composition, so remember { Mutex() } cannot preserve the handoff.
            secondComposition.setContent {
                ObserveMviEffects(vm, secondOwner) { seen += "new:${it.value}" }
            }
            runCurrent()
            ui.onIntent(Notify(2))
            runCurrent()
            assertEquals(listOf("old:1"), seen)
            assertEquals(1, secondOwner.registry.observerCount)

            cleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf("old:1", "new:2"), seen)
            assertEquals(0, firstOwner.registry.observerCount)
            ui.onIntent(Notify(3))
            runCurrent()
            assertEquals(listOf("old:1", "new:2", "new:3"), seen)
            secondComposition.dispose()
            runCurrent()
            assertEquals(0, secondOwner.registry.observerCount)
        } finally {
            cleanup.complete(Unit)
            firstComposition.dispose()
            secondComposition.dispose()
            recomposer.cancel()
            runner.cancel()
        }
    }

    private data object ScreenState : UiState
    private data class Notify(val value: Int) : UiIntent
    private data class Signal(val value: Int) : UiEffect

    private class Vm : BaseMviViewModel<ScreenState, Notify, Signal>(ScreenState) {
        override fun MviDefinition<ScreenState, Notify, Signal>.define() {
            on<Notify> { tryEffect(Signal(it.value)) }
        }

    }

    private class Owner(initial: Lifecycle.State) : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        init { registry.currentState = initial }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun onClear() = Unit
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
    }
}
