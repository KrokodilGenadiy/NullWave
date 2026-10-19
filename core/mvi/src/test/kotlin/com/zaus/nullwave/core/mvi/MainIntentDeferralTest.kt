package com.zaus.nullwave.core.mvi

import com.zaus.nullwave.core.mvi.runtime.MainIntentIngress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainIntentDeferralTest {
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
    fun `nested deferrals preserve FIFO until the outer scope releases`() = runTest(dispatcher) {
        val ingress = MainIntentIngress(backgroundScope)
        val events = mutableListOf<String>()
        try {
            val returned = ingress.deferDispatch {
                events += "outer started"
                ingress.dispatch { events += "first" }
                ingress.deferDispatch {
                    events += "inner started"
                    ingress.dispatch { events += "second" }
                    events += "inner finished"
                }
                assertEquals(listOf("outer started", "inner started", "inner finished"), events)
                ingress.dispatch { events += "third" }
                events += "outer finished"
                42
            }
            runCurrent()

            assertEquals(42, returned)
            assertEquals(
                listOf("outer started", "inner started", "inner finished", "outer finished", "first", "second", "third"),
                events,
            )
        } finally {
            ingress.close()
        }
    }

    @Test
    fun `worker and Main submissions retain admission order while deferred`() = runTest(dispatcher) {
        val ingress = MainIntentIngress(backgroundScope)
        val handled = mutableListOf<Int>()
        val handledOn = mutableListOf<Thread>()
        val mainThread = Thread.currentThread()
        fun record(value: Int) {
            handled += value
            handledOn += Thread.currentThread()
        }
        try {
            ingress.deferDispatch {
                onWorker { ingress.dispatch { record(1) } }
                ingress.dispatch { record(2) }
                onWorker { ingress.dispatch { record(3) } }
                ingress.dispatch { record(4) }
                runCurrent()
                assertTrue(handled.isEmpty())
            }
            runCurrent()

            assertEquals(listOf(1, 2, 3, 4), handled)
            assertTrue(handledOn.all { it === mainThread })
        } finally {
            ingress.close()
        }
    }

    @Test
    fun `throwing deferral releases pending calls and preserves the original failure`() = runTest(dispatcher) {
        val ingress = MainIntentIngress(backgroundScope)
        val handled = mutableListOf<Int>()
        val original = IllegalArgumentException("cancellation reducer failed")
        try {
            val thrown = assertThrows(IllegalArgumentException::class.java) {
                ingress.deferDispatch {
                    ingress.dispatch { handled += 1 }
                    throw original
                }
            }
            runCurrent()

            assertSame(original, thrown)
            assertEquals(listOf(1), handled)
            ingress.dispatch { handled += 2 }
            assertEquals(listOf(1, 2), handled)
        } finally {
            ingress.close()
        }
    }

    @Test
    fun `closing inside deferral discards pending and later calls`() = runTest(dispatcher) {
        val ingress = MainIntentIngress(backgroundScope)
        val handled = mutableListOf<Int>()
        ingress.deferDispatch {
            ingress.dispatch { handled += 1 }
            onWorker { ingress.dispatch { handled += 2 } }
            ingress.close()
            ingress.dispatch { handled += 3 }
        }
        ingress.dispatch { handled += 4 }
        onWorker { ingress.dispatch { handled += 5 } }
        runCurrent()

        assertTrue(handled.isEmpty())
    }

    @Test
    fun `an already scheduled drain pauses during deferral and resumes on release`() = runTest(dispatcher) {
        val ingress = MainIntentIngress(backgroundScope)
        val handled = mutableListOf<Int>()
        try {
            onWorker { ingress.dispatch { handled += 1 } }
            ingress.deferDispatch {
                runCurrent()
                ingress.dispatch { handled += 2 }
                assertTrue(handled.isEmpty())
            }
            runCurrent()

            assertEquals(listOf(1, 2), handled)
        } finally {
            ingress.close()
        }
    }

    private fun onWorker(block: () -> Unit) {
        val failure = AtomicReference<Throwable>()
        val worker = Thread {
            try {
                block()
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        worker.start()
        worker.join()
        assertNull(failure.get())
    }
}
