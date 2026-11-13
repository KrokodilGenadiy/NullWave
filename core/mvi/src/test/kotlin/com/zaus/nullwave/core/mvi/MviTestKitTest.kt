package com.zaus.nullwave.core.mvi

import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.testing.MviTestMain
import com.zaus.nullwave.core.mvi.testing.assertHandlesEveryIntent
import com.zaus.nullwave.core.mvi.testing.bind
import com.zaus.nullwave.core.mvi.testing.send
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MviTestKitTest {
    private val mvi = MviTestMain()
    private val created = mutableListOf<KitViewModel>()

    @Before fun setUp() = mvi.setUp()
    @After fun tearDown() = mvi.tearDown()

    @Test
    fun everyIntentOfANestedSealedHierarchyIsSentToItsOwnViewModel() = runTest(mvi.dispatcher) {
        assertHandlesEveryIntent(allExamples) { kitViewModel(handleRename = true) }

        assertEquals(allExamples.size, created.size)
        assertEquals(listOf(1, 0, 0), created.map { it.snapshot.value.state.count })
        assertEquals("Handlers run without a binding, so sources stay stopped", 0, upstream.subscriptionCount.value)
    }

    @Test
    fun aMissingHandlerFailsTheTestWithTheIntentName() {
        val failure = assertThrows(IllegalStateException::class.java) {
            runTest(mvi.dispatcher) {
                assertHandlesEveryIntent(allExamples) { kitViewModel(handleRename = false) }
            }
        }
        assertEquals("No handler is registered for intent 'Rename'.", failure.message)
    }

    @Test
    fun anIntentWithoutAnExampleIsNamedBeforeAnythingIsSent() {
        val failure = assertThrows(AssertionError::class.java) {
            runTest(mvi.dispatcher) {
                assertHandlesEveryIntent(allExamples.dropLast(1)) { kitViewModel(handleRename = true) }
            }
        }
        assertEquals(
            "No example for 1 intent(s): com.zaus.nullwave.core.mvi.MviTestKitTest.KitIntent.Edit.Rename",
            failure.message,
        )
        assertTrue("Nothing may be sent when the list is incomplete", created.isEmpty())
    }

    @Test
    fun anUnsealedIntentRootIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest(mvi.dispatcher) {
                assertHandlesEveryIntent(listOf(OpenIntent())) { mvi.own(OpenViewModel()) }
            }
        }
    }

    @Test
    fun bindCollectsSourcesUntilItsJobIsCancelledAndSendRunsTheHandler() = runTest(mvi.dispatcher) {
        val vm = kitViewModel(handleRename = true)
        upstream.value = 5
        runCurrent()
        assertEquals("Reading State alone never binds sources", 0, vm.snapshot.value.state.seen)

        val binding = bind(vm)
        assertEquals(5, vm.snapshot.value.state.seen)
        send(vm, KitIntent.Increment)
        assertEquals(1, vm.snapshot.value.state.count)

        binding.cancel()
        runCurrent()
        upstream.value = 9
        runCurrent()
        assertEquals("Stop timeout 0 releases the source with the binding", 5, vm.snapshot.value.state.seen)
    }

    @Test
    fun tearDownClearsOwnedViewModels() {
        val vm = KitViewModel(handleRename = true)
        val local = MviTestMain()
        local.setUp()
        local.own(vm)
        assertFalse(vm.cleared)
        local.tearDown()
        assertTrue(vm.cleared)
        mvi.setUp() // tearDown restored the real Main; the outer @After expects ours.
    }

    private val upstream = MutableStateFlow(0)
    private val allExamples = listOf(KitIntent.Increment, KitIntent.Edit.Reset, KitIntent.Edit.Rename("x"))

    private fun kitViewModel(handleRename: Boolean) = mvi.own(KitViewModel(handleRename)).also { created += it }

    private data class KitState(val count: Int = 0, val seen: Int = 0) : UiState

    sealed interface KitIntent : UiIntent {
        data object Increment : KitIntent
        sealed interface Edit : KitIntent {
            data class Rename(val name: String) : Edit
            data object Reset : Edit
        }
    }

    private inner class KitViewModel(private val handleRename: Boolean) :
        BaseMviViewModel<KitState, KitIntent, Nothing>(KitState(), sourceStopTimeoutMillis = 0) {
        var cleared = false

        init {
            addCloseable { cleared = true }
        }

        override fun MviDefinition<KitState, KitIntent, Nothing>.define() {
            source("kit.upstream", flow = { upstream }) { value -> copy(seen = value) }
            on<KitIntent.Increment> { reduce { copy(count = count + 1) } }
            on<KitIntent.Edit.Reset> { reduce { copy(count = 0) } }
            if (handleRename) on<KitIntent.Edit.Rename> { }
        }
    }

    private open class OpenIntent : UiIntent

    private class OpenViewModel : BaseMviViewModel<KitState, OpenIntent, Nothing>(KitState()) {
        override fun MviDefinition<KitState, OpenIntent, Nothing>.define() {
            on<OpenIntent> { }
        }
    }
}
