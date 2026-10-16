package com.zaus.nullwave.core.designsystem.components.control.stepper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepperValuesTest {
    @Test fun repeatedTenthsReachZeroAndBothBoundsWithoutAccumulation() {
        val scale = StepperScale(-12f..12f, 0.1f)
        var value = -12f
        repeat(120) { value = scale.next(value, increase = true) }
        assertEquals(0f.toBits(), value.toBits())
        repeat(120) { value = scale.next(value, increase = true) }
        assertEquals(12f, value, 0f)
        repeat(240) { value = scale.next(value, increase = false) }
        assertEquals(-12f, value, 0f)
    }

    @Test fun offGridValuesMoveToNextDirectionalStop() {
        val scale = StepperScale(-12f..12f, 0.1f)
        assertEquals(3.5f, scale.next(3.45f, increase = true), 0f)
        assertEquals(3.4f, scale.next(3.45f, increase = false), 0f)
        assertEquals(-3.4f, scale.next(-3.45f, increase = true), 0f)
        assertEquals(-3.5f, scale.next(-3.45f, increase = false), 0f)
    }

    @Test fun partialLastIntervalPreservesBothEndpoints() {
        val scale = StepperScale(0f..1f, 0.3f)
        assertEquals(1f, scale.next(0.9f, increase = true), 0f)
        assertEquals(0.9f, scale.next(1f, increase = false), 0f)
        assertEquals(0f, scale.next(0.3f, increase = false), 0f)
        assertEquals(1f, scale.next(1f, increase = true), 0f)
        assertEquals(0f, scale.next(0f, increase = false), 0f)
    }

    @Test fun largeStepTraversesDirectlyBetweenBounds() {
        val scale = StepperScale(-1f..1f, 3f)
        assertEquals(1f, scale.next(-1f, increase = true), 0f)
        assertEquals(-1f, scale.next(1f, increase = false), 0f)
    }

    @Test fun adjacentRepresentableValuesDoNotBecomeNoOpClicks() {
        val scale = StepperScale(0f..1f, 0.33333334f)
        assertEquals(0.33333334f, scale.next(0.6666667f, increase = false), 0f)
        assertTrue(scale.next(Math.nextDown(1f), increase = true) > Math.nextDown(1f))
        assertTrue(scale.next(Math.nextUp(0f), increase = false) < Math.nextUp(0f))
    }

    @Test fun clampingAndSignedZeroAreStable() {
        val scale = StepperScale(-1f..1f, 0.1f)
        assertEquals(-1f, scale.clamp(-8f), 0f)
        assertEquals(1f, scale.clamp(8f), 0f)
        assertEquals(0f.toBits(), scale.clamp(-0f).toBits())
        assertEquals(-0.1f, scale.next(-0f, increase = false), 0f)
        assertEquals(0.1f, scale.next(-0f, increase = true), 0f)
    }

    @Test fun finiteEndpointsDoNotOverflowWhenComputingTheirSpan() {
        val scale = StepperScale(-Float.MAX_VALUE..Float.MAX_VALUE, Float.MAX_VALUE)
        assertEquals(0f, scale.next(-Float.MAX_VALUE, increase = true), 0f)
        assertEquals(Float.MAX_VALUE, scale.next(0f, increase = true), 0f)
        assertEquals(-Float.MAX_VALUE, scale.next(0f, increase = false), 0f)
    }

    @Test fun collapsedReversedAndNonFiniteRangesAreRejected() {
        listOf(1f..1f, 2f..1f, Float.NaN..1f, 0f..Float.POSITIVE_INFINITY).forEach { range ->
            rejects { StepperScale(range, 0.1f) }
        }
    }

    @Test fun invalidAndUnrepresentableStepsAreRejected() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 1e-12f).forEach { step ->
            rejects { StepperScale(1f..2f, step) }
        }
    }

    @Test fun nonFiniteInputValuesAreRejected() {
        val scale = StepperScale(-12f..12f, 0.1f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { value ->
            rejects { scale.next(value, increase = true) }
        }
    }

    private fun rejects(block: () -> Unit) {
        var thrown = false
        try { block() } catch (_: IllegalArgumentException) { thrown = true }
        assertTrue("Expected IllegalArgumentException", thrown)
    }
}
