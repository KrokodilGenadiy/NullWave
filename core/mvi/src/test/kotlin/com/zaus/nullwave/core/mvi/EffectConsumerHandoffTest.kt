package com.zaus.nullwave.core.mvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.runtime.effect.EffectEpoch
import com.zaus.nullwave.core.mvi.runtime.effect.EffectMailbox
import com.zaus.nullwave.core.mvi.ui.collectEffectsWithLifecycle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A retained VM coordinates consumers even when the UI hosts do not share a Compose slot. */
@OptIn(ExperimentalCoroutinesApi::class)
class EffectConsumerHandoffTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(AndroidLikeTestMainDispatcher(dispatcher))
    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `new lifecycle host waits for cancelled consumer then receives only pending effects`() = runTest(dispatcher) {
        val vm = vm()
        val ui = vm.uiApi()
        val first = Owner()
        val second = Owner()
        val cleanup = CompletableDeferred<Unit>()
        val seen = mutableListOf<String>()
        val old = backgroundScope.launch(Dispatchers.Main.immediate) {
            vm.collectEffectsWithLifecycle(first.lifecycle) {
                seen += "old:${it.value}"
                try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
            }
        }
        try {
            ui.onIntent(Notify(1))
            runCurrent()
            first.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            assertFalse(old.isCompleted)
            val replacement = backgroundScope.launch(Dispatchers.Main.immediate) {
                vm.collectEffectsWithLifecycle(second.lifecycle) { seen += "new:${it.value}" }
            }
            ui.onIntent(Notify(2))
            runCurrent()
            assertEquals(listOf("old:1"), seen)
            assertTrue(replacement.isActive)

            cleanup.complete(Unit)
            runCurrent()
            assertTrue(old.isCompleted)
            assertEquals(listOf("old:1", "new:2"), seen)
            assertEquals(0, first.registry.observerCount)
            replacement.cancelAndJoin()
            assertEquals(0, second.registry.observerCount)
        } finally { cleanup.complete(Unit) }
    }

    @Test
    fun `cancelling a waiting host leaves pending signals for the next replacement`() = runTest(dispatcher) {
        val vm = vm()
        val ui = vm.uiApi()
        val cleanup = CompletableDeferred<Unit>()
        val old = backgroundScope.launch(Dispatchers.Main.immediate) {
            vm.collectEffects {
                try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
            }
        }
        try {
            ui.onIntent(Notify(1))
            runCurrent()
            old.cancel()
            val cancelledWaiter = backgroundScope.launch(Dispatchers.Main.immediate) {
                vm.collectEffects { error("Cancelled host must not consume") }
            }
            ui.onIntent(Notify(2))
            runCurrent()
            cancelledWaiter.cancelAndJoin()
            assertFalse(old.isCompleted)

            val received = mutableListOf<Int>()
            val next = backgroundScope.launch(Dispatchers.Main.immediate) {
                vm.collectEffects { received += it.value }
            }
            cleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf(2), received)
            assertTrue(next.isActive)
        } finally { cleanup.complete(Unit) }
    }

    @Test
    fun `clear releases a waiting host before old cleanup finishes without delivering pending effects`() = runTest(dispatcher) {
        val vm = vm()
        val ui = vm.uiApi()
        val cleanup = CompletableDeferred<Unit>()
        val old = backgroundScope.launch(Dispatchers.Main.immediate) {
            vm.collectEffects {
                try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
            }
        }
        try {
            ui.onIntent(Notify(1))
            runCurrent()
            old.cancel()
            var returned = false
            val replacement = backgroundScope.launch(Dispatchers.Main.immediate) {
                vm.collectEffects { error("Clear must discard pending effects") }
                returned = true
            }
            ui.onIntent(Notify(2))
            runCurrent()
            assertFalse(returned)

            store.clear()
            runCurrent()
            assertTrue(returned)
            assertTrue(replacement.isCompleted)
            assertFalse(replacement.isCancelled)
            assertFalse(old.isCompleted)
            cleanup.complete(Unit)
            old.join()
        } finally { cleanup.complete(Unit) }
    }

    @Test
    fun `a second active collector is rejected rather than silently queued`() = runTest(dispatcher) {
        val vm = vm()
        val received = mutableListOf<Int>()
        val first = backgroundScope.launch(Dispatchers.Main.immediate) {
            vm.collectEffects { received += it.value }
        }
        val failure = runCatching { vm.collectEffects {} }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("MVI effects allow only one active collector.", failure?.message)
        vm.uiApi().onIntent(Notify(7))
        runCurrent()
        assertEquals(listOf(7), received)
        first.cancelAndJoin()
    }

    @Test
    fun `two replacements cannot both acquire the lease after one cleanup`() = runTest(dispatcher) {
        val vm = vm()
        val ui = vm.uiApi()
        val cleanup = CompletableDeferred<Unit>()
        val old = backgroundScope.launch(Dispatchers.Main.immediate) {
            vm.collectEffects {
                try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
            }
        }
        try {
            ui.onIntent(Notify(1))
            runCurrent()
            old.cancel()
            val received = mutableListOf<Int>()
            val replacements = List(2) {
                backgroundScope.async(Dispatchers.Main.immediate) {
                    runCatching { vm.collectEffects { signal -> received += signal.value } }.exceptionOrNull()
                }
            }
            runCurrent()
            assertTrue(replacements.all { it.isActive })
            cleanup.complete(Unit)
            runCurrent()
            val rejected = replacements.single { it.isCompleted }
            assertTrue(rejected.await() is IllegalStateException)
            val accepted = replacements.single { it.isActive }
            ui.onIntent(Notify(2))
            runCurrent()
            assertEquals(listOf(2), received)
            accepted.cancelAndJoin()
        } finally { cleanup.complete(Unit) }
    }

    @Test
    fun `effects revoked while a replacement waits are skipped after handoff`() = runTest(dispatcher) {
        val mailbox = EffectMailbox<Int>()
        val cleanup = CompletableDeferred<Unit>()
        val epoch = EffectEpoch()
        val old = backgroundScope.launch(Dispatchers.Main.immediate) {
            mailbox.collect {
                try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
            }
        }
        try {
            mailbox.offer(1, null) { true }
            runCurrent()
            old.cancel()
            val seen = mutableListOf<Int>()
            backgroundScope.launch(Dispatchers.Main.immediate) { mailbox.collect { seen += it } }
            mailbox.offer(2, epoch) { true }
            mailbox.revoke(epoch)
            mailbox.offer(3, null) { true }
            cleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf(3), seen)
        } finally {
            cleanup.complete(Unit)
            mailbox.close()
        }
    }

    @Test
    fun `cancelled handler cannot wait for its own consumer from cleanup even through another VM`() = runTest(dispatcher) {
        val first = vm()
        val second = Vm().also { store.put("other", it) }
        val firstUi = first.uiApi()
        second.uiApi().onIntent(Notify(9))
        val failures = mutableListOf<Throwable?>()
        val collector = backgroundScope.launch(Dispatchers.Main.immediate) {
            first.collectEffects {
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) {
                        failures += runCatching {
                            withTimeout(1_000) { first.collectEffects {} }
                        }.exceptionOrNull()
                        val handled = CompletableDeferred<Unit>()
                        val nested = launch {
                            second.collectEffects {
                                failures += runCatching {
                                    withTimeout(1_000) { first.collectEffects {} }
                                }.exceptionOrNull()
                                handled.complete(Unit)
                            }
                        }
                        handled.await()
                        nested.cancelAndJoin()
                    }
                }
            }
        }
        firstUi.onIntent(Notify(1))
        runCurrent()
        collector.cancelAndJoin()
        assertEquals(2, failures.size)
        failures.forEach {
            assertEquals("Must reject at admission rather than time out", IllegalStateException::class.java, it?.javaClass)
            assertEquals("MVI effects allow only one active collector.", it?.message)
        }
    }

    private fun vm() = Vm().also { store.put("screen", it) }
    private data object Snapshot : UiState
    private data class Notify(val value: Int) : UiIntent
    private data class Signal(val value: Int) : UiEffect
    private class Vm : BaseMviViewModel<Snapshot, Notify, Signal>(Snapshot) {
        override fun MviDefinition<Snapshot, Notify, Signal>.define() {
            on<Notify> { tryEffect(Signal(it.value)) }
        }
    }
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
}
