package com.zaus.nullwave.core.mvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.collectEffectsWithLifecycle
import com.zaus.nullwave.core.mvi.ui.collectStateWithUiBinding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Work ownership follows its coroutine, including children, dispatcher changes and cleanup. */
@OptIn(ExperimentalCoroutinesApi::class)
class MviWorkUiCapabilityTest {
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
    fun `execute cannot acquire own or other UI entries even when the API was cached`() = runTest(dispatcher) {
        var targetDefinitions = 0
        var targetSources = 0
        val target = own {
            targetDefinitions++
            source("target", flow = { flow<Unit> { targetSources++; awaitCancellation() } }) { this }
        }
        val cachedTarget = target.uiApi()
        val completed = CompletableDeferred<Result<List<String>>>()
        lateinit var caller: ProbeViewModel
        caller = own {
            val work = idle<Unit, Unit>("work") {
                execute { completed.complete(runCatching { checkAllContexts(caller, target) }) }
                onResult { _, _ -> copy(value = 1) }
            }
            on<Start> { submit(work, Unit) }
        }
        val cachedCaller = caller.uiApi()

        cachedCaller.onIntent(Start)
        assertEquals(contexts, completed.await().getOrThrow())
        runCurrent()

        assertEquals(1, caller.state.value.value)
        assertEquals(0, targetDefinitions)
        assertEquals(0, targetSources)
        assertSame(cachedCaller, caller.uiApi())
        assertSame(cachedTarget, target.uiApi())
    }

    @Test
    fun `source cannot acquire own or other UI entries across coroutine contexts`() = runTest(dispatcher) {
        var targetDefinitions = 0
        var targetSources = 0
        val target = own {
            targetDefinitions++
            source("target", flow = { flow<Unit> { targetSources++; awaitCancellation() } }) { this }
        }
        val cachedTarget = target.uiApi()
        val completed = CompletableDeferred<Result<List<String>>>()
        lateinit var caller: ProbeViewModel
        caller = own {
            source("data", flow = {
                flow {
                    completed.complete(runCatching { checkAllContexts(caller, target) })
                    emit(1)
                    awaitCancellation()
                }
            }) { copy(value = it) }
        }
        val cachedCaller = caller.uiApi()
        val binding = backgroundScope.launch { caller.withUiBinding { awaitCancellation() } }

        assertEquals(contexts, completed.await().getOrThrow())
        runCurrent()

        assertEquals(1, caller.state.value.value)
        assertEquals(0, targetDefinitions)
        assertEquals(0, targetSources)
        assertSame(cachedCaller, caller.uiApi())
        assertSame(cachedTarget, target.uiApi())
        binding.cancelAndJoin()
    }

    @Test
    fun `cancelled execute cleanup retains its work marker under NonCancellable`() = runTest(dispatcher) {
        var targetDefinitions = 0
        val target = own { targetDefinitions++ }
        target.uiApi()
        val started = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Result<Unit>>()
        lateinit var caller: ProbeViewModel
        caller = own {
            val work = idle<Unit, Unit>("work") {
                execute {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            delay(1)
                            completed.complete(runCatching { checkEntries(caller, target, "cleanup") })
                        }
                    }
                }
            }
            on<Start> { submit(work, Unit) }
            on<Stop> { cancel(work) }
        }
        val ui = caller.uiApi()

        ui.onIntent(Start)
        started.await()
        ui.onIntent(Stop)
        completed.await().getOrThrow()

        assertEquals(0, targetDefinitions)
        assertEquals(Snapshot(), caller.state.value)
    }

    @Test
    fun `work cannot register lifecycle helpers while the owner is only CREATED`() = runTest(dispatcher) {
        var targetDefinitions = 0
        val target = own { targetDefinitions++ }
        val lifecycle = CountingCreatedLifecycle()
        val completed = CompletableDeferred<Result<Unit>>()
        val caller = own {
            val work = idle<Unit, Unit>("work") {
                execute {
                    completed.complete(runCatching {
                        assertRejected("inactive lifecycle state helper") {
                            target.collectStateWithUiBinding(lifecycle) {}
                        }
                        assertRejected("inactive lifecycle effects helper") {
                            target.collectEffectsWithLifecycle(lifecycle) {}
                        }
                    })
                }
            }
            on<Start> { submit(work, Unit) }
        }

        caller.uiApi().onIntent(Start)
        completed.await().getOrThrow()

        assertEquals(0, lifecycle.registrations)
        assertEquals(0, targetDefinitions)
    }

    @Test
    fun `inline execute cannot use the still open captured IntentScope`() = runTest(dispatcher) {
        lateinit var mutate: () -> Unit
        var handlerOpen = false
        var executedInline = false
        var rejected: IllegalStateException? = null
        val caller = own {
            val work = idle<Unit, Unit>("work") {
                execute {
                    executedInline = handlerOpen
                    try {
                        mutate()
                    } catch (failure: IllegalStateException) {
                        rejected = failure
                    }
                }
            }
            on<Start> {
                mutate = { reduce { copy(value = 99) } }
                handlerOpen = true
                submit(work, Unit)
                handlerOpen = false
                reduce { copy(value = 1) }
            }
        }

        caller.uiApi().onIntent(Start)

        assertTrue("The regression must exercise execute before its submitting handler returns", executedInline)
        assertNotNull("An active handler receiver must still be unavailable inside execute", rejected)
        assertEquals(1, caller.state.value.value)
    }

    private suspend fun checkAllContexts(caller: ProbeViewModel, target: ProbeViewModel): List<String> {
        val visited = mutableListOf<String>()
        suspend fun check(label: String) {
            checkEntries(caller, target, label)
            visited += label
        }
        check("before suspension")
        delay(1)
        check("after suspension")
        coroutineScope { launch { check("structured child") }.join() }
        withContext(Dispatchers.Default) { check("different dispatcher") }
        withContext(NonCancellable) { check("NonCancellable") }
        return visited
    }

    private suspend fun checkEntries(caller: ProbeViewModel, target: ProbeViewModel, context: String) {
        for ((name, viewModel) in listOf("own" to caller, "other" to target)) {
            assertRejected("$context / $name UI API") { viewModel.uiApi() }
            assertRejected("$context / $name UI binding") { viewModel.withUiBinding {} }
            assertRejected("$context / $name effect collection") { viewModel.collectEffects {} }
        }
    }

    private suspend fun assertRejected(label: String, action: suspend () -> Unit) {
        var rejected: IllegalStateException? = null
        try {
            // A broken effects guard would otherwise leave the probe collecting indefinitely.
            withTimeout(1_000) { action() }
        } catch (failure: IllegalStateException) {
            rejected = failure
        }
        assertNotNull(label, rejected)
        assertTrue("$label must fail at the UI boundary", rejected!!.message.orEmpty().contains("UI/platform boundary"))
    }

    private fun own(definition: MviDefinition<Snapshot, Intent, Signal>.() -> Unit): ProbeViewModel =
        ProbeViewModel(definition).also { store.put("work-ui-${nextKey++}", it) }

    private data class Snapshot(val value: Int = 0) : UiState
    private sealed interface Intent : UiIntent
    private data object Start : Intent
    private data object Stop : Intent
    private data object Signal : UiEffect
    private class ProbeViewModel(private val definition: MviDefinition<Snapshot, Intent, Signal>.() -> Unit) :
        BaseMviViewModel<Snapshot, Intent, Signal>(Snapshot(), sourceStopTimeoutMillis = 0) {
        override fun MviDefinition<Snapshot, Intent, Signal>.define() = definition()
    }

    private class CountingCreatedLifecycle : Lifecycle() {
        var registrations = 0
            private set
        override val currentState: State = State.CREATED
        override fun addObserver(observer: LifecycleObserver) { registrations++ }
        override fun removeObserver(observer: LifecycleObserver) = Unit
    }

    private val contexts = listOf(
        "before suspension", "after suspension", "structured child", "different dispatcher", "NonCancellable",
    )
}
