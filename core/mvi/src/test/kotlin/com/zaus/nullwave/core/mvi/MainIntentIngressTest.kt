package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainIntentIngressTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextId = 0

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
    fun `a later Main intent cannot overtake a completed worker submission`() = runTest(dispatcher) {
        val executed = mutableListOf<Int>()
        val vm = own {
            val load = latest<Int, Int>("load") {
                execute { executed += it; it }
                onResult { _, value -> copy(value = value) }
            }
            on<Set> { submit(load, it.value) }
        }

        onWorker { vm.ui.onIntent(Set(1)) }
        vm.ui.onIntent(Set(2))
        assertEquals(0, vm.snapshot.value.state.value)

        runCurrent()

        assertEquals(listOf(1, 2), executed)
        assertEquals(2, vm.snapshot.value.state.value)
    }

    @Test
    fun `Main retains inline handling after a worker queue is drained`() = runTest(dispatcher) {
        val handled = mutableListOf<Int>()
        val vm = own {
            on<Set> {
                handled += it.value
                reduce { copy(value = it.value) }
            }
        }

        onWorker { vm.ui.onIntent(Set(1)) }
        onWorker { vm.ui.onIntent(Set(2)) }
        vm.ui.onIntent(Set(3))
        runCurrent()
        vm.ui.onIntent(Set(4))

        assertEquals(listOf(1, 2, 3, 4), handled)
        assertEquals(4, vm.snapshot.value.state.value)
    }

    @Test
    fun `synchronous platform callback from another VM operation may dispatch`() = runTest(dispatcher) {
        val target = own { on<Set> { reduce { copy(value = it.value) } } }
        val platform = SynchronousPlatformEvents { target.ui.onIntent(Set(7)) }
        val source = own {
            val changePlatform = idle<Unit, Unit>("platform") {
                execute { platform.update() }
            }
            on<Set> { submit(changePlatform, Unit) }
        }

        source.ui.onIntent(Set(0))
        runCurrent()

        assertEquals(7, target.snapshot.value.state.value)
    }

    @Test
    fun `work cannot acquire its own UI dispatch capability`() = runTest(dispatcher) {
        lateinit var vm: ProbeVm
        var rejected = false
        vm = own {
            val work = idle<Unit, Unit>("work") {
                execute {
                    rejected = runCatching { vm.uiApi().onIntent(Set(7)) }.exceptionOrNull() is IllegalStateException
                }
            }
            on<Set> { submit(work, Unit) }
        }

        vm.ui.onIntent(Set(0))
        runCurrent()

        assertTrue(rejected)
        assertEquals(0, vm.snapshot.value.state.value)
    }

    @Test
    fun `captured UI callbacks can cross VM operations without guessing caller provenance`() = runTest(dispatcher) {
        lateinit var source: ProbeVm
        var callbackFailure: Throwable? = null
        val target = own {
            on<Set> {
                callbackFailure = runCatching { source.ui.onIntent(Set(7)) }.exceptionOrNull()
                reduce { copy(value = it.value) }
            }
        }
        source = own {
            val work = idle<Unit, Unit>("work") {
                execute { target.ui.onIntent(Set(1)) }
            }
            on<Set> {
                if (it.value == 0) submit(work, Unit) else reduce { copy(value = it.value) }
            }
        }

        source.ui.onIntent(Set(0))
        runCurrent()

        assertNull(callbackFailure)
        assertEquals(7, source.snapshot.value.state.value)
        assertEquals(1, target.snapshot.value.state.value)
    }

    @Test
    fun `a nested different VM handler cannot use a captured outer intent scope`() = runTest(dispatcher) {
        lateinit var mutateOuter: () -> Unit
        var rejected = false
        val target = own {
            on<Set> {
                rejected = runCatching { mutateOuter() }.exceptionOrNull() is IllegalStateException
            }
        }
        val source = own {
            on<Set> {
                mutateOuter = { reduce { copy(value = 99) } }
                target.ui.onIntent(Set(1))
                reduce { copy(value = it.value) }
            }
        }

        source.ui.onIntent(Set(2))

        assertTrue(rejected)
        assertEquals(2, source.snapshot.value.state.value)
    }

    @Test
    fun `a nested different VM definition cannot configure an outer open operation builder`() = runTest(dispatcher) {
        lateinit var configureOuter: () -> Unit
        var rejected = false
        val target = own {
            rejected = runCatching { configureOuter() }.exceptionOrNull() is IllegalStateException
            on<Set> {}
        }
        val source = own {
            val work = idle<Unit, Unit>("work") {
                configureOuter = { execute {} }
                target.ui.onIntent(Set(1))
                execute {}
            }
            on<Set> { submit(work, Unit) }
        }

        source.ui.onIntent(Set(0))
        runCurrent()

        assertTrue(rejected)
    }

    @Test
    fun `cleared VM ignores Main and worker callbacks consistently`() = runTest(dispatcher) {
        val vm = own { on<Set> { reduce { copy(value = it.value) } } }
        vm.ui.onIntent(Set(1))
        store.clear()

        vm.ui.onIntent(Set(2))
        onWorker { vm.ui.onIntent(Set(3)) }
        runCurrent()

        assertEquals(1, vm.snapshot.value.state.value)
    }

    @Test
    fun `clear ignores a synchronous callback from operation cancellation cleanup`() = runTest(dispatcher) {
        lateinit var vm: ProbeVm
        var cleanupRan = false
        var callbackFailure: Throwable? = null
        vm = own {
            val work = idle<Unit, Unit>("work") {
                execute {
                    try {
                        awaitCancellation()
                    } finally {
                        cleanupRan = true
                        callbackFailure = runCatching { vm.ui.onIntent(Set(7)) }.exceptionOrNull()
                    }
                }
            }
            on<Set> {
                if (it.value == 0) submit(work, Unit) else reduce { copy(value = it.value) }
            }
        }
        vm.ui.onIntent(Set(0))
        runCurrent()

        store.clear()
        runCurrent()

        assertTrue(cleanupRan)
        assertNull(callbackFailure)
        assertEquals(0, vm.snapshot.value.state.value)
    }

    @Test
    fun `clearing before queued ingress drops it without initializing the definition`() = runTest(dispatcher) {
        var definitions = 0
        val vm = own {
            definitions++
            on<Set> { reduce { copy(value = it.value) } }
        }
        onWorker { vm.ui.onIntent(Set(1)) }

        store.clear()
        vm.ui.onIntent(Set(2))
        runCurrent()

        assertEquals(0, definitions)
        assertEquals(0, vm.snapshot.value.state.value)
    }

    @Test
    fun `definition failure remains sticky until the VM is cleared`() = runTest(dispatcher) {
        val failure = IllegalStateException("Invalid definition")
        val vm = own { throw failure }

        assertSame(failure, assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Set(1)) })
        assertSame(failure, assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Set(2)) })

        store.clear()
        vm.ui.onIntent(Set(3))
        onWorker { vm.ui.onIntent(Set(4)) }
    }

    private fun onWorker(block: () -> Unit) {
        val failure = AtomicReference<Throwable>()
        val worker = Thread {
            try {
                block()
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        worker.start()
        worker.join()
        assertNull(failure.get())
    }

    private suspend fun own(definition: MviDefinition<Snapshot, Set, Nothing>.() -> Unit): ProbeVm =
        ProbeVm(definition).also { store.put("main-ingress-${nextId++}", it); it.ui = it.uiApi() }

    private data class Snapshot(val value: Int = 0) : UiState
    private data class Set(val value: Int) : UiIntent
    private class ProbeVm(
        private val definition: MviDefinition<Snapshot, Set, Nothing>.() -> Unit,
    ) : BaseMviViewModel<Snapshot, Set, Nothing>(Snapshot()) {
        lateinit var ui: MviUiApi<Snapshot, Set>

        override fun MviDefinition<Snapshot, Set, Nothing>.define() = definition()
    }
    private class SynchronousPlatformEvents(private val listener: () -> Unit) {
        fun update() = listener()
    }
}
