package com.zaus.nullwave.core.mvi

import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.dsl.OperationBuilder
import com.zaus.nullwave.core.mvi.runtime.MviAccess
import com.zaus.nullwave.core.mvi.runtime.MviRuntime
import com.zaus.nullwave.core.mvi.runtime.operation.OperationPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationFailureIntegrationTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(ImmediateTestMainDispatcher(dispatcher))
    }

    @After
    fun tearDown() {
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `every policy reports execution as primary when its finalizer also fails`() = runTest(dispatcher) {
        for (policy in OperationPolicy.entries) {
            val original = IllegalArgumentException("repository message")
            val finalizer = IllegalStateException("finalizer message")
            withRuntime(
                definition = {
                    val clauses: OperationBuilder<Snapshot, Nothing, Unit, Unit>.() -> Unit = {
                        execute { throw original }
                        onFinished { throw finalizer }
                    }
                    val operation = when (policy) {
                        OperationPolicy.Latest -> latest("work", clauses)
                        OperationPolicy.Queued -> queued("work", block = clauses)
                        OperationPolicy.Idle -> idle("work", clauses)
                    }
                    on<Start> { submit(operation, Unit) }
                },
            ) { schema, _, failures ->
                schema.dispatch(Start)
                runCurrent()

                assertEquals(1, failures.size)
                val reported = failures.single()
                // withContext may recover the coroutine stack into an exception copy. The
                // original must stay reachable, and finalization must augment the reported error.
                assertEquals(original.javaClass, reported.javaClass)
                assertEquals(original.message, reported.message)
                assertTrue(generateSequence(reported) { it.cause }.any { it === original })
                assertEquals(listOf(finalizer), reported.suppressed.toList())
            }
        }
    }

    @Test
    fun `result reducer failure remains primary and neither failed reducer publishes state`() = runTest(dispatcher) {
        val original = IllegalArgumentException("invalid result")
        val finalizer = IllegalStateException("invalid final state")
        withRuntime(
            definition = {
                val operation = latest<Unit, Int>("work") {
                    execute { 42 }
                    onAccepted { copy(value = 1) }
                    onResult { _, _ -> throw original }
                    onFinished { throw finalizer }
                }
                on<Start> { submit(operation, Unit) }
            },
        ) { schema, runtime, failures ->
            schema.dispatch(Start)
            runCurrent()

            assertEquals(1, failures.size)
            assertSame(original, failures.single())
            assertEquals(listOf(finalizer), original.suppressed.toList())
            assertEquals(Snapshot(value = 1), runtime.state.value)
        }
    }

    @Test
    fun `self cancellation does not silence an ordinary finalizer exception`() = runTest(dispatcher) {
        val cancellation = CancellationException("work cancelled")
        val finalizer = IllegalStateException("broken finalizer")
        withRuntime(
            definition = {
                val operation = idle<Unit, Unit>("work") {
                    execute { throw cancellation }
                    onResult { _, _ -> error("Cancelled work must not publish a result") }
                    onFinished { throw finalizer }
                }
                on<Start> { submit(operation, Unit) }
            },
        ) { schema, _, failures ->
            schema.dispatch(Start)
            runCurrent()

            assertEquals(1, failures.size)
            assertSame(finalizer, failures.single())
            assertTrue(finalizer.suppressed.any { it === cancellation || it.cause === cancellation })
        }
    }

    private fun withRuntime(
        definition: MviDefinition<Snapshot, Start, Nothing>.() -> Unit,
        block: (MviDefinition<Snapshot, Start, Nothing>, MviRuntime<Snapshot, Nothing>, List<Throwable>) -> Unit,
    ) {
        val failures = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate +
                CoroutineExceptionHandler { _, failure -> failures += failure },
        )
        val runtime = MviRuntime<Snapshot, Nothing>(Snapshot(), scope, observeSources = {})
        try {
            val schema = MviDefinition<Snapshot, Start, Nothing>(runtime)
            MviAccess.defining(runtime) {
                schema.definition()
                schema.seal()
            }
            block(schema, runtime, failures)
        } finally {
            runtime.close()
            scope.cancel()
        }
    }

    private data class Snapshot(val value: Int = 0) : UiState
    private data object Start : UiIntent
}
