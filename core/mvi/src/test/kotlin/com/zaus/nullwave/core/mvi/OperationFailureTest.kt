package com.zaus.nullwave.core.mvi

import com.zaus.nullwave.core.mvi.runtime.operation.withOperationFinalization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationFailureTest {
    @Test
    fun `suspending success returns value and finalizes once`() = runTest {
        var finalized = 0

        val value = withOperationFinalization(
            block = { yield(); 42 },
            finalizer = { finalized++ },
        )

        assertEquals(42, value)
        assertEquals(1, finalized)
    }

    @Test
    fun `work failure survives successful finalization`() {
        val original = IllegalArgumentException("original work message")
        var finalized = 0

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            withOperationFinalization(block = { throw original }, finalizer = { finalized++ })
        }

        assertSame(original, thrown)
        assertEquals(1, finalized)
        assertTrue(thrown.suppressed.isEmpty())
    }

    @Test
    fun `finalizer failure after successful work remains observable`() {
        val finalizer = IllegalStateException("broken finalizer")

        val thrown = assertThrows(IllegalStateException::class.java) {
            withOperationFinalization(block = {}, finalizer = { throw finalizer })
        }

        assertSame(finalizer, thrown)
        assertTrue(thrown.suppressed.isEmpty())
    }

    @Test
    fun `ordinary work failure keeps its cause and existing suppressed errors`() {
        val cause = IllegalStateException("repository cause")
        val existing = IllegalArgumentException("previous diagnostic")
        val original = IllegalStateException("original message", cause).apply { addSuppressed(existing) }
        val finalizer = IllegalArgumentException("broken finalizer")

        val thrown = assertThrows(IllegalStateException::class.java) {
            withOperationFinalization(block = { throw original }, finalizer = { throw finalizer })
        }

        assertSame(original, thrown)
        assertSame(cause, thrown.cause)
        assertEquals(listOf(existing, finalizer), thrown.suppressed.toList())
    }

    @Test
    fun `finalizer cancellation does not hide ordinary work failure`() {
        val original = IllegalArgumentException("work failed")
        val finalizer = CancellationException("finalizer cancelled")

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            withOperationFinalization(block = { throw original }, finalizer = { throw finalizer })
        }

        assertSame(original, thrown)
        assertEquals(listOf(finalizer), thrown.suppressed.toList())
    }

    @Test
    fun `ordinary finalizer failure is not hidden under work cancellation`() {
        val cancellation = CancellationException("work cancelled")
        val finalizer = IllegalStateException("broken finalizer")

        val thrown = assertThrows(IllegalStateException::class.java) {
            withOperationFinalization(block = { throw cancellation }, finalizer = { throw finalizer })
        }

        assertSame(finalizer, thrown)
        assertEquals(listOf(cancellation), thrown.suppressed.toList())
    }

    @Test
    fun `two cancellations preserve the original cancellation`() {
        val original = CancellationException("work cancelled")
        val finalizer = CancellationException("finalizer cancelled")

        val thrown = assertThrows(CancellationException::class.java) {
            withOperationFinalization(block = { throw original }, finalizer = { throw finalizer })
        }

        assertSame(original, thrown)
        assertEquals(listOf(finalizer), thrown.suppressed.toList())
    }

    @Test
    fun `throwing the same object twice does not cause self suppression failure`() {
        val original = IllegalArgumentException("shared failure")

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            withOperationFinalization(block = { throw original }, finalizer = { throw original })
        }

        assertSame(original, thrown)
        assertTrue(thrown.suppressed.isEmpty())
    }

    @Test
    fun `fatal work error is not converted to a recoverable failure`() {
        val original = AssertionError("broken invariant")
        val finalizer = IllegalStateException("broken finalizer")

        val thrown = assertThrows(AssertionError::class.java) {
            withOperationFinalization(block = { throw original }, finalizer = { throw finalizer })
        }

        assertSame(original, thrown)
        assertEquals(listOf(finalizer), thrown.suppressed.toList())
    }

    @Test
    fun `nonlocal return still finalizes once`() {
        var finalized = 0

        fun exitEarly(): Int {
            withOperationFinalization(block = { return 42 }, finalizer = { finalized++ })
        }

        assertEquals(42, exitEarly())
        assertEquals(1, finalized)
    }
}
