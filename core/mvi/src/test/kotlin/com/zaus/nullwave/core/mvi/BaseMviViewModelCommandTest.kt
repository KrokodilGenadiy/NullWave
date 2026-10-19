package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.runtime.operation.OperationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseMviViewModelCommandTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextKey = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `state is synchronous and stable without a UI collector`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())
        val publicState = vm.state

        vm.onIntent(CommandIntent.Edit("first"))
        assertEquals("first", publicState.value.draft)
        assertEquals(publicState.value, vm.snapshot)

        vm.onIntent(CommandIntent.Edit("second"))
        assertSame(publicState, vm.state)
        assertEquals("second", vm.state.value.draft)
        assertEquals(0, vm.sourceStarts)
    }

    @Test
    fun `two immediate save intents cannot pass the same idle operation reservation`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())

        vm.onIntent(CommandIntent.Save)
        vm.onIntent(CommandIntent.Save)

        assertTrue(vm.state.value.saving)
        // Commands have not run yet. The idle property must already reserve its slot.
        assertEquals(0, vm.saveCalls)
        runCurrent()
        assertEquals(1, vm.saveCalls)

        vm.saveResult.complete("saved")
        runCurrent()
        assertFalse(vm.state.value.saving)
        assertEquals("saved", vm.state.value.savedValue)
    }

    @Test
    fun `an asynchronous result preserves edits made while it was suspended`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())
        vm.onIntent(CommandIntent.Edit("submitted"))
        vm.onIntent(CommandIntent.Save)
        runCurrent()

        vm.onIntent(CommandIntent.Edit("new draft"))
        vm.saveResult.complete("submitted")
        runCurrent()

        assertEquals("submitted", vm.submittedDraft)
        assertEquals("submitted", vm.state.value.savedValue)
        assertEquals("new draft", vm.state.value.draft)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `command survives the last UI binding and source stop timeout`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())
        val ui = backgroundScope.launch { vm.withUiBinding { vm.state.collect() } }
        runCurrent()
        assertEquals(1, vm.sourceStarts)

        vm.onIntent(CommandIntent.Save)
        runCurrent()
        ui.cancel()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(1, vm.sourceStops)
        assertFalse(vm.saveCancelled)
        assertTrue(vm.state.value.saving)

        vm.saveResult.complete("finished while hidden")
        runCurrent()
        assertEquals("finished while hidden", vm.state.value.savedValue)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `clearing the ViewModelStore cancels commands and sources without a UI error`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())
        backgroundScope.launch { vm.withUiBinding { vm.state.collect() } }
        vm.onIntent(CommandIntent.Save)
        runCurrent()

        store.clear()
        runCurrent()

        assertTrue(vm.saveCancelled)
        assertEquals(1, vm.sourceStops)
        assertNull(vm.state.value.savedValue)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `completed operation cannot publish through a retained callback`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())
        assertTrue(vm.startPrimary("finished", waitForCancellation = false))
        runCurrent()
        assertEquals(listOf("finished"), vm.primaryFinishes)
        val completedScope = vm.primaryScopes.single()
        val completedState = vm.state.value
        assertEquals("finished", completedState.primaryResult)
        assertFalse(completedState.primaryLoading)

        assertFalse(completedScope.commitState { copy(primaryResult = "late callback") })
        assertEquals(completedState, vm.state.value)
    }

    @Test
    fun `returning to the same query does not make its previous request current`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())
        assertTrue(vm.startPrimary("A"))
        runCurrent()
        val firstScope = vm.primaryScopes.single()
        assertTrue(vm.startPrimary("B"))
        assertTrue(vm.startPrimary("A"))
        runCurrent()

        assertEquals(listOf("A"), vm.primaryFinishes)
        assertEquals(listOf("A", "A"), vm.primaryStarts)
        assertFalse(firstScope.commitState { copy(primaryResult = "first A") })
        assertTrue(vm.primaryScopes.last().commitState { copy(primaryResult = "second A") })
        assertEquals("A", vm.state.value.query)
        assertEquals("second A", vm.state.value.primaryResult)
    }

    @Test
    fun `replacing a secondary operation does not revoke the primary operation`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())
        assertTrue(vm.startPrimary("catalog"))
        assertTrue(vm.startSecondary())
        runCurrent()
        val firstSecondaryScope = vm.secondaryScopes.single()
        assertTrue(vm.startSecondary())
        runCurrent()

        assertTrue(vm.primaryFinishes.isEmpty())
        assertEquals(1, vm.secondaryFinishes)
        assertEquals(2, vm.secondaryScopes.size)
        assertTrue(vm.primaryScopes.single().commitState { copy(primaryResult = "catalog result") })
        assertFalse(firstSecondaryScope.commitState { copy(secondaryResult = "stale artwork") })
        assertTrue(vm.secondaryScopes.last().commitState { copy(secondaryResult = "artwork result") })
        assertEquals("catalog result", vm.state.value.primaryResult)
        assertEquals("artwork result", vm.state.value.secondaryResult)
    }

    @Test
    fun `property cancellation resets loading and immediately rejects a retained callback`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())
        assertTrue(vm.startPrimary("revoked"))
        runCurrent()
        val scope = vm.primaryScopes.single()
        assertTrue(vm.cancelPrimary())
        val stateAfterCancellation = vm.state.value
        assertFalse(stateAfterCancellation.primaryLoading)
        assertTrue(vm.primaryFinishes.isEmpty())

        // No dispatcher turn yet: cancellation must revoke writes before cleanup resumes.
        assertFalse(scope.commitState { copy(primaryResult = "too late") })
        assertFalse(scope.commitState { copy(primaryLoading = false) })
        assertEquals(stateAfterCancellation, vm.state.value)
        assertFalse(vm.cancelPrimary())
        runCurrent()
        assertEquals(listOf("revoked"), vm.primaryFinishes)
        assertEquals(stateAfterCancellation, vm.state.value)
    }

    @Test
    fun `meaningful result is visible before its effect reaches the collector`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())
        val observed = mutableListOf<Pair<CommandEffect, String?>>()
        backgroundScope.launch {
            vm.collectEffects { effect -> observed += effect to vm.state.value.savedValue }
        }
        runCurrent()

        vm.onIntent(CommandIntent.Save)
        runCurrent()
        vm.saveResult.complete("committed")
        runCurrent()

        assertEquals(listOf(CommandEffect.Saved("committed") to "committed"), observed)
    }

    @Test
    fun `effect buffered without a collector is consumed once by a later collector`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())
        vm.onIntent(CommandIntent.Save)
        runCurrent()
        vm.saveResult.complete("buffered")
        runCurrent()
        assertEquals("buffered", vm.state.value.savedValue)

        val first = mutableListOf<CommandEffect>()
        val firstCollector = backgroundScope.launch { vm.collectEffects(first::add) }
        runCurrent()
        firstCollector.cancelAndJoin()
        assertTrue(firstCollector.isCompleted)
        assertEquals(listOf(CommandEffect.Saved("buffered")), first)

        val second = mutableListOf<CommandEffect>()
        backgroundScope.launch { vm.collectEffects(second::add) }
        runCurrent()
        assertTrue(second.isEmpty())
    }

    @Test
    fun `clearing the store discards buffered effects`() = runTest(dispatcher) {
        val vm = own(CommandViewModel())
        vm.onIntent(CommandIntent.Save)
        runCurrent()
        vm.saveResult.complete("obsolete signal")
        runCurrent()
        store.clear()
        runCurrent()

        val effects = mutableListOf<CommandEffect>()
        val collector = backgroundScope.launch { vm.collectEffects(effects::add) }
        runCurrent()

        assertTrue(effects.isEmpty())
        assertTrue(collector.isCompleted)
        assertFalse(collector.isCancelled)
        assertEquals("obsolete signal", vm.state.value.savedValue)
    }

    @Test
    fun `unexpected source failure escapes rather than becoming a UI error`() {
        val expected = IllegalStateException("broken source invariant")

        val actual = assertThrows(IllegalStateException::class.java) {
            runTest(dispatcher) {
                val vm = own(CommandViewModel(sourceFailure = expected))
                try {
                    backgroundScope.launch { vm.withUiBinding { vm.state.collect() } }
                    runCurrent()
                    assertNull(vm.state.value.error)
                } finally {
                    store.clear()
                    runCurrent()
                }
            }
        }

        // Coroutine stack-trace recovery may copy the exception while retaining its type/message.
        assertEquals(expected.message, actual.message)
    }

    private fun <T : ViewModel> own(viewModel: T): T = viewModel.also {
        store.put("command-test-${nextKey++}", it)
    }

    private data class CommandState(
        val draft: String = "",
        val saving: Boolean = false,
        val savedValue: String? = null,
        val query: String = "",
        val primaryLoading: Boolean = false,
        val primaryResult: String? = null,
        val secondaryResult: String? = null,
        val error: String? = null,
    ) : UiState

    private sealed interface CommandIntent : UiIntent {
        data class Edit(val value: String) : CommandIntent
        data object Save : CommandIntent
    }

    private sealed interface CommandEffect : UiEffect {
        data class Saved(val value: String) : CommandEffect
    }

    /** A form-like fixture: explicit command ownership and no repository or UI dependencies. */
    private class CommandViewModel(
        private val sourceFailure: Throwable? = null,
    ) : MviTestViewModel<CommandState, CommandIntent, CommandEffect>(CommandState()) {
        val saveResult = CompletableDeferred<String>()
        private val save = idleOperation()
        private val primary = latestOperation()
        private val secondary = latestOperation()

        val primaryScopes = mutableListOf<OperationScope<CommandState>>()
        val primaryStarts = mutableListOf<String>()
        val primaryFinishes = mutableListOf<String>()
        val secondaryScopes = mutableListOf<OperationScope<CommandState>>()
        var secondaryFinishes = 0
            private set
        var saveCalls = 0
            private set
        var submittedDraft: String? = null
            private set
        var saveCancelled = false
            private set
        var sourceStarts = 0
            private set
        var sourceStops = 0
            private set
        val snapshot: CommandState get() = currentState

        override fun onIntent(intent: CommandIntent) {
            when (intent) {
                is CommandIntent.Edit -> setState { copy(draft = intent.value) }
                CommandIntent.Save -> {
                    val draft = currentState.draft
                    save.submit(onAccepted = { copy(saving = true) }) {
                        saveCalls++
                        submittedDraft = draft
                        try {
                            val result = saveResult.await()
                            commitState { copy(savedValue = result, saving = false) }
                            sendEffect(CommandEffect.Saved(result))
                        } catch (cancelled: CancellationException) {
                            saveCancelled = true
                            throw cancelled
                        } finally {
                            commitState { copy(saving = false) }
                        }
                    }
                }
            }
        }

        override suspend fun observeSources() {
            sourceStarts++
            try {
                sourceFailure?.let { throw it }
                awaitCancellation()
            } finally {
                sourceStops++
            }
        }

        fun startPrimary(query: String, waitForCancellation: Boolean = true): Boolean {
            return primary.submit(
                onAccepted = { copy(query = query, primaryLoading = true) },
            ) {
                primaryStarts += query
                primaryScopes += this
                try {
                    if (waitForCancellation) awaitCancellation()
                    commitState { copy(primaryResult = query, primaryLoading = false) }
                } finally {
                    primaryFinishes += query
                }
            }
        }

        fun cancelPrimary(): Boolean = primary.cancel { copy(primaryLoading = false) }

        fun startSecondary(): Boolean = secondary.submit {
            secondaryScopes += this
            try {
                awaitCancellation()
            } finally {
                secondaryFinishes++
            }
        }
    }
}
