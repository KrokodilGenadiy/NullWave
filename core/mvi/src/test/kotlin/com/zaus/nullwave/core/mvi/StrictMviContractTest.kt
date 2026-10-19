package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Public-contract checks deliberately exercise misuse through otherwise compilable Kotlin. */
@OptIn(ExperimentalCoroutinesApi::class)
class StrictMviContractTest {
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
    fun `definition reads fully initialized dependencies and is created once`() = runTest(dispatcher) {
        lateinit var vm: ProbeViewModel
        var definitions = 0
        vm = own {
            definitions++
            assertTrue(vm.constructed)
            on<Start> { reduce { copy(value = value + 1) } }
        }
        assertEquals(0, definitions)
        assertEquals(TestState(), vm.state.value)

        vm.onIntent(Start)
        vm.onIntent(Start)

        assertEquals(1, definitions)
        assertEquals(2, vm.state.value.value)
    }

    @Test
    fun `failed definition is not retried or partially exposed`() = runTest(dispatcher) {
        val failure = IllegalStateException("broken schema")
        var definitions = 0
        val vm = own {
            definitions++
            on<Start> { reduce { copy(value = 1) } }
            throw failure
        }

        val first = assertThrows(IllegalStateException::class.java) { vm.onIntent(Start) }
        val second = assertThrows(IllegalStateException::class.java) { vm.onIntent(Start) }

        assertEquals(1, definitions)
        assertSame(failure, first)
        assertTrue(second === first || second.cause === first)
        assertEquals(TestState(), vm.state.value)
    }

    @Test
    fun `unregistered intent fails instead of silently doing nothing`() = runTest(dispatcher) {
        val vm = own { on<Start> {} }
        vm.onIntent(Start)

        val failure = assertThrows(RuntimeException::class.java) { vm.onIntent(Stop) }

        assertTrue(failure.message.orEmpty().contains("Stop"))
        assertEquals(TestState(), vm.state.value)
    }

    @Test
    fun `duplicate intent handlers fail while building the definition`() = runTest(dispatcher) {
        val vm = own {
            on<Start> {}
            on<Start> { reduce { copy(value = 1) } }
        }

        assertThrows(RuntimeException::class.java) { vm.onIntent(Start) }
        assertEquals(TestState(), vm.state.value)
    }

    @Test
    fun `duplicate operation names fail even across different policies`() = runTest(dispatcher) {
        val vm = own {
            latest<Unit, Unit>("save") { execute {} }
            queued<Unit, Unit>("save") { execute {} }
            on<Start> {}
        }

        assertThrows(RuntimeException::class.java) { vm.onIntent(Start) }
    }

    @Test
    fun `blank operation name is rejected`() = runTest(dispatcher) {
        val vm = own {
            idle<Unit, Unit>(" ") { execute {} }
            on<Start> {}
        }

        assertThrows(RuntimeException::class.java) { vm.onIntent(Start) }
    }

    @Test
    fun `operation without execute is rejected before any intent runs`() = runTest(dispatcher) {
        var handled = false
        val vm = own {
            latest<Int, Int>("incomplete") { onResult { _, result -> copy(value = result) } }
            on<Start> { handled = true }
        }

        assertThrows(RuntimeException::class.java) { vm.onIntent(Start) }
        assertFalse(handled)
    }

    @Test
    fun `duplicate execute declarations are rejected`() = runTest(dispatcher) {
        val vm = own {
            latest<Int, Int>("duplicate") {
                execute { it }
                execute { it + 1 }
            }
            on<Start> {}
        }

        assertThrows(RuntimeException::class.java) { vm.onIntent(Start) }
    }

    @Test
    fun `duplicate result reducers are rejected`() = runTest(dispatcher) {
        val vm = own {
            latest<Int, Int>("duplicate") {
                execute { it }
                onResult { _, result -> copy(value = result) }
                onResult { _, result -> copy(value = result + 1) }
            }
            on<Start> {}
        }

        assertThrows(RuntimeException::class.java) { vm.onIntent(Start) }
    }

    @Test
    fun `nonpositive queue capacity is rejected during definition`() = runTest(dispatcher) {
        for (capacity in listOf(0, -1)) {
            val vm = own {
                queued<Unit, Unit>("queue", maxPending = capacity) { execute {} }
                on<Start> {}
            }

            assertThrows(RuntimeException::class.java) { vm.onIntent(Start) }
        }
    }

    @Test
    fun `escaped definition cannot register a handler after construction`() = runTest(dispatcher) {
        lateinit var registerLater: () -> Unit
        val vm = own {
            registerLater = { on<Stop> {} }
            on<Start> {}
        }
        vm.onIntent(Start)

        assertThrows(IllegalStateException::class.java) { registerLater() }
    }

    @Test
    fun `escaped operation builder cannot change accepted lifecycle after construction`() = runTest(dispatcher) {
        lateinit var configureLater: () -> Unit
        val vm = own {
            latest<Unit, Unit>("work") {
                execute {}
                configureLater = { onAccepted { copy(value = 99) } }
            }
            on<Start> {}
        }
        vm.onIntent(Start)

        assertThrows(IllegalStateException::class.java) { configureLater() }
        assertEquals(TestState(), vm.state.value)
    }

    @Test
    fun `operation builder is closed when its declaration fails`() = runTest(dispatcher) {
        lateinit var repairLater: () -> Unit
        val vm = own {
            latest<Unit, Unit>("broken") {
                repairLater = { execute {} }
                error("declaration failed")
            }
        }

        assertThrows(IllegalStateException::class.java) { vm.onIntent(Start) }
        assertThrows(IllegalStateException::class.java) { repairLater() }
    }

    @Test
    fun `escaped intent scope cannot write after its handler returns`() = runTest(dispatcher) {
        lateinit var mutateLater: () -> Unit
        val vm = own {
            on<Start> { mutateLater = { reduce { copy(value = 99) } } }
        }
        vm.onIntent(Start)

        assertThrows(IllegalStateException::class.java) { mutateLater() }
        assertEquals(TestState(), vm.state.value)
    }

    @Test
    fun `escaped intent scope cannot send an effect after its handler returns`() = runTest(dispatcher) {
        lateinit var sendLater: () -> Unit
        val vm = own {
            on<Start> { sendLater = { tryEffect(TestEffect(99)) } }
        }
        vm.onIntent(Start)

        assertThrows(IllegalStateException::class.java) { sendLater() }
    }

    @Test
    fun `intent scope closes even when its handler throws`() = runTest(dispatcher) {
        lateinit var mutateLater: () -> Unit
        val vm = own {
            on<Start> {
                mutateLater = { reduce { copy(value = 99) } }
                error("handler failed")
            }
        }

        assertThrows(IllegalStateException::class.java) { vm.onIntent(Start) }
        assertThrows(IllegalStateException::class.java) { mutateLater() }
        assertEquals(TestState(), vm.state.value)
    }

    @Test
    fun `operation token from a different ViewModel cannot be submitted`() = runTest(dispatcher) {
        lateinit var other: ProbeViewModel
        var executed = false
        val owner = own {
            val operation = latest<Int, Unit>("owned") { execute { executed = true } }
            other = own { on<Start> { submit(operation, 1) } }
            on<Start> {}
        }
        owner.onIntent(Start)

        assertThrows(IllegalStateException::class.java) { other.onIntent(Start) }
        runCurrent()
        assertFalse(executed)
    }

    @Test
    fun `operation token from a different ViewModel cannot be cancelled`() = runTest(dispatcher) {
        lateinit var other: ProbeViewModel
        val owner = own {
            val operation = idle<Unit, Unit>("owned") { execute {} }
            other = own { on<Start> { cancel(operation) } }
            on<Start> {}
        }
        owner.onIntent(Start)

        assertThrows(IllegalStateException::class.java) { other.onIntent(Start) }
    }

    @Test
    fun `intent dispatch from a reducer fails before it can publish partial state`() = runTest(dispatcher) {
        lateinit var vm: ProbeViewModel
        vm = own {
            on<Start> {
                reduce {
                    vm.onIntent(Stop)
                    copy(value = 1)
                }
            }
            on<Stop> { reduce { copy(value = 2) } }
        }

        assertThrows(IllegalStateException::class.java) { vm.onIntent(Start) }
        assertEquals(TestState(), vm.state.value)
    }

    @Test
    fun `state observer may dispatch a new intent after a reducer publishes`() = runTest(dispatcher) {
        val vm = own {
            on<Start> { reduce { copy(value = 1) } }
            on<Stop> { reduce { copy(value = 2) } }
        }
        val seen = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {
                seen += it.value
                if (it.value == 1) vm.onIntent(Stop)
            }
        }

        vm.onIntent(Start)

        assertEquals(listOf(0, 1, 2), seen)
        assertEquals(2, vm.state.value.value)
    }

    @Test
    fun `nested handler cannot use the still open outer intent receiver`() = runTest(dispatcher) {
        lateinit var mutateOuter: () -> Unit
        var rejected = false
        val vm = own {
            on<Start> {
                mutateOuter = { reduce { copy(value = 99) } }
                reduce { copy(value = 1) }
            }
            on<Stop> {
                try {
                    mutateOuter()
                } catch (_: IllegalStateException) {
                    rejected = true
                }
                reduce { copy(value = 2) }
            }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { if (it.value == 1) vm.onIntent(Stop) }
        }

        vm.onIntent(Start)

        assertTrue(rejected)
        assertEquals(2, vm.state.value.value)
    }

    @Test
    fun `worker-thread intent is marshalled to Main before definition or state mutation`() = runTest(dispatcher) {
        var definitions = 0
        val mainThread = Thread.currentThread()
        val handledOn = AtomicReference<Thread>()
        val vm = own {
            definitions++
            on<Start> {
                handledOn.set(Thread.currentThread())
                reduce { copy(value = 1) }
            }
        }
        val failure = AtomicReference<Throwable>()
        val worker = Thread {
            try {
                vm.onIntent(Start)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }

        worker.start()
        worker.join()

        assertEquals(null, failure.get())
        assertEquals(0, definitions)
        assertEquals(TestState(), vm.state.value)

        runCurrent()

        assertEquals(1, definitions)
        assertSame(mainThread, handledOn.get())
        assertEquals(1, vm.state.value.value)
    }

    @Test
    fun `state invariant is checked before invalid state becomes visible`() = runTest(dispatcher) {
        val vm = own {
            validateState { check(it.value >= 0) { "Value must be nonnegative" } }
            on<Start> { reduce { copy(value = -1) } }
            on<Stop> { reduce { copy(value = 2) } }
        }
        val seen = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { seen += it.value }
        }

        assertThrows(IllegalStateException::class.java) { vm.onIntent(Start) }
        assertEquals(listOf(0), seen)
        assertEquals(TestState(), vm.state.value)
        vm.onIntent(Stop)
        assertEquals(listOf(0, 2), seen)
    }

    private fun own(
        definition: MviDefinition<TestState, TestIntent, TestEffect>.() -> Unit,
    ): ProbeViewModel = ProbeViewModel(definition).also { store.put("strict-${nextKey++}", it) }

    private data class TestState(val value: Int = 0) : UiState
    private sealed interface TestIntent : UiIntent
    private data object Start : TestIntent
    private data object Stop : TestIntent
    private data class TestEffect(val value: Int) : UiEffect

    private class ProbeViewModel(
        private val definition: MviDefinition<TestState, TestIntent, TestEffect>.() -> Unit,
    ) : BaseMviViewModel<TestState, TestIntent, TestEffect>(
        initialState = TestState(),
    ) {
        val constructed = true

        override fun MviDefinition<TestState, TestIntent, TestEffect>.define() = definition()
    }
}
