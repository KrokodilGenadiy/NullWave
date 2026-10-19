package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseMviViewModelEffectTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextKey = 0

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
    fun `full buffer rejects new signals and draining preserves accepted order and frees capacity`() =
        runTest(dispatcher) {
            val vm = own()
            val accepted = fillBuffer(vm)
            assertEquals(64, accepted.size)
            assertFalse(vm.notify(-1))

            val received = mutableListOf<Signal>()
            val firstCollector = backgroundScope.launch {
                vm.collectEffects(received::add)
            }
            runCurrent()
            firstCollector.cancelAndJoin()
            assertTrue(firstCollector.isCompleted)
            assertEquals(accepted, received)

            assertTrue(vm.notify(-2))
            val nextCollector = backgroundScope.launch { vm.collectEffects(received::add) }
            runCurrent()
            assertEquals(accepted + Signal(-2), received)
            nextCollector.cancel()
            runCurrent()

            val replay = mutableListOf<Signal>()
            backgroundScope.launch { vm.collectEffects(replay::add) }
            runCurrent()
            assertTrue(replay.isEmpty())
        }

    @Test
    fun `full effect buffer does not keep a completed idle command busy`() = runTest(dispatcher) {
        val vm = own()
        fillBuffer(vm)
        val results = mutableListOf<Boolean>()

        repeat(3) { index ->
            assertTrue(vm.save.submit(onAccepted = { copy(loading = true) }) {
                assertTrue(commitState { copy(value = index + 1, loading = false) })
                results += vm.notify(-1)
            })
            runCurrent()
            assertEquals(index + 1, vm.state.value.value)
            assertFalse(vm.state.value.loading)
            assertEquals(List(index + 1) { false }, results)
        }
        assertFalse(vm.save.cancel())
    }

    @Test
    fun `full effect buffer does not stop FIFO from advancing to the next command`() = runTest(dispatcher) {
        val vm = own()
        fillBuffer(vm)
        val releaseFirst = CompletableDeferred<Unit>()
        val finished = mutableListOf<Int>()

        assertTrue(vm.queue.submit {
            releaseFirst.await()
            assertTrue(commitState { copy(value = 1) })
            assertFalse(vm.notify(-1))
            finished += 1
        })
        assertTrue(vm.queue.submit {
            assertTrue(commitState { copy(value = 2) })
            assertFalse(vm.notify(-2))
            finished += 2
        })
        runCurrent()
        assertTrue(finished.isEmpty())

        releaseFirst.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2), finished)
        assertEquals(2, vm.state.value.value)
        assertFalse(vm.queue.cancel())
    }

    @Test
    fun `cancelled caller throws before offering to either a free or full buffer`() = runTest(dispatcher) {
        for (full in listOf(false, true)) {
            val vm = own()
            val accepted = if (full) fillBuffer(vm) else emptyList()
            var cancellationObserved = false
            var returned = false
            val caller = backgroundScope.launch {
                currentCoroutineContext().cancel()
                try {
                    vm.notify(-1)
                    returned = true
                } catch (cancelled: CancellationException) {
                    cancellationObserved = true
                    throw cancelled
                }
            }
            runCurrent()
            assertTrue(caller.isCancelled)
            assertTrue(cancellationObserved)
            assertFalse(returned)

            val received = mutableListOf<Signal>()
            backgroundScope.launch { vm.collectEffects(received::add) }
            runCurrent()
            assertEquals(accepted, received)
        }
    }

    @Test
    fun `closed channel returns false for an active caller but cancellation still takes priority`() =
        runTest(dispatcher) {
            val vm = own()
            assertTrue(vm.notify(1))
            store.clear()
            runCurrent()
            assertFalse(vm.notify(2))

            var cancellationObserved = false
            backgroundScope.launch {
                currentCoroutineContext().cancel()
                try {
                    vm.notify(3)
                    error("A cancelled caller must not return an acceptance result")
                } catch (cancelled: CancellationException) {
                    cancellationObserved = true
                    throw cancelled
                }
            }
            runCurrent()
            assertTrue(cancellationObserved)
        }

    @Test
    fun `replacement from committed state cancels the old effect before buffer acceptance`() =
        runTest(dispatcher) {
            val vm = own()
            val received = mutableListOf<Signal>()
            backgroundScope.launch { vm.collectEffects(received::add) }
            var replacementAccepted = false
            var oldCancelled = false
            var oldReturned = false

            // Main stays queued; this collector reenters synchronously during state publication.
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.state.collect { state ->
                    if (state.value == 1) {
                        replacementAccepted = vm.search.submit(onAccepted = { copy(value = 2) }) {
                            assertTrue(vm.notify(2))
                        }
                    }
                }
            }
            assertTrue(vm.search.submit {
                assertTrue(commitState { copy(value = 1) })
                try {
                    vm.notify(1)
                    oldReturned = true
                } catch (cancelled: CancellationException) {
                    oldCancelled = true
                    throw cancelled
                }
            })
            runCurrent()

            assertTrue(replacementAccepted)
            assertTrue(oldCancelled)
            assertFalse(oldReturned)
            assertEquals(2, vm.state.value.value)
            assertEquals(listOf(Signal(2)), received)
        }

    private fun own(): EffectViewModel = EffectViewModel().also {
        store.put("effects-${nextKey++}", it)
    }

    /** Locate the bounded buffer's rejection without depending on any global coroutine setting. */
    private suspend fun fillBuffer(vm: EffectViewModel): List<Signal> = withTimeout(1_000) {
        val accepted = mutableListOf<Signal>()
        for (value in 0 until 10_000) {
            if (!vm.notify(value)) return@withTimeout accepted
            accepted += Signal(value)
        }
        error("Expected the bounded effect buffer to reject without a consumer")
    }

    private data class Snapshot(val value: Int = 0, val loading: Boolean = false) : UiState
    private data class Signal(val value: Int) : UiEffect

    private class EffectViewModel : MviTestViewModel<Snapshot, Nothing, Signal>(Snapshot()) {
        val save = idleOperation()
        val queue = queuedOperation(maxPending = 1)
        val search = latestOperation()

        override fun onIntent(intent: Nothing) = Unit

        suspend fun notify(value: Int): Boolean = sendEffect(Signal(value))
    }
}
