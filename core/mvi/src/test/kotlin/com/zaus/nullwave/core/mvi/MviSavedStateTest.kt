package com.zaus.nullwave.core.mvi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.persistence.MviSavedState
import com.zaus.nullwave.core.mvi.persistence.SavedStateStore
import com.zaus.nullwave.core.mvi.persistence.jsonSavedState
import com.zaus.nullwave.core.mvi.persistence.savedState
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Encoder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MviSavedStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val owners = ViewModelStore()
    private var nextKey = 0

    @Before fun setUp() = Dispatchers.setMain(ImmediateTestMainDispatcher(dispatcher))
    @After fun tearDown() {
        owners.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `restore precedes first State read but does not define collect or write`() = runTest(dispatcher) {
        val storage = Storage(mutableMapOf("inputs" to """{"version":1,"payload":"saved"}"""))
        var definitions = 0
        var collections = 0
        val vm = own(binding(storage)) {
            definitions++
            source("data", flow = { collections++; flowOf(1) }) { copy(result = it) }
            on<Edit> { reduce { copy(text = it.text) } }
        }
        assertEquals(Snapshot("saved"), vm.snapshot.value.state)
        assertEquals(0, definitions)
        assertEquals(0, collections)
        assertEquals(0, storage.writes)
    }

    @Test
    fun `accepted inputs are saved before State observers and without a UI binding`() = runTest(dispatcher) {
        val storage = Storage()
        val vm = own(binding(storage)) { on<Edit> { reduce { copy(text = it.text) } } }
        val observed = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.snapshot.map { it.state }.distinctUntilChanged().collect { state ->
                if (state.text.isNotEmpty()) {
                    assertEquals(state.text, restored(storage).text)
                    observed += state.text
                }
            }
        }
        vm.ui.onIntent(Edit("one"))
        vm.ui.onIntent(Edit("two"))
        assertEquals(listOf("one", "two"), observed)
        assertEquals(Snapshot("two"), restored(storage))
    }

    @Test
    fun `equal payload and transient changes skip encoding and writing`() = runTest(dispatcher) {
        val storage = Storage()
        var encodes = 0
        val codec = object : KSerializer<String> by String.serializer() {
            override fun serialize(encoder: Encoder, value: String) {
                encodes++
                encoder.encodeString(value)
            }
        }
        val vm = own(binding(storage, codec)) {
            on<Edit> { reduce { copy(text = it.text) } }
            on<Tick> { reduce { copy(result = result + 1) } }
        }
        vm.ui.onIntent(Edit("one")) // Initial checkpoint, then the accepted input.
        repeat(3) { vm.ui.onIntent(Tick); vm.ui.onIntent(Edit("one")) }
        assertEquals(2, storage.writes)
        assertEquals(2, encodes)
        assertEquals(3, vm.snapshot.value.state.result)
        assertEquals(Snapshot("one"), restored(storage))
    }

    @Test
    fun `reducer validator selector and encoding failures leave old State storage and source valid`() = runTest(dispatcher) {
        val storage = Storage()
        val response = MutableSharedFlow<Int>()
        var subscriptions = 0
        val failure = IllegalArgumentException("rejected")
        val codec = object : KSerializer<String> by String.serializer() {
            override fun serialize(encoder: Encoder, value: String) {
                if (value == "codec") throw failure
                encoder.encodeString(value)
            }
        }
        val vm = own(binding(storage, codec)) {
            validateState { if (it.text == "validator") throw failure }
            val data = source("data", input = { text }, flow = { subscriptions++; response }) { _, value -> copy(result = value) }
            source("last selector", input = { if (text == "selector") throw failure; Unit }, flow = { flowOf(Unit) }) { _, _ -> this }
            on<Edit> { refresh(data) { copy(text = it.text) } }
            on<Tick> { reduce { throw failure } }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        assertSame(failure, assertThrows(IllegalArgumentException::class.java) { vm.ui.onIntent(Tick) })
        for (input in listOf("validator", "selector", "codec")) {
            assertSame(failure, assertThrows(IllegalArgumentException::class.java) { vm.ui.onIntent(Edit(input)) })
        }
        response.emit(7)
        runCurrent()
        assertEquals(Snapshot(result = 7), vm.snapshot.value.state)
        assertEquals(Snapshot(), restored(storage))
        assertEquals(1, storage.writes)
        assertEquals(1, subscriptions)
    }

    @Test
    fun `source and operation reducers use the same persistence boundary`() = runTest(dispatcher) {
        val storage = Storage()
        val output = MutableSharedFlow<String>()
        val vm = own(binding(storage)) {
            source("input", flow = { output }) { copy(text = it) }
            val work = latest<String, String>("work") {
                execute { it.uppercase() }
                onResult { _, result -> copy(text = result) }
            }
            on<Edit> { submit(work, it.text) }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        output.emit("source")
        runCurrent()
        assertEquals("source", restored(storage).text)
        vm.ui.onIntent(Edit("operation"))
        runCurrent()
        assertEquals("OPERATION", restored(storage).text)
    }

    @Test
    fun `clear prevents late operation output from saving and drops later intents`() = runTest(dispatcher) {
        val storage = Storage()
        val response = CompletableDeferred<String>()
        val vm = own(binding(storage)) {
            val work = latest<String, String>("work") {
                onAccepted { copy(text = it) }
                execute { withContext(NonCancellable) { response.await() } }
                onResult { _, result -> copy(text = result) }
            }
            on<Edit> { submit(work, it.text) }
        }
        vm.ui.onIntent(Edit("accepted"))
        runCurrent()
        owners.clear()
        response.complete("late")
        runCurrent()
        vm.ui.onIntent(Edit("after clear"))
        assertEquals("accepted", restored(storage).text)
        assertEquals("accepted", vm.snapshot.value.state.text)
    }

    @Test
    fun `missing malformed wrong type and unknown version use defaults without stale legacy fallback`() = runTest(dispatcher) {
        for (raw in listOf(null, 123, "broken", "[]", """{"version":2,"payload":"future"}""",
            """{"version":"1","payload":"wrong version type"}""", """{"version":1,"payload":{}}""")) {
            var legacyCalls = 0
            val storage = Storage(mutableMapOf("inputs" to raw, "old" to "stale"))
            val adapter = jsonSavedState<Snapshot, String>(storage, "inputs", String.serializer(),
                select = { text }, restore = { copy(text = it) }, legacy = { legacyCalls++; string("old") })
            val vm = own(adapter) { on<Tick> {} }
            assertEquals(Snapshot(), vm.snapshot.value.state)
            assertEquals(0, legacyCalls)
        }
        assertEquals(Snapshot(), restored(Storage()))
    }

    @Test
    fun `legacy migrates into one versioned payload and current payload wins over old keys`() = runTest(dispatcher) {
        val storage = Storage(mutableMapOf("old" to "legacy"))
        val adapter = jsonSavedState<Snapshot, String>(storage, "inputs", String.serializer(),
            select = { text }, restore = { copy(text = it) }, legacy = { string("old") })
        val vm = own(adapter) { on<Tick> {} }
        assertEquals("legacy", vm.snapshot.value.state.text)
        assertTrue(storage.contains("old"))
        vm.ui.onIntent(Tick)
        assertEquals(setOf("inputs"), storage.values.keys)
        assertEquals("legacy", restored(storage).text)
        storage.values["old"] = "stale"
        val next = own(jsonSavedState<Snapshot, String>(storage, "inputs", String.serializer(),
            select = { text }, restore = { copy(text = it) }, legacy = { string("old") })) { on<Tick> {} }
        assertEquals("legacy", next.snapshot.value.state.text)
    }

    @Test
    fun `handle observer intents during initialization and save wait for the full transition`() = runTest(dispatcher) {
        val storage = Storage()
        val seen = mutableListOf<String>()
        val vm = own(binding(storage)) {
            on<Edit> { reduce { copy(text = it.text) }; seen += state.text }
        }
        storage.afterWrite = { if (restored(storage).text == "first") vm.ui.onIntent(Edit("second")) }
        vm.ui.onIntent(Edit("first"))
        assertEquals(listOf("first", "second"), seen)
        assertEquals("second", restored(storage).text)

        val initial = Storage()
        val other = own(binding(initial)) { on<Edit> { reduce { copy(text = it.text) } } }
        initial.afterWrite = { if (restored(initial).text.isEmpty()) other.ui.onIntent(Edit("from observer")) }
        backgroundScope.launch { other.withUiBinding { awaitCancellation() } }
        runCurrent()
        assertEquals("from observer", other.snapshot.value.state.text)
    }

    @Test
    fun `clear from storage observer prevents subsequent publication or handler entry`() = runTest(dispatcher) {
        val storage = Storage()
        var entries = 0
        val vm = own(binding(storage)) { on<Edit> { entries++; reduce { copy(text = it.text) } } }
        storage.afterWrite = { owners.clear() }
        vm.ui.onIntent(Edit("never handled"))
        vm.ui.onIntent(Edit("also ignored"))
        assertEquals(0, entries)
        assertEquals(Snapshot(), vm.snapshot.value.state)
        assertEquals(1, storage.writes)
    }

    @Test
    fun `clear during an accepted write prevents State publication and source restart`() = runTest(dispatcher) {
        val storage = Storage()
        var collections = 0
        val vm = own(binding(storage)) {
            val data = source("data", input = { text }, flow = { collections++; flowOf(1) }) { _, value ->
                copy(result = value)
            }
            on<Edit> { refresh(data) { copy(text = it.text) } }
        }
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        runCurrent()
        storage.afterWrite = { owners.clear() }
        vm.ui.onIntent(Edit("written before clear"))
        runCurrent()
        assertEquals(Snapshot(result = 1), vm.snapshot.value.state)
        assertEquals(1, collections)
        assertEquals("written before clear", restored(storage).text)
        val writes = storage.writes
        vm.ui.onIntent(Edit("ignored"))
        assertEquals(writes, storage.writes)
    }

    @Test
    fun `write failure stops the runtime without publishing or allowing another write`() = runTest(dispatcher) {
        val storage = Storage()
        val failure = IllegalStateException("storage observer failed after write")
        val vm = own(binding(storage)) { on<Edit> { reduce { copy(text = it.text) } } }
        vm.ui.onIntent(Edit("before"))
        storage.afterWrite = { throw failure }
        assertSame(failure, assertThrows(IllegalStateException::class.java) { vm.ui.onIntent(Edit("failed")) })
        val writes = storage.writes
        assertEquals("before", vm.snapshot.value.state.text)
        // A failed write is not falsely advertised as an atomic rollback of an external object.
        assertEquals("failed", restored(storage).text)
        vm.ui.onIntent(Edit("ignored"))
        assertEquals(writes, storage.writes)
    }

    @Test
    fun `one adapter cannot be shared across owners`() = runTest(dispatcher) {
        val adapter = binding(Storage())
        own(adapter) { on<Tick> {} }
        assertThrows(IllegalStateException::class.java) { ProbeVm(adapter) { on<Tick> {} } }
    }

    @Test
    fun `real handle preserves Unicode input through a new VM with fresh defaults`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        fun adapter(target: SavedStateHandle) = savedState<Snapshot, String>(target, "inputs", String.serializer(),
            select = { text }, restore = { copy(text = it) })
        val vm = own(adapter(handle)) { on<Edit> { reduce { copy(text = it.text, result = 42) } } }
        vm.ui.onIntent(Edit("  Ёлка \"🌊\"\n"))
        val copy = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        owners.clear()
        val next = own(adapter(copy)) { on<Tick> {} }
        assertEquals(Snapshot("  Ёлка \"🌊\"\n"), next.snapshot.value.state)
    }

    private fun binding(storage: Storage, codec: KSerializer<String> = String.serializer()) =
        jsonSavedState<Snapshot, String>(storage, "inputs", codec, select = { text }, restore = { copy(text = it) })

    private fun restored(storage: Storage) = binding(storage).attach(Snapshot()).initialState

    private suspend fun own(adapter: MviSavedState<Snapshot>, definition: MviDefinition<Snapshot, Intent, Nothing>.() -> Unit) =
        ProbeVm(adapter, definition).also { owners.put("vm-${nextKey++}", it); it.ui = it.uiApi() }

    private class Storage(val values: MutableMap<String, Any?> = mutableMapOf()) : SavedStateStore {
        var writes = 0
        var afterWrite: () -> Unit = {}
        override fun contains(key: String) = values.containsKey(key)
        override fun get(key: String) = values[key]
        override fun set(key: String, value: String) { values[key] = value; writes++; afterWrite() }
        override fun remove(key: String) { values.remove(key) }
    }

    private data class Snapshot(val text: String = "", val result: Int = 0) : UiState
    private sealed interface Intent : UiIntent
    private data class Edit(val text: String) : Intent
    private data object Tick : Intent
    private class ProbeVm(
        adapter: MviSavedState<Snapshot>,
        private val definition: MviDefinition<Snapshot, Intent, Nothing>.() -> Unit,
    ) : BaseMviViewModel<Snapshot, Intent, Nothing>(Snapshot(), sourceStopTimeoutMillis = 0, savedState = adapter) {
        lateinit var ui: MviUiApi<Snapshot, Intent>

        override fun MviDefinition<Snapshot, Intent, Nothing>.define() = definition()
    }
}
