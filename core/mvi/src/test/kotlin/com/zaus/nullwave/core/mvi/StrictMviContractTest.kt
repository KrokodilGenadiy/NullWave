package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.dsl.Operation
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import java.util.concurrent.atomic.AtomicReference
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
        assertEquals(TestState(), vm.snapshot.value.state)

        vm.ui.onIntent(Start)
        vm.ui.onIntent(Start)

        assertEquals(1, definitions)
        assertEquals(2, vm.snapshot.value.state.value)
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

        val first = assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Start) }
        val second = assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Start) }

        assertEquals(1, definitions)
        assertSame(failure, first)
        assertTrue(second === first || second.cause === first)
        assertEquals(TestState(), vm.snapshot.value.state)
    }

    @Test
    fun `unregistered intent fails instead of silently doing nothing`() = runTest(dispatcher) {
        val vm = own { on<Start> {} }
        vm.ui.onIntent(Start)

        val failure = assertThrows(RuntimeException::class.java) { vm.ui.onIntent(Stop) }

        assertTrue(failure.message.orEmpty().contains("Stop"))
        assertEquals(TestState(), vm.snapshot.value.state)
    }

    @Test
    fun `duplicate intent handlers fail while building the definition`() = runTest(dispatcher) {
        val vm = own {
            on<Start> {}
            on<Start> { reduce { copy(value = 1) } }
        }

        assertThrows(RuntimeException::class.java) { vm.ui.onIntent(Start) }
        assertEquals(TestState(), vm.snapshot.value.state)
    }

    @Test
    fun `duplicate operation names fail even across different policies`() = runTest(dispatcher) {
        val vm = own {
            latest<Unit, Unit>("save") { execute {} }
            queued<Unit, Unit>("save") { execute {} }
            on<Start> {}
        }

        assertThrows(RuntimeException::class.java) { vm.ui.onIntent(Start) }
    }

    @Test
    fun `blank operation name is rejected`() = runTest(dispatcher) {
        val vm = own {
            idle<Unit, Unit>(" ") { execute {} }
            on<Start> {}
        }

        assertThrows(RuntimeException::class.java) { vm.ui.onIntent(Start) }
    }

    @Test
    fun `operation without execute is rejected before any intent runs`() = runTest(dispatcher) {
        var handled = false
        val vm = own {
            latest<Int, Int>("incomplete") { onResult { _, result -> copy(value = result) } }
            on<Start> { handled = true }
        }

        assertThrows(RuntimeException::class.java) { vm.ui.onIntent(Start) }
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

        assertThrows(RuntimeException::class.java) { vm.ui.onIntent(Start) }
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

        assertThrows(RuntimeException::class.java) { vm.ui.onIntent(Start) }
    }

    @Test
    fun `nonpositive queue capacity is rejected during definition`() = runTest(dispatcher) {
        for (capacity in listOf(0, -1)) {
            val vm = own {
                queued<Unit, Unit>("queue", maxPending = capacity) { execute {} }
                on<Start> {}
            }

            assertThrows(RuntimeException::class.java) { vm.ui.onIntent(Start) }
        }
    }

    @Test
    fun `escaped definition cannot register a handler after construction`() = runTest(dispatcher) {
        lateinit var registerLater: () -> Unit
        val vm = own {
            registerLater = { on<Stop> {} }
            on<Start> {}
        }
        vm.ui.onIntent(Start)

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
        vm.ui.onIntent(Start)

        assertThrows(IllegalStateException::class.java) { configureLater() }
        assertEquals(TestState(), vm.snapshot.value.state)
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

        assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Start) }
        assertThrows(IllegalStateException::class.java) { repairLater() }
    }

    @Test
    fun `escaped intent scope cannot write after its handler returns`() = runTest(dispatcher) {
        lateinit var mutateLater: () -> Unit
        val vm = own {
            on<Start> { mutateLater = { reduce { copy(value = 99) } } }
        }
        vm.ui.onIntent(Start)

        assertThrows(IllegalStateException::class.java) { mutateLater() }
        assertEquals(TestState(), vm.snapshot.value.state)
    }

    @Test
    fun `escaped intent scope cannot send an effect after its handler returns`() = runTest(dispatcher) {
        lateinit var sendLater: () -> Unit
        val vm = own {
            on<Start> { sendLater = { tryEffect(TestEffect(99)) } }
        }
        vm.ui.onIntent(Start)

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

        assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Start) }
        assertThrows(IllegalStateException::class.java) { mutateLater() }
        assertEquals(TestState(), vm.snapshot.value.state)
    }

    @Test
    fun `operation token from a different ViewModel cannot be submitted`() = runTest(dispatcher) {
        lateinit var operation: Operation<Int>
        var executed = false
        val owner = own {
            operation = latest<Int, Unit>("owned") { execute { executed = true } }
            on<Start> {}
        }
        owner.ui.onIntent(Start)
        val other = own { on<Start> { submit(operation, 1) } }

        assertThrows(IllegalStateException::class.java) { other.ui.onIntent(Start) }
        runCurrent()
        assertFalse(executed)
    }

    @Test
    fun `operation token from a different ViewModel cannot be cancelled`() = runTest(dispatcher) {
        lateinit var operation: Operation<Unit>
        val owner = own {
            operation = idle<Unit, Unit>("owned") { execute {} }
            on<Start> {}
        }
        owner.ui.onIntent(Start)
        val other = own { on<Start> { cancel(operation) } }

        assertThrows(IllegalStateException::class.java) { other.ui.onIntent(Start) }
    }

    @Test
    fun `captured handler cannot nest a reducer before partial state is published`() = runTest(dispatcher) {
        val vm = own {
            on<Start> {
                val mutateHandler = { reduce { copy(value = 2) } }
                reduce {
                    mutateHandler()
                    copy(value = 1)
                }
            }
        }

        assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Start) }
        assertEquals(TestState(), vm.snapshot.value.state)
    }

    @Test
    fun `state observer may dispatch a new intent after a reducer publishes`() = runTest(dispatcher) {
        val vm = own {
            on<Start> { reduce { copy(value = 1) } }
            on<Stop> { reduce { copy(value = 2) } }
        }
        val seen = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.snapshot.map { it.state }.distinctUntilChanged().collect {
                seen += it.value
                if (it.value == 1) vm.ui.onIntent(Stop)
            }
        }

        vm.ui.onIntent(Start)

        assertEquals(listOf(0, 1, 2), seen)
        assertEquals(2, vm.snapshot.value.state.value)
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
            vm.snapshot.map { it.state }.distinctUntilChanged().collect { if (it.value == 1) vm.ui.onIntent(Stop) }
        }

        vm.ui.onIntent(Start)

        assertTrue(rejected)
        assertEquals(2, vm.snapshot.value.state.value)
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
                vm.ui.onIntent(Start)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }

        worker.start()
        worker.join()

        assertEquals(null, failure.get())
        assertEquals(0, definitions)
        assertEquals(TestState(), vm.snapshot.value.state)

        runCurrent()

        assertEquals(1, definitions)
        assertSame(mainThread, handledOn.get())
        assertEquals(1, vm.snapshot.value.state.value)
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
            vm.snapshot.map { it.state }.distinctUntilChanged().collect { seen += it.value }
        }

        assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Start) }
        assertEquals(listOf(0), seen)
        assertEquals(TestState(), vm.snapshot.value.state)
        vm.ui.onIntent(Stop)
        assertEquals(listOf(0, 2), seen)
    }

    private suspend fun own(
        definition: MviDefinition<TestState, TestIntent, TestEffect>.() -> Unit,
    ): ProbeViewModel = ProbeViewModel(definition).also { store.put("strict-${nextKey++}", it); it.ui = it.uiApi() }

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
        lateinit var ui: MviUiApi<TestState, TestIntent>

        val constructed = true

        override fun MviDefinition<TestState, TestIntent, TestEffect>.define() = definition()
    }
}
