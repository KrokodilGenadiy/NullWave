package com.zaus.nullwave.core.mvi

import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.dsl.OperationBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** A valid UI continuation may run while a source's Work is still installed on the Main thread. */
@RunWith(Parameterized::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MviNeutralRuntimeContextTest(
    private val policy: String,
    private val suspendingWork: Boolean,
    private val sameViewModel: Boolean,
) {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(AndroidLikeTestMainDispatcher(dispatcher))
    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `source resumed UI can run every operation phase without inheriting Work`() = runTest(dispatcher) {
        val signal = CompletableDeferred<Unit>()
        val source = ProbeVm(signal).also { store.put("source", it) }
        val target = if (sameViewModel) source else ProbeVm(null).also { store.put("target", it) }
        val ui = launch(Dispatchers.Main.immediate) {
            val binding = launch(start = CoroutineStart.UNDISPATCHED) {
                source.withUiBinding { awaitCancellation() }
            }
            try {
                withTimeout(5_000) { signal.await() }
                target.uiApi().onIntent(Run(42))
            } finally {
                binding.cancelAndJoin()
            }
        }
        ui.join()
        advanceTimeBy(2)
        runCurrent()

        assertEquals(Snapshot(accepted = 1, started = 1, value = 42, finished = 1), target.snapshot.value.state)
        if (!sameViewModel) assertEquals(Snapshot(), source.snapshot.value.state)
    }

    private data class Snapshot(
        val accepted: Int = 0,
        val started: Int = 0,
        val value: Int = 0,
        val finished: Int = 0,
    ) : UiState
    private data class Run(val value: Int) : UiIntent

    private inner class ProbeVm(private val signal: CompletableDeferred<Unit>?) :
        BaseMviViewModel<Snapshot, Run, Nothing>(Snapshot(), sourceStopTimeoutMillis = 0) {
        override fun MviDefinition<Snapshot, Run, Nothing>.define() {
            if (signal != null) {
                source("source", flow = {
                    flow<Unit> {
                        delay(1) // Android's resumeUndispatched keeps the source's phase on Main.
                        signal.complete(Unit)
                        awaitCancellation()
                    }
                }) { this }
            }
            val configure: OperationBuilder<Snapshot, Nothing, Int, Int>.() -> Unit = {
                onAccepted { copy(accepted = accepted + 1) }
                onStarted { copy(started = started + 1) }
                execute { value ->
                    if (suspendingWork) delay(1)
                    value
                }
                onResult { _, result -> copy(value = result) }
                onFinished { copy(finished = finished + 1) }
            }
            val operation = when (policy) {
                "latest" -> latest("work", configure)
                "queued" -> queued("work", block = configure)
                "idle" -> idle("work", configure)
                else -> error("Unknown test policy: $policy")
            }
            on<Run> { submit(operation, it.value) }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "policy={0}, suspending={1}, sameVm={2}")
        fun cases(): List<Array<Any>> = listOf("latest", "queued", "idle").flatMap { policy ->
            listOf(false, true).flatMap { suspending ->
                listOf(false, true).map { sameVm -> arrayOf<Any>(policy, suspending, sameVm) }
            }
        }
    }
}
