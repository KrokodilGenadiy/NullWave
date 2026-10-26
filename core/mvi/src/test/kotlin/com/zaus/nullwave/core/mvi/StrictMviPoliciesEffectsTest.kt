package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StrictMviPoliciesEffectsTest {
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
    fun `latest waits for old cleanup and suppresses its result finalizer and effect`() = runTest(dispatcher) {
        val releaseOld = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        val accepted = mutableListOf<Boolean>()
        val effects = mutableListOf<TestEffect>()
        val vm = own {
            val work = latest<Int, Int>("search") {
                execute { input ->
                    started += input
                    if (input == 1) withContext(NonCancellable) { releaseOld.await() }
                    input * 10
                }
                onAccepted { input -> copy(accepted = input, loading = true) }
                onResult { _, result -> copy(result = result) }
                onFinished { input -> copy(finished = finished + input, loading = false) }
                effect { input, _ -> TestEffect(input) }
            }
            on<Run> { accepted += submit(work, it.value) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.collectEffects { effects += it } }
        vm.ui.onIntent(Run(1))
        runCurrent()

        vm.ui.onIntent(Run(2))
        runCurrent()
        assertEquals(listOf(1), started)
        assertEquals(2, vm.snapshot.value.state.accepted)
        assertTrue(vm.snapshot.value.state.loading)
        releaseOld.complete(Unit)
        runCurrent()

        assertEquals(listOf(true, true), accepted)
        assertEquals(listOf(1, 2), started)
        assertEquals(20, vm.snapshot.value.state.result)
        assertEquals(listOf(2), vm.snapshot.value.state.finished)
        assertFalse(vm.snapshot.value.state.loading)
        assertEquals(listOf(TestEffect(2)), effects)
    }

    @Test
    fun `queue preserves typed input FIFO and rejects overflow before acceptance`() = runTest(dispatcher) {
        val first = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        val accepted = mutableListOf<Boolean>()
        val vm = own {
            val work = queued<Int, Int>("save", maxPending = 1) {
                execute { input ->
                    started += input
                    if (input == 1) first.await()
                    input * 10
                }
                onAccepted { input -> copy(accepted = input) }
                onResult { _, result -> copy(result = result) }
                onFinished { input -> copy(finished = finished + input) }
            }
            on<Run> { accepted += submit(work, it.value) }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Run(2))
        vm.ui.onIntent(Run(3))

        assertEquals(listOf(true, true, false), accepted)
        assertEquals(2, vm.snapshot.value.state.accepted)
        assertEquals(listOf(1), started)
        first.complete(Unit)
        runCurrent()

        assertEquals(listOf(1, 2), started)
        assertEquals(listOf(1, 2), vm.snapshot.value.state.finished)
        assertEquals(20, vm.snapshot.value.state.result)
    }

    @Test
    fun `idle rejects while executing and accepts again after finalization`() = runTest(dispatcher) {
        val first = CompletableDeferred<Unit>()
        val accepted = mutableListOf<Boolean>()
        val vm = own {
            val work = idle<Int, Int>("refresh") {
                execute { input ->
                    if (input == 1) first.await()
                    input
                }
                onAccepted { input -> copy(accepted = input, loading = true) }
                onResult { _, result -> copy(result = result) }
                onFinished { input -> copy(finished = finished + input, loading = false) }
            }
            on<Run> { accepted += submit(work, it.value) }
        }
        vm.ui.onIntent(Run(1))
        vm.ui.onIntent(Run(2))
        runCurrent()
        assertEquals(1, vm.snapshot.value.state.accepted)
        assertTrue(vm.snapshot.value.state.loading)

        first.complete(Unit)
        runCurrent()
        vm.ui.onIntent(Run(3))
        runCurrent()

        assertEquals(listOf(true, false, true), accepted)
        assertEquals(listOf(1, 3), vm.snapshot.value.state.finished)
        assertEquals(3, vm.snapshot.value.state.result)
        assertFalse(vm.snapshot.value.state.loading)
    }

    @Test
    fun `execute self cancellation still applies final state without result or effect`() = runTest(dispatcher) {
        val effects = mutableListOf<TestEffect>()
        val accepted = mutableListOf<Boolean>()
        val vm = own {
            val work = idle<Int, Int>("selfCancel") {
                execute { input ->
                    currentCoroutineContext().cancel()
                    input
                }
                onAccepted { copy(loading = true) }
                onResult { _, result -> copy(result = result) }
                onFinished { input -> copy(finished = finished + input, loading = false) }
                effect { input, _ -> TestEffect(input) }
            }
            on<Run> { accepted += submit(work, it.value) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.collectEffects { effects += it } }

        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Run(2))
        runCurrent()

        assertEquals(listOf(true, true), accepted)
        assertEquals(listOf(1, 2), vm.snapshot.value.state.finished)
        assertEquals(0, vm.snapshot.value.state.result)
        assertFalse(vm.snapshot.value.state.loading)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `property cancellation clears pending loading exactly once without finishing revoked input`() = runTest(dispatcher) {
        val accepted = mutableListOf<Boolean>()
        val cancelled = mutableListOf<Boolean>()
        val vm = own {
            val work = queued<Int, Unit>("save", maxPending = 1) {
                execute { awaitCancellation() }
                onAccepted { input -> copy(accepted = input, loading = true) }
                onFinished { input -> copy(finished = finished + input, loading = false) }
                onCancelled { copy(cancelCount = cancelCount + 1, loading = false) }
            }
            on<Run> { accepted += submit(work, it.value) }
            on<Cancel> { cancelled += cancel(work) }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Run(2))

        vm.ui.onIntent(Cancel)
        vm.ui.onIntent(Cancel)
        runCurrent()

        assertEquals(listOf(true, true), accepted)
        assertEquals(listOf(true, false), cancelled)
        assertEquals(1, vm.snapshot.value.state.cancelCount)
        assertTrue(vm.snapshot.value.state.finished.isEmpty())
        assertFalse(vm.snapshot.value.state.loading)
    }

    @Test
    fun `buffered latest effect is dropped if another request is accepted before collection`() = runTest(dispatcher) {
        val vm = own {
            val work = latest<Int, Int>("search") {
                execute { it }
                onResult { _, result -> copy(result = result) }
                effect { input, _ -> TestEffect(input) }
            }
            on<Run> { submit(work, it.value) }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Run(2))
        runCurrent()
        val effects = mutableListOf<TestEffect>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.collectEffects { effects += it } }
        runCurrent()

        assertEquals(2, vm.snapshot.value.state.result)
        assertEquals(listOf(TestEffect(2)), effects)
    }

    @Test
    fun `completed queued results keep independent buffered effects until property cancellation`() = runTest(dispatcher) {
        val vm = own {
            val work = queued<Int, Int>("save") {
                execute { input -> if (input == 3) awaitCancellation() else input }
                effect { input, _ -> TestEffect(input) }
            }
            on<Run> { submit(work, it.value) }
            on<Cancel> { cancel(work) }
        }
        vm.ui.onIntent(Run(1))
        vm.ui.onIntent(Run(2))
        runCurrent()
        val effects = mutableListOf<TestEffect>()
        val consumer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.collectEffects { effects += it }
        }
        assertEquals(listOf(TestEffect(1), TestEffect(2)), effects)
        consumer.cancel()
        runCurrent()

        vm.ui.onIntent(Run(4))
        runCurrent()
        vm.ui.onIntent(Run(3))
        runCurrent()
        vm.ui.onIntent(Cancel)
        runCurrent()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.collectEffects { effects += it } }
        runCurrent()

        assertEquals(listOf(TestEffect(1), TestEffect(2)), effects)
    }

    @Test
    fun `cancel discards completed buffered notifications even when no work remains`() = runTest(dispatcher) {
        val cancellations = mutableListOf<Boolean>()
        val vm = own {
            val work = queued<Int, Int>("save") {
                execute { it }
                effect { input, _ -> TestEffect(input) }
            }
            on<Run> { submit(work, it.value) }
            on<Cancel> { cancellations += cancel(work) }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()

        vm.ui.onIntent(Cancel)
        val effects = mutableListOf<TestEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.collectEffects { effects += it } }
        runCurrent()
        assertEquals(listOf(false), cancellations)
        assertTrue(effects.isEmpty())

        vm.ui.onIntent(Run(2))
        runCurrent()
        assertEquals(listOf(TestEffect(2)), effects)
    }

    @Test
    fun `full effect buffer cannot retain an idle operation slot`() = runTest(dispatcher) {
        val accepted = mutableListOf<Boolean>()
        val vm = own {
            val work = idle<Int, Int>("save") {
                execute { it }
                onAccepted { copy(loading = true) }
                onResult { _, result -> copy(result = result) }
                onFinished { input -> copy(finished = finished + input, loading = false) }
                effect { input, _ -> TestEffect(input) }
            }
            on<Run> { accepted += submit(work, it.value) }
        }

        repeat(100) {
            vm.ui.onIntent(Run(it))
            runCurrent()
        }

        assertTrue(accepted.all { it })
        assertEquals(100, vm.snapshot.value.state.finished.size)
        assertEquals(99, vm.snapshot.value.state.result)
        assertFalse(vm.snapshot.value.state.loading)
    }

    @Test
    fun `only one effect collector is allowed and cancellation releases ownership`() = runTest(dispatcher) {
        val vm = own { on<Run> { tryEffect(TestEffect(it.value)) } }
        val first = backgroundScope.launch { vm.collectEffects {} }
        runCurrent()

        supervisorScope {
            val second = async {
                try {
                    vm.collectEffects {}
                    null
                } catch (failure: IllegalStateException) {
                    failure
                }
            }
            runCurrent()
            assertTrue(second.await() is IllegalStateException)
        }
        first.cancel()
        runCurrent()
        val effects = mutableListOf<TestEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.collectEffects { effects += it } }
        vm.ui.onIntent(Run(7))
        runCurrent()

        assertEquals(listOf(TestEffect(7)), effects)
    }

    @Test
    fun `effect observer may dispatch while intent offers an immediately delivered signal`() = runTest(dispatcher) {
        val vm = own {
            on<Run> {
                tryEffect(TestEffect(it.value))
                reduce { copy(result = it.value) }
            }
            on<Query> { reduce { copy(query = it.value) } }
        }
        val effects = mutableListOf<TestEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.collectEffects {
                effects += it
                vm.ui.onIntent(Query("from UI effect handler"))
            }
        }

        vm.ui.onIntent(Run(7))
        runCurrent()

        assertEquals(listOf(TestEffect(7)), effects)
        assertEquals("from UI effect handler", vm.snapshot.value.state.query)
        assertEquals(7, vm.snapshot.value.state.result)
    }

    @Test
    fun `result observer may replace completing latest and invalidate its buffered effect`() = runTest(dispatcher) {
        val effects = mutableListOf<TestEffect>()
        val vm = own {
            val work = latest<Int, Int>("search") {
                execute { input -> if (input == 2) awaitCancellation() else input }
                onAccepted { input -> copy(accepted = input, loading = true) }
                onResult { _, result -> copy(result = result) }
                onFinished { input -> copy(finished = finished + input, loading = false) }
                effect { input, _ -> TestEffect(input) }
            }
            on<Run> { submit(work, it.value) }
        }
        var replaced = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.snapshot.map { it.state }.distinctUntilChanged().collect {
                if (it.result == 1 && !replaced) {
                    replaced = true
                    vm.ui.onIntent(Run(2))
                }
            }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.collectEffects { effects += it } }
        runCurrent()

        assertTrue(replaced)
        assertEquals(2, vm.snapshot.value.state.accepted)
        assertTrue(vm.snapshot.value.state.loading)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `keyed source rejects cancellation-ignoring result through an A B A input cycle`() = runTest(dispatcher) {
        val requests = mutableListOf<Pair<String, CompletableDeferred<Int>>>()
        val vm = own {
            source(
                name = "query",
                input = { query },
                flow = { key ->
                    val response = CompletableDeferred<Int>()
                    requests += key to response
                    cancellationIgnoringFlow(response)
                },
                reducer = { _, value -> copy(result = value) },
            )
            on<Query> { reduce { copy(query = it.value) } }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        try {
            assertEquals(listOf("A"), requests.map { it.first })

            vm.ui.onIntent(Query("B"))
            vm.ui.onIntent(Query("A"))
            requests[0].second.complete(1)
            runCurrent()

            assertEquals(0, vm.snapshot.value.state.result)
            assertEquals(listOf("A", "A"), requests.map { it.first })
            requests[1].second.complete(2)
            runCurrent()
            assertEquals(2, vm.snapshot.value.state.result)
        } finally {
            requests.toList().forEach { it.second.complete(-1) }
        }
    }

    @Test
    fun `source restart invalidates late result before starting the replacement`() = runTest(dispatcher) {
        val requests = mutableListOf<CompletableDeferred<Int>>()
        val vm = own {
            val catalogue = source(
                name = "catalogue",
                flow = {
                    CompletableDeferred<Int>().also(requests::add).let(::cancellationIgnoringFlow)
                },
                reducer = { value -> copy(result = value) },
            )
            on<Restart> { refresh(catalogue) }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        try {
            assertEquals(1, requests.size)

            vm.ui.onIntent(Restart)
            requests[0].complete(1)
            runCurrent()

            assertEquals(0, vm.snapshot.value.state.result)
            assertEquals(2, requests.size)
            requests[1].complete(2)
            runCurrent()
            assertEquals(2, vm.snapshot.value.state.result)
        } finally {
            requests.toList().forEach { it.complete(-1) }
        }
    }

    @Test
    fun `source output after last UI binding stops cannot update state`() = runTest(dispatcher) {
        val requests = mutableListOf<CompletableDeferred<Int>>()
        val vm = own {
            val catalogue = source(
                name = "catalogue",
                flow = {
                    CompletableDeferred<Int>().also(requests::add).let(::cancellationIgnoringFlow)
                },
                reducer = { value -> copy(result = value) },
            )
            on<Restart> { refresh(catalogue) }
        }
        val binding = backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        try {
            assertEquals(1, requests.size)
            binding.cancel()
            runCurrent()
            requests[0].complete(1)
            runCurrent()
            assertEquals(0, vm.snapshot.value.state.result)

            backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
            runCurrent()
            assertEquals(2, requests.size)
            requests[1].complete(2)
            runCurrent()
            assertEquals(2, vm.snapshot.value.state.result)
        } finally {
            requests.toList().forEach { it.complete(-1) }
        }
    }

    @Test
    fun `execute cannot acquire UI dispatch to bypass its result contract`() = runTest(dispatcher) {
        lateinit var vm: ProbeViewModel
        var rejected: IllegalStateException? = null
        vm = own {
            val work = latest<Int, Int>("work") {
                execute { input ->
                    try {
                        vm.uiApi().onIntent(Query("illegal side channel"))
                    } catch (failure: IllegalStateException) {
                        rejected = failure
                    }
                    input
                }
                onResult { _, result -> copy(result = result) }
            }
            on<Run> { submit(work, it.value) }
            on<Query> { reduce { copy(query = it.value) } }
        }

        vm.ui.onIntent(Run(4))
        runCurrent()

        assertNotNull(rejected)
        assertEquals("A", vm.snapshot.value.state.query)
        assertEquals(4, vm.snapshot.value.state.result)
    }

    @Test
    fun `observer input runs after the publishing transition and cannot roll back`() = runTest(dispatcher) {
        val observedInputs = mutableListOf<String>()
        val vm = own {
            source(
                name = "query",
                input = { query },
                flow = { key ->
                    observedInputs += key
                    flowOf(key.single().code)
                },
                reducer = { _, result -> copy(result = result) },
            )
            on<Query> { reduce { copy(query = it.value) } }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.snapshot.map { it.state }.distinctUntilChanged().collect {
                if (it.query == "B") vm.ui.onIntent(Query("C"))
            }
        }

        vm.ui.onIntent(Query("B"))
        runCurrent()

        assertEquals("C", vm.snapshot.value.state.query)
        assertEquals('C'.code, vm.snapshot.value.state.result)
        // B is a complete transition before the queued C intent, so its synchronous source may run.
        assertEquals(listOf("A", "B", "C"), observedInputs)
    }

    private suspend fun own(
        definition: MviDefinition<TestState, TestIntent, TestEffect>.() -> Unit,
    ): ProbeViewModel = ProbeViewModel(definition).also { store.put("policy-${nextKey++}", it); it.ui = it.uiApi() }

    /** Models a callback adapter that ignores cooperative cancellation, without Flow builder checks. */
    private fun cancellationIgnoringFlow(response: CompletableDeferred<Int>): Flow<Int> = object : Flow<Int> {
        override suspend fun collect(collector: FlowCollector<Int>) {
            withContext(NonCancellable) { collector.emit(response.await()) }
        }
    }

    private data class TestState(
        val accepted: Int = 0,
        val result: Int = 0,
        val loading: Boolean = false,
        val finished: List<Int> = emptyList(),
        val cancelCount: Int = 0,
        val query: String = "A",
    ) : UiState

    private sealed interface TestIntent : UiIntent
    private data class Run(val value: Int) : TestIntent
    private data object Cancel : TestIntent
    private data object Restart : TestIntent
    private data class Query(val value: String) : TestIntent
    private data class TestEffect(val value: Int) : UiEffect

    private class ProbeViewModel(
        private val definition: MviDefinition<TestState, TestIntent, TestEffect>.() -> Unit,
    ) : BaseMviViewModel<TestState, TestIntent, TestEffect>(
        initialState = TestState(),
        sourceStopTimeoutMillis = 0,
    ) {
        lateinit var ui: MviUiApi<TestState, TestIntent>

        override fun MviDefinition<TestState, TestIntent, TestEffect>.define() = definition()
    }
}
