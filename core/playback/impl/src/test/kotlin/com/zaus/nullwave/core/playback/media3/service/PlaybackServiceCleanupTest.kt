package com.zaus.nullwave.core.playback.media3.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackServiceCleanupTest {
    @Test
    fun `platform shutdown precedes lifetime cancellation and engine release follows owner close`() = runTest {
        val fixture = Fixture(this)

        val cleanup = fixture.close()

        assertTrue(cleanup.isCompleted)
        assertEquals(Fixture.releaseOrder, fixture.events)
        assertTrue(fixture.failures.isEmpty())
    }

    @Test
    fun `failures in both phases keep the first cause and attempt every release`() = runTest {
        val fixture = Fixture(this)
        val failingSteps = Fixture.releaseOrder.filter { it != "cancel" }
        val failures = failingSteps.associateWith { IllegalStateException(it) }
        fixture.failureAt = failures

        val cleanup = fixture.close()

        assertTrue(cleanup.isCompleted)
        assertEquals(Fixture.releaseOrder, fixture.events)
        val reported = fixture.failures.single()
        assertSame(failures.getValue("noisy"), reported)
        assertEquals(failingSteps.drop(1).map(failures::getValue), reported.suppressed.toList())
    }

    @Test
    fun `cancellation handler failure does not prevent owner or engine release`() = runTest {
        val fixture = Fixture(this)
        val cancellationFailure = IllegalStateException("cancellation handler")
        val listenerFailure = IllegalStateException("listener")
        fixture.failureAt = mapOf("cancel" to cancellationFailure, "listener" to listenerFailure)

        fixture.close()

        assertEquals(Fixture.releaseOrder, fixture.events)
        val reported = fixture.failures.single()
        assertSame(cancellationFailure, reported.cause)
        assertEquals(listOf(listenerFailure), reported.suppressed.toList())
    }

    @Test
    fun `owner failure remains primary when all engine releases also fail`() = runTest {
        val fixture = Fixture(this)
        val order = listOf("owner", "listener", "target", "session", "player")
        val failures = order.associateWith { IllegalArgumentException(it) }
        fixture.failureAt = failures

        fixture.close()

        assertEquals(Fixture.releaseOrder, fixture.events)
        val reported = fixture.failures.single()
        assertSame(failures.getValue("owner"), reported)
        assertEquals(order.drop(1).map(failures::getValue), reported.suppressed.toList())
    }

    @Test
    fun `cancelled service does not interrupt an owner waiting for admission or release early`() = runTest {
        val fixture = Fixture(this)
        val admission = CompletableDeferred<Unit>()

        val cleanup = fixture.close {
            fixture.step("owner-wait")
            admission.await()
            fixture.step("owner")
        }

        assertFalse(fixture.serviceJob.isActive)
        assertFalse(cleanup.isCompleted)
        assertEquals(listOf("noisy", "foreground", "cancel", "owner-wait"), fixture.events)
        admission.complete(Unit)
        runCurrent()

        assertTrue(cleanup.isCompleted)
        assertEquals(listOf("noisy", "foreground", "cancel", "owner-wait") + Fixture.releaseOrder.drop(3), fixture.events)
        assertTrue(fixture.failures.isEmpty())
    }

    @Test
    fun `synchronous failure is reported once after suspended owner and remaining releases finish`() = runTest {
        val fixture = Fixture(this)
        val primary = IllegalStateException("noisy")
        val ownerFailure = IllegalStateException("owner")
        val releaseFailure = IllegalStateException("listener")
        fixture.failureAt = mapOf("noisy" to primary, "listener" to releaseFailure)
        val admission = CompletableDeferred<Unit>()

        val cleanup = fixture.close {
            fixture.step("owner-wait")
            admission.await()
            throw ownerFailure
        }

        assertFalse(cleanup.isCompleted)
        assertTrue(fixture.failures.isEmpty())
        assertTrue(primary.suppressed.isEmpty())
        admission.complete(Unit)
        runCurrent()

        assertTrue(cleanup.isCompleted)
        assertSame(primary, fixture.failures.single())
        assertEquals(listOf(ownerFailure, releaseFailure), primary.suppressed.toList())
        assertEquals(listOf("noisy", "foreground", "cancel", "owner-wait", "listener", "target", "session", "player"), fixture.events)
    }

    @Test
    fun `engine release waits for structured child cleanup after owner fails`() = runTest {
        val fixture = Fixture(this)
        val childRelease = CompletableDeferred<Unit>()
        val ownerFailure = IllegalStateException("owner")

        val cleanup = fixture.close {
            coroutineScope {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            fixture.step("owner-child-wait")
                            childRelease.await()
                            fixture.step("owner-child-released")
                        }
                    }
                }
                throw ownerFailure
            }
        }
        runCurrent()

        assertFalse(cleanup.isCompleted)
        assertEquals(listOf("noisy", "foreground", "cancel", "owner-child-wait"), fixture.events)
        assertTrue(fixture.failures.isEmpty())
        childRelease.complete(Unit)
        runCurrent()

        assertTrue(cleanup.isCompleted)
        assertEquals(listOf("noisy", "foreground", "cancel", "owner-child-wait", "owner-child-released", "listener", "target", "session", "player"), fixture.events)
        // Coroutine stack-trace recovery may copy IllegalStateException after child suspension,
        // retaining the original instance in its cause chain.
        val reported = fixture.failures.single()
        assertTrue(generateSequence(reported) { it.cause }.any { it === ownerFailure })
    }

    @Test
    fun `same primary throwable can recur without self suppression skipping later releases`() = runTest {
        val fixture = Fixture(this)
        val primary = IllegalStateException("shared failure")
        val last = IllegalStateException("player")
        fixture.failureAt = mapOf("noisy" to primary, "foreground" to primary, "owner" to primary, "listener" to primary, "player" to last)

        fixture.close()

        assertEquals(Fixture.releaseOrder, fixture.events)
        assertSame(primary, fixture.failures.single())
        assertEquals(listOf(last), primary.suppressed.toList())
    }

    @Test
    fun `cancellation before ordinary failure does not hide the ordinary cleanup failure`() = runTest {
        val fixture = Fixture(this)
        val cancellation = CancellationException("noisy")
        val ordinary = IllegalStateException("owner")
        val later = IllegalStateException("session")
        fixture.failureAt = mapOf("noisy" to cancellation, "owner" to ordinary, "session" to later)

        fixture.close()

        assertEquals(Fixture.releaseOrder, fixture.events)
        assertSame(ordinary, fixture.failures.single())
        assertEquals(listOf(cancellation, later), ordinary.suppressed.toList())
    }

    @Test
    fun `cancellation after ordinary failure stays suppressed without replacing the original`() = runTest {
        val fixture = Fixture(this)
        val ordinary = IllegalStateException("foreground")
        val cancellation = CancellationException("owner")
        fixture.failureAt = mapOf("foreground" to ordinary, "owner" to cancellation)

        fixture.close()

        assertEquals(Fixture.releaseOrder, fixture.events)
        assertSame(ordinary, fixture.failures.single())
        assertEquals(listOf(cancellation), ordinary.suppressed.toList())
    }

    @Test
    fun `only cancellation still attempts all releases and cancels without reporting a fake failure`() = runTest {
        val fixture = Fixture(this)
        val cancellation = CancellationException("noisy")
        val later = CancellationException("session")
        fixture.failureAt = mapOf("noisy" to cancellation, "owner" to cancellation, "session" to later)

        val cleanup = fixture.close()

        assertTrue(cleanup.isCompleted)
        assertTrue(cleanup.isCancelled)
        assertEquals(Fixture.releaseOrder, fixture.events)
        assertTrue(fixture.failures.isEmpty())
        assertEquals(listOf(later), cancellation.suppressed.toList())
    }

    @Test
    fun `initialization cleanup retains its original failure with ordered suppressed releases`() {
        val original = IllegalArgumentException("initialization")
        val firstRelease = IllegalStateException("foreground")
        val lastRelease = IllegalStateException("player")
        val events = mutableListOf<String>()
        val failures = PlaybackCleanupFailures(original)

        failures.attempt { events += "foreground"; throw firstRelease }
        failures.attempt { events += "listener"; throw original }
        failures.attempt { events += "target" }
        failures.attempt { events += "player"; throw lastRelease }
        val reported = assertThrows(IllegalArgumentException::class.java, failures::throwIfAny)

        assertSame(original, reported)
        assertEquals(listOf("foreground", "listener", "target", "player"), events)
        assertEquals(listOf(firstRelease, lastRelease), reported.suppressed.toList())
    }

    private class Fixture(test: TestScope) {
        val events = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()
        var failureAt: Map<String, Throwable> = emptyMap()
        val main = StandardTestDispatcher(test.testScheduler)
        val serviceJob = SupervisorJob()
        private val scope = CoroutineScope(serviceJob + main + CoroutineExceptionHandler { _, failure -> failures += failure })

        init {
            serviceJob.invokeOnCompletion { step("cancel") }
        }

        fun close(owner: suspend () -> Unit = { step("owner") }) = closePlaybackResources(
            scope = scope,
            main = main,
            beforeCancellation = listOf({ step("noisy") }, { step("foreground") }),
            closeOwner = owner,
            releases = listOf({ step("listener") }, { step("target") }, { step("session") }, { step("player") }),
        )

        fun step(name: String) {
            events += name
            failureAt[name]?.let { throw it }
        }

        companion object {
            val releaseOrder = listOf("noisy", "foreground", "cancel", "owner", "listener", "target", "session", "player")
        }
    }
}
