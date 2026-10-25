package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.MviUiApi
import com.zaus.nullwave.core.mvi.runtime.MviRuntime
import com.zaus.nullwave.core.mvi.runtime.effect.EffectEpoch
import com.zaus.nullwave.core.mvi.runtime.effect.EffectMailbox
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManagedEffectDeliveryTest {
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
    fun `more than capacity completed latest requests retain the current effect`() = runTest(dispatcher) {
        val vm = own {
            val search = latest<Int, Int>("search") {
                execute { it }
                onResult { _, value -> copy(value = value) }
                effect { _, value -> Signal(value) }
            }
            on<Run> { submit(search, it.value) }
        }
        repeat(65) {
            vm.ui.onIntent(Run(it))
            runCurrent()
        }
        val received = mutableListOf<Signal>()
        val collector = backgroundScope.launch { vm.collectEffects(received::add) }
        runCurrent()
        collector.cancelAndJoin()

        assertEquals(64, vm.state.value.value)
        assertEquals(listOf(Signal(64)), received)
        assertTrue(collector.isCompleted)
    }

    @Test
    fun `cancelling completed queued effects frees capacity for an independent notification`() = runTest(dispatcher) {
        val accepted = mutableListOf<Boolean>()
        val cancelled = mutableListOf<Boolean>()
        val vm = own {
            val save = queued<Int, Int>("save") {
                execute { it }
                effect { _, value -> Signal(value) }
            }
            on<Run> { submit(save, it.value) }
            on<Cancel> { cancelled += cancel(save) }
            on<Notify> { accepted += tryEffect(Signal(it.value)) }
        }
        repeat(64) {
            vm.ui.onIntent(Run(it))
            runCurrent()
        }
        vm.ui.onIntent(Cancel)
        vm.ui.onIntent(Notify(100))
        val received = mutableListOf<Signal>()
        backgroundScope.launch { vm.collectEffects { received += it } }
        runCurrent()

        assertEquals(listOf(false), cancelled)
        assertEquals(listOf(true), accepted)
        assertEquals(listOf(Signal(100)), received)
    }

    @Test
    fun `compaction preserves valid FIFO across independent owners and unowned signals`() = runTest(dispatcher) {
        val mailbox = EffectMailbox<Signal>()
        val first = EffectEpoch()
        val second = EffectEpoch()
        try {
            repeat(64) { value ->
                val owner = when (value % 3) {
                    0 -> first
                    1 -> second
                    else -> null
                }
                assertTrue(mailbox.offer(Signal(value), owner) { true })
            }
            assertFalse(mailbox.offer(Signal(100), null) { true })
            // Marking a token stale is enough: admission itself compacts, even before notification.
            first.revoke()
            assertTrue(mailbox.offer(Signal(100), null) { true })
            val received = mutableListOf<Signal>()
            backgroundScope.launch { mailbox.collect { received += it } }
            runCurrent()

            assertEquals((0 until 64).filter { it % 3 != 0 }.map(::Signal) + Signal(100), received)
        } finally {
            mailbox.close()
        }
    }

    @Test
    fun `latest revokes active handler and waits for its cleanup without retaining operation work`() = runTest(dispatcher) {
        val cleanup = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        var cleanupStarted = false
        val vm = own {
            val search = latest<Int, Int>("search") {
                execute { it }
                onResult { _, value -> copy(value = value) }
                effect { _, value -> Signal(value) }
            }
            on<Run> { submit(search, it.value) }
        }
        val collector = backgroundScope.launch {
            vm.collectEffects { effect ->
                started += effect.value
                if (effect.value == 1) {
                    try {
                        awaitCancellation()
                    } finally {
                        cleanupStarted = true
                        withContext(NonCancellable) { cleanup.await() }
                    }
                }
            }
        }
        try {
            vm.ui.onIntent(Run(1))
            runCurrent()
            vm.ui.onIntent(Run(2))
            runCurrent()

            assertTrue(cleanupStarted)
            assertTrue(collector.isActive)
            assertEquals(2, vm.state.value.value)
            assertEquals(listOf(1), started)

            cleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf(1, 2), started)
        } finally {
            cleanup.complete(Unit)
        }
    }

    @Test
    fun `explicit cancellation revokes a completed operations active handler even when returning false`() = runTest(dispatcher) {
        val cancellations = mutableListOf<Boolean>()
        var cancelled = false
        val vm = own {
            val save = queued<Int, Int>("save") {
                execute { it }
                effect { _, value -> Signal(value) }
            }
            on<Run> { submit(save, it.value) }
            on<Cancel> { cancellations += cancel(save) }
        }
        backgroundScope.launch {
            vm.collectEffects {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Cancel)
        runCurrent()

        assertEquals(listOf(false), cancellations)
        assertTrue(cancelled)
    }

    @Test
    fun `another owners replacement does not cancel or overtake an active handler`() = runTest(dispatcher) {
        val mailbox = EffectMailbox<Signal>()
        val first = EffectEpoch()
        val second = EffectEpoch()
        val release = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        var firstCancelled = false
        try {
            backgroundScope.launch {
                mailbox.collect { signal ->
                    received += signal.value
                    if (signal.value == 1) {
                        try {
                            release.await()
                        } finally {
                            firstCancelled = !release.isCompleted
                        }
                    }
                }
            }
            mailbox.offer(Signal(1), first) { true }
            mailbox.offer(Signal(2), second) { true }
            runCurrent()
            mailbox.revoke(second)
            mailbox.offer(Signal(3), EffectEpoch()) { true }
            runCurrent()

            assertFalse(firstCancelled)
            assertEquals(listOf(1), received)
            release.complete(Unit)
            runCurrent()
            assertFalse(firstCancelled)
            assertEquals(listOf(1, 3), received)
        } finally {
            release.complete(Unit)
            mailbox.close()
        }
    }

    @Test
    fun `consumer stop retains lease until cleanup completes then reconnect drains only pending signals`() = runTest(dispatcher) {
        val mailbox = EffectMailbox<Signal>()
        val cleanup = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val consumer = backgroundScope.launch {
            mailbox.collect {
                received += it.value
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { cleanup.await() }
                }
            }
        }
        try {
            mailbox.offer(Signal(1), null) { true }
            mailbox.offer(Signal(2), null) { true }
            runCurrent()
            consumer.cancel()
            runCurrent()

            val replacement = backgroundScope.launch { mailbox.collect { received += it.value } }
            runCurrent()
            assertTrue(replacement.isActive)
            assertEquals(listOf(1), received)
            assertFalse(consumer.isCompleted)

            cleanup.complete(Unit)
            runCurrent()
            assertTrue(consumer.isCompleted)
            assertEquals(listOf(1, 2), received)
        } finally {
            cleanup.complete(Unit)
            mailbox.close()
        }
    }

    @Test
    fun `clear cancels active UI handler and discards pending signals while awaiting cleanup`() = runTest(dispatcher) {
        val cleanup = CompletableDeferred<Unit>()
        val runtime = MviRuntime<Snapshot, Signal>(Snapshot(), backgroundScope, observeSources = {})
        var cancelled = false
        val received = mutableListOf<Int>()
        val collector = backgroundScope.launch {
            runtime.collectEffects {
                received += it.value
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                    withContext(NonCancellable) { cleanup.await() }
                }
            }
        }
        try {
            runtime.offerEffect(Signal(1))
            runtime.offerEffect(Signal(2))
            runCurrent()
            runtime.close()
            runCurrent()
            assertTrue(cancelled)
            assertFalse(runtime.offerEffect(Signal(3)))
            assertEquals(listOf(1), received)
            assertFalse(collector.isCompleted)

            cleanup.complete(Unit)
            runCurrent()
            assertTrue(collector.isCompleted)
            val afterClear = backgroundScope.launch { runtime.collectEffects { received += it.value } }
            runCurrent()
            assertTrue(afterClear.isCompleted)
            assertEquals(listOf(1), received)
        } finally {
            cleanup.complete(Unit)
            runtime.close()
        }
    }

    @Test
    fun `managed UI handler may dispatch while effect is published synchronously`() = runTest(dispatcher) {
        val vm = own {
            on<Notify> { tryEffect(Signal(it.value)) }
            on<Run> { reduce { copy(value = it.value) } }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.collectEffects { vm.ui.onIntent(Run(it.value)) }
        }
        vm.ui.onIntent(Notify(42))
        runCurrent()

        assertEquals(42, vm.state.value.value)
    }

    @Test
    fun `execute cannot enter managed UI collection to bypass its operation boundary`() = runTest(dispatcher) {
        lateinit var vm: ProbeViewModel
        var rejected = false
        vm = own {
            val search = latest<Int, Int>("search") {
                execute { input ->
                    try {
                        vm.collectEffects { vm.ui.onIntent(Notify(it.value)) }
                    } catch (_: IllegalStateException) {
                        rejected = true
                    }
                    input
                }
                onResult { _, value -> copy(value = value) }
            }
            on<Run> { submit(search, it.value) }
            on<Notify> { reduce { copy(value = it.value) } }
            on<Cancel> { tryEffect(Signal(100)) }
        }
        vm.ui.onIntent(Cancel)
        vm.ui.onIntent(Run(7))
        runCurrent()

        assertTrue(rejected)
        assertEquals(7, vm.state.value.value)
        val remaining = mutableListOf<Signal>()
        backgroundScope.launch { vm.collectEffects { remaining += it } }
        runCurrent()
        assertEquals(listOf(Signal(100)), remaining)
    }

    @Test
    fun `latest handler cleanup intent runs after the current admission and wins next`() = runTest(dispatcher) {
        val accepted = mutableListOf<Pair<Int, Boolean>>()
        val received = mutableListOf<Int>()
        val vm = own {
            val search = latest<Int, Int>("search") {
                execute { it }
                onResult { _, value -> copy(value = value) }
                effect { _, value -> Signal(value) }
            }
            on<Run> { accepted += it.value to submit(search, it.value) }
        }
        backgroundScope.launch {
            vm.collectEffects { effect ->
                received += effect.value
                if (effect.value == 1) {
                    try {
                        awaitCancellation()
                    } finally {
                        vm.ui.onIntent(Run(3))
                    }
                }
            }
        }
        vm.ui.onIntent(Run(1))
        runCurrent()
        vm.ui.onIntent(Run(2))
        runCurrent()

        assertEquals(3, vm.state.value.value)
        // Run(3) from cleanup waits for Run(2)'s handler. Its synchronous work/effect may finish
        // before the next intent starts; latest does not retroactively revoke delivered actions.
        assertEquals(listOf(1, 2, 3), received)
        assertEquals(listOf(1 to true, 2 to true, 3 to true), accepted)
    }

    @Test
    fun `cancel does not own newer operation started by the revoked UI handlers cleanup`() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val cancellations = mutableListOf<Boolean>()
        val received = mutableListOf<Int>()
        val vm = own {
            val search = latest<Int, Int>("search") {
                execute { input ->
                    if (input == 2) release.await()
                    input
                }
                onResult { _, value -> copy(value = value) }
                effect { _, value -> Signal(value) }
            }
            on<Run> { submit(search, it.value) }
            on<Cancel> { cancellations += cancel(search) }
        }
        backgroundScope.launch {
            vm.collectEffects { effect ->
                received += effect.value
                if (effect.value == 1) {
                    try {
                        awaitCancellation()
                    } finally {
                        vm.ui.onIntent(Run(2))
                    }
                }
            }
        }
        try {
            vm.ui.onIntent(Run(1))
            runCurrent()
            vm.ui.onIntent(Cancel)
            runCurrent()
            assertEquals(listOf(false), cancellations)

            release.complete(Unit)
            runCurrent()
            assertEquals(2, vm.state.value.value)
            assertEquals(listOf(1, 2), received)
        } finally {
            release.complete(Unit)
        }
    }

    private suspend fun own(definition: MviDefinition<Snapshot, Intent, Signal>.() -> Unit): ProbeViewModel =
        ProbeViewModel(definition).also { store.put("managed-effect-${nextKey++}", it); it.ui = it.uiApi() }

    private data class Snapshot(val value: Int = 0) : UiState
    private sealed interface Intent : UiIntent
    private data class Run(val value: Int) : Intent
    private data class Notify(val value: Int) : Intent
    private data object Cancel : Intent
    private data class Signal(val value: Int) : UiEffect

    private class ProbeViewModel(
        private val definition: MviDefinition<Snapshot, Intent, Signal>.() -> Unit,
    ) : BaseMviViewModel<Snapshot, Intent, Signal>(Snapshot()) {
        lateinit var ui: MviUiApi<Snapshot, Intent>

        override fun MviDefinition<Snapshot, Intent, Signal>.define() = definition()
    }
}
