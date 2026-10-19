package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** Public entry regressions, including clear between the caller's check and Main dispatch. */
@OptIn(ExperimentalCoroutinesApi::class)
class ManagedEffectLifecycleTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextKey = 0

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
    fun `collection after clear returns without initializing definition or cancelling caller`() = runTest(dispatcher) {
        val vm = own()
        store.clear()

        repeat(2) {
            vm.collectEffects { error("A cleared VM must not invoke its handler") }
        }

        assertEquals(0, vm.definitions)
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test
    fun `worker collection after initialized VM clear returns without buffered signals`() = runTest(dispatcher) {
        val vm = own()
        vm.onIntent(Notify(1))
        assertEquals(1, vm.definitions)
        store.clear()

        // The closed entry must also be safe when called off Main.
        withContext(Dispatchers.Default) {
            vm.collectEffects { error("Clear must discard buffered signals") }
            assertTrue(currentCoroutineContext().isActive)
        }

        assertEquals(1, vm.definitions)
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test
    fun `clear while collection waits for Main returns normally with or without a definition`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        for (initialized in listOf(false, true)) {
            val vm = own()
            if (initialized) {
                vm.onIntent(Notify(1))
                runCurrent()
            }
            var returned = false
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.collectEffects { error("A collector waiting for Main must not outlive clear") }
                returned = true
            }
            assertFalse(returned)

            store.clear()
            runCurrent()

            assertTrue(returned)
            assertTrue(collector.isCompleted)
            assertFalse(collector.isCancelled)
            assertEquals(if (initialized) 1 else 0, vm.definitions)
        }
    }

    @Test
    fun `late collection returns during old handler cleanup after clear`() = runTest(dispatcher) {
        val vm = own()
        val cleanup = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        var cleanupStarted = false
        val collector = backgroundScope.launch {
            vm.collectEffects {
                received += it.value
                try {
                    awaitCancellation()
                } finally {
                    cleanupStarted = true
                    withContext(NonCancellable) { cleanup.await() }
                }
            }
        }
        try {
            vm.onIntent(Notify(1))
            runCurrent()
            vm.onIntent(Notify(2))
            store.clear()
            runCurrent()
            assertTrue(cleanupStarted)
            assertFalse(collector.isCompleted)

            // No lease conflict with the still-cleaning old collector, and no replay.
            vm.collectEffects { error("Closed entry must not acquire the old consumer's lease") }
            assertEquals(listOf(1), received)

            cleanup.complete(Unit)
            runCurrent()
            assertTrue(collector.isCompleted)
            assertFalse(collector.isCancelled)
        } finally {
            cleanup.complete(Unit)
        }
    }

    @Test
    fun `cancellation before Main entry leaves definition and consumer available`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val vm = own()
        val cancelled = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.collectEffects { error("Cancelled caller must not consume a signal") }
        }
        cancelled.cancel()
        runCurrent()
        assertTrue(cancelled.isCompleted)
        assertEquals(0, vm.definitions)

        val received = mutableListOf<Signal>()
        val collector = backgroundScope.launch { vm.collectEffects(received::add) }
        vm.onIntent(Notify(7))
        runCurrent()
        assertEquals(listOf(Signal(7)), received)
        collector.cancelAndJoin()
    }

    @Test
    fun `handler failure escapes and releases the consumer for pending signals`() = runTest(dispatcher) {
        val vm = own()
        vm.onIntent(Notify(1))
        vm.onIntent(Notify(2))
        val expected = IllegalStateException("UI handler failed")

        val failure = runCatching { vm.collectEffects { throw expected } }.exceptionOrNull()

        assertTrue(generateSequence(failure) { it.cause }.any { it === expected })
        assertTrue(currentCoroutineContext().isActive)
        val received = mutableListOf<Signal>()
        val collector = backgroundScope.launch { vm.collectEffects(received::add) }
        runCurrent()
        assertEquals(listOf(Signal(2)), received)
        collector.cancelAndJoin()
    }

    private fun own(): ProbeViewModel = ProbeViewModel().also { store.put("effect-lifecycle-${nextKey++}", it) }

    private data class Snapshot(val value: Int = 0) : UiState
    private data class Notify(val value: Int) : UiIntent
    private data class Signal(val value: Int) : UiEffect

    private class ProbeViewModel : BaseMviViewModel<Snapshot, Notify, Signal>(Snapshot()) {
        var definitions = 0

        override fun MviDefinition<Snapshot, Notify, Signal>.define() {
            definitions++
            on<Notify> { tryEffect(Signal(it.value)) }
        }
    }
}
