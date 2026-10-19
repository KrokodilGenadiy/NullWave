package com.zaus.nullwave.core.mvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.ui.collectStateWithUiBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class CollectMviStateWithLifecycleTest {
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
    fun `STARTED collects and binds while STOP suspends collection until the next START`() = runTest {
        val owner = TestOwner(Lifecycle.State.CREATED)
        val viewModel = viewModel()
        val snapshots = mutableListOf<TestState>()
        backgroundScope.launch {
            viewModel.collectStateWithUiBinding(owner.lifecycle) { snapshots += it }
        }
        runCurrent()
        assertEquals(0, viewModel.starts)
        assertTrue(snapshots.isEmpty())

        owner.moveTo(Lifecycle.State.STARTED)
        runCurrent()
        assertEquals(1, viewModel.starts)
        assertEquals(1, viewModel.activeSources)
        assertEquals(listOf(TestState()), snapshots)

        viewModel.write(1)
        runCurrent()
        assertEquals(TestState(1), snapshots.last())

        owner.moveTo(Lifecycle.State.CREATED)
        runCurrent()
        viewModel.write(2)
        runCurrent()
        assertEquals(TestState(1), snapshots.last())
        assertEquals(1, viewModel.activeSources)
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertEquals(0, viewModel.activeSources)

        owner.moveTo(Lifecycle.State.STARTED)
        runCurrent()
        assertEquals(2, viewModel.starts)
        assertEquals(1, viewModel.activeSources)
        assertEquals(TestState(2), snapshots.last())
    }

    @Test
    fun `RESUMED excludes a STARTED owner and resumes with the latest state`() = runTest {
        val owner = TestOwner(Lifecycle.State.STARTED)
        val viewModel = viewModel()
        val snapshots = mutableListOf<TestState>()
        backgroundScope.launch {
            viewModel.collectStateWithUiBinding(owner.lifecycle, Lifecycle.State.RESUMED) {
                snapshots += it
            }
        }
        runCurrent()
        assertEquals(0, viewModel.starts)
        assertTrue(snapshots.isEmpty())

        owner.moveTo(Lifecycle.State.RESUMED)
        runCurrent()
        assertEquals(1, viewModel.starts)
        assertEquals(listOf(TestState()), snapshots)

        owner.moveTo(Lifecycle.State.STARTED)
        runCurrent()
        viewModel.write(5)
        runCurrent()
        assertEquals(TestState(), snapshots.last())

        owner.moveTo(Lifecycle.State.RESUMED)
        runCurrent()
        assertEquals(TestState(5), snapshots.last())
        assertEquals(1, viewModel.starts)
    }

    @Test
    fun `destroying an active owner completes collection and releases its binding`() = runTest {
        val owner = TestOwner(Lifecycle.State.STARTED)
        val viewModel = viewModel()
        val collection = backgroundScope.launch {
            viewModel.collectStateWithUiBinding(owner.lifecycle) {}
        }
        runCurrent()
        assertEquals(1, viewModel.activeSources)
        assertEquals(1, owner.registry.observerCount)

        owner.moveTo(Lifecycle.State.DESTROYED)
        runCurrent()
        assertTrue(collection.isCompleted)
        assertFalse(collection.isCancelled)
        assertEquals(0, owner.registry.observerCount)
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertEquals(0, viewModel.activeSources)
    }

    @Test
    fun `an already destroyed owner never binds or collects`() = runTest {
        val owner = TestOwner(Lifecycle.State.CREATED).apply { moveTo(Lifecycle.State.DESTROYED) }
        val viewModel = viewModel()
        var collected = false

        viewModel.collectStateWithUiBinding(owner.lifecycle) { collected = true }
        runCurrent()

        assertFalse(collected)
        assertEquals(0, viewModel.starts)
        assertEquals(0, owner.registry.observerCount)
    }

    @Test
    fun `cancelling collection releases the binding and removes the lifecycle observer`() = runTest {
        val owner = TestOwner(Lifecycle.State.STARTED)
        val viewModel = viewModel()
        val collection = backgroundScope.launch {
            viewModel.collectStateWithUiBinding(owner.lifecycle) {}
        }
        runCurrent()
        assertEquals(1, viewModel.activeSources)

        collection.cancel()
        runCurrent()
        assertEquals(0, owner.registry.observerCount)
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertEquals(0, viewModel.activeSources)
    }

    @Test
    fun `collector failure propagates and releases the binding`() = runTest {
        val owner = TestOwner(Lifecycle.State.STARTED)
        val viewModel = viewModel()
        val failure = IllegalStateException("Rendering failed")

        supervisorScope {
            val collection = async {
                viewModel.collectStateWithUiBinding(owner.lifecycle) { throw failure }
            }
            runCurrent()
            val observed = try {
                collection.await()
                null
            } catch (error: IllegalStateException) {
                error
            }
            assertTrue(observed is IllegalStateException)
            assertEquals(failure.message, observed?.message)
        }

        assertEquals(0, owner.registry.observerCount)
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertEquals(0, viewModel.activeSources)
    }

    @Test
    fun `two active owners share sources until the last owner stops`() = runTest {
        val first = TestOwner(Lifecycle.State.STARTED)
        val second = TestOwner(Lifecycle.State.STARTED)
        val viewModel = viewModel()
        val firstStates = mutableListOf<TestState>()
        val secondStates = mutableListOf<TestState>()
        backgroundScope.launch {
            viewModel.collectStateWithUiBinding(first.lifecycle) { firstStates += it }
        }
        backgroundScope.launch {
            viewModel.collectStateWithUiBinding(second.lifecycle) { secondStates += it }
        }
        runCurrent()
        assertEquals(1, viewModel.starts)

        first.moveTo(Lifecycle.State.CREATED)
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertEquals(1, viewModel.activeSources)

        viewModel.write(9)
        runCurrent()
        assertEquals(TestState(), firstStates.last())
        assertEquals(TestState(9), secondStates.last())

        second.moveTo(Lifecycle.State.CREATED)
        runCurrent()
        advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        assertEquals(0, viewModel.activeSources)
        assertEquals(1, viewModel.stops)
    }

    @Test
    fun `nonvisible lifecycle thresholds are rejected before registering an observer`() = runTest {
        val owner = TestOwner(Lifecycle.State.STARTED)
        val viewModel = viewModel()
        for (threshold in listOf(
            Lifecycle.State.DESTROYED,
            Lifecycle.State.INITIALIZED,
            Lifecycle.State.CREATED,
        )) {
            val failure = try {
                viewModel.collectStateWithUiBinding(owner.lifecycle, threshold) {}
                null
            } catch (error: IllegalArgumentException) {
                error
            }
            assertTrue("$threshold should be rejected", failure is IllegalArgumentException)
            assertEquals(0, owner.registry.observerCount)
        }
        runCurrent()
        assertEquals(0, viewModel.starts)
    }

    @Test
    fun `a cleared ViewModel does not collect from a still active owner`() = runTest {
        val owner = TestOwner(Lifecycle.State.STARTED)
        val viewModel = viewModel()
        store.clear()
        var collected = false
        backgroundScope.launch {
            viewModel.collectStateWithUiBinding(owner.lifecycle) { collected = true }
        }
        runCurrent()

        assertFalse(collected)
        assertEquals(0, viewModel.starts)
    }


    private fun viewModel(): ProbeViewModel = ProbeViewModel().also { store.put("probe", it) }

    private data class TestState(val value: Int = 0) : UiState

    private class ProbeViewModel : MviTestViewModel<TestState, Nothing, Nothing>(
        initialState = TestState(),
        sourceStopTimeoutMillis = STOP_TIMEOUT,
    ) {
        var starts = 0
            private set
        var stops = 0
            private set
        var activeSources = 0
            private set

        override fun onIntent(intent: Nothing) = Unit

        override suspend fun observeSources() {
            starts++
            activeSources++
            try {
                awaitCancellation()
            } finally {
                activeSources--
                stops++
            }
        }

        fun write(value: Int) = setState { copy(value = value) }
    }

    private class TestOwner(initialState: Lifecycle.State) : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry

        init {
            moveTo(initialState)
        }

        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 100L
    }
}
