package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StrictMviSourceContractTest {
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
    fun `source cannot change its own input or publish accompanying data`() = runTest(dispatcher) {
        val repository = ControlledSource<Request, Int>()
        val vm = own {
            source("tracks", input = { request }, flow = repository::observe) { _, value ->
                copy(request = request.copy(query = "feedback"), result = value)
            }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()

        repository.requests.single().respond(12)
        runCurrent()

        assertEquals(ScreenState(), vm.snapshot.value.state)
        assertEquals(listOf(Request()), repository.requests.map { it.input })
        assertTrue(repository.failures.single().message.orEmpty().contains("Source 'tracks'"))
        assertTrue(repository.failures.single().message.orEmpty().contains("input of source 'tracks'"))
    }

    @Test
    fun `cross source feedback is rejected before either source revision or state changes`() = runTest(dispatcher) {
        val tracks = ControlledSource<Request, Int>()
        val albums = ControlledSource<Request, Int>()
        val vm = own {
            source("tracks", input = { request }, flow = tracks::observe) { _, value ->
                if (value == 1) copy(relatedRequest = relatedRequest.copy(query = "feedback"), result = value)
                else copy(result = value)
            }
            source("albums", input = { relatedRequest }, flow = albums::observe) { _, value ->
                if (value == 1) copy(request = request.copy(query = "feedback"), relatedResult = value)
                else copy(relatedResult = value)
            }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()

        tracks.requests.single().respond(1)
        runCurrent()
        assertEquals(ScreenState(), vm.snapshot.value.state)
        assertTrue(tracks.failures.single().message.orEmpty().contains("input of source 'albums'"))

        albums.requests.single().respond(1)
        runCurrent()
        assertEquals(ScreenState(), vm.snapshot.value.state)
        assertTrue(albums.failures.single().message.orEmpty().contains("input of source 'tracks'"))

        // The failed publications must not revoke either still-valid subscription.
        tracks.requests.single().respond(20)
        albums.requests.single().respond(30)
        runCurrent()

        assertEquals(ScreenState(result = 20, relatedResult = 30), vm.snapshot.value.state)
        assertEquals(1, tracks.requests.size)
        assertEquals(1, albums.requests.size)
    }

    @Test
    fun `unkeyed source also cannot drive another source input`() = runTest(dispatcher) {
        val preferences = ControlledSource<Unit, Boolean>()
        val tracks = ControlledSource<Request, Int>()
        val vm = own {
            source("preferences", flow = { preferences.observe(Unit) }) { favouritesOnly ->
                copy(request = request.copy(favouritesOnly = favouritesOnly))
            }
            source("tracks", input = { request }, flow = tracks::observe) { _, value ->
                copy(result = value)
            }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()

        preferences.requests.single().respond(true)
        runCurrent()

        assertEquals(ScreenState(), vm.snapshot.value.state)
        assertEquals(1, tracks.requests.size)
        assertTrue(preferences.failures.single().message.orEmpty().contains("Source 'preferences'"))
        assertTrue(preferences.failures.single().message.orEmpty().contains("input of source 'tracks'"))
    }

    @Test
    fun `complete request tracks query sort and filter without restarting for equal input or source data`() = runTest(dispatcher) {
        val repository = ControlledSource<Request, Int>()
        val vm = own {
            source("tracks", input = { request }, flow = repository::observe) { _, value ->
                copy(result = value)
            }
            on<ChangeRequest> { reduce { copy(request = it.request) } }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        val query = Request(query = "new")
        val sort = query.copy(sort = Sort.Artist)
        val filter = sort.copy(favouritesOnly = true)

        listOf(query, sort, filter).forEach {
            vm.ui.onIntent(ChangeRequest(it))
            runCurrent()
        }
        vm.ui.onIntent(ChangeRequest(filter.copy()))
        runCurrent()
        repository.requests.last().respond(42)
        runCurrent()

        assertEquals(listOf(Request(), query, sort, filter), repository.requests.map { it.input })
        assertEquals(ScreenState(request = filter, result = 42), vm.snapshot.value.state)
        assertTrue(repository.failures.isEmpty())
    }

    @Test
    fun `operation result may deliberately change source input`() = runTest(dispatcher) {
        val repository = ControlledSource<Request, Int>()
        val vm = own {
            source("tracks", input = { request }, flow = repository::observe) { _, value ->
                copy(result = value)
            }
            val restore = latest<Request, Request>("restoreQuery") {
                execute { it }
                onResult { _, restored -> copy(request = restored) }
            }
            on<ChangeRequest> { submit(restore, it.request) }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        val restored = Request(query = "restored", sort = Sort.Artist, favouritesOnly = true)

        vm.ui.onIntent(ChangeRequest(restored))
        runCurrent()
        repository.requests.last().respond(7)
        runCurrent()

        assertEquals(listOf(Request(), restored), repository.requests.map { it.input })
        assertEquals(ScreenState(request = restored, result = 7), vm.snapshot.value.state)
        assertTrue(repository.failures.isEmpty())
    }

    @Test
    fun `interface intent registration fails while defining instead of leaving an unreachable handler`() = runTest(dispatcher) {
        var handled = false
        val vm = own {
            on<ScreenIntent> { handled = true }
            on<ChangeRequest> { handled = true }
        }

        val failure = assertThrows(IllegalArgumentException::class.java) {
            vm.ui.onIntent(ChangeRequest(Request()))
        }

        assertTrue(failure.message.orEmpty().contains("ScreenIntent"))
        assertTrue(failure.message.orEmpty().contains("concrete class"))
        assertEquals(false, handled)
    }

    @Test
    fun `abstract intent registration fails while defining`() = runTest(dispatcher) {
        val vm = own { on<AbstractIntent> {} }

        val failure = assertThrows(IllegalArgumentException::class.java) {
            vm.ui.onIntent(ConcreteIntent)
        }

        assertTrue(failure.message.orEmpty().contains("AbstractIntent"))
        assertTrue(failure.message.orEmpty().contains("exact runtime types"))
    }

    private suspend fun own(
        definition: MviDefinition<ScreenState, ScreenIntent, ScreenEffect>.() -> Unit,
    ): ProbeViewModel = ProbeViewModel(definition).also { store.put("source-${nextKey++}", it); it.ui = it.uiApi() }

    private enum class Sort { Title, Artist }

    private data class Request(
        val query: String = "A",
        val sort: Sort = Sort.Title,
        val favouritesOnly: Boolean = false,
    )

    private data class ScreenState(
        val request: Request = Request(),
        val relatedRequest: Request = Request(query = "B"),
        val result: Int = 0,
        val relatedResult: Int = 0,
    ) : UiState

    private sealed interface ScreenIntent : UiIntent
    private data class ChangeRequest(val request: Request) : ScreenIntent
    private abstract class AbstractIntent : ScreenIntent
    private data object ConcreteIntent : AbstractIntent()
    private data object ScreenEffect : UiEffect

    private class ProbeViewModel(
        private val definition: MviDefinition<ScreenState, ScreenIntent, ScreenEffect>.() -> Unit,
    ) : BaseMviViewModel<ScreenState, ScreenIntent, ScreenEffect>(ScreenState(), sourceStopTimeoutMillis = 0) {
        lateinit var ui: MviUiApi<ScreenState, ScreenIntent>

        override fun MviDefinition<ScreenState, ScreenIntent, ScreenEffect>.define() = definition()
    }

    /** Lets a callback-style provider report a rejected emission and keep its subscription alive. */
    private class ControlledSource<K, T> {
        val requests = mutableListOf<RequestStream<K, T>>()
        val failures = mutableListOf<IllegalStateException>()

        fun observe(input: K): Flow<T> = object : Flow<T> {
            override suspend fun collect(collector: FlowCollector<T>) {
                val request = RequestStream<K, T>(input).also(requests::add)
                for (value in request.values) {
                    try {
                        collector.emit(value)
                    } catch (failure: IllegalStateException) {
                        if (failure is CancellationException) throw failure
                        failures += failure
                    }
                }
            }
        }
    }

    private class RequestStream<K, T>(val input: K) {
        val values = Channel<T>(Channel.UNLIMITED)

        fun respond(value: T) {
            check(values.trySend(value).isSuccess)
        }
    }
}
