package com.zaus.nullwave.core.designsystem.components.control.slider

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderValuesTest {
    @Test fun negativeRangeSnapsToInteriorStopsAndEndpoints() {
        val range = -12f..12f
        validateSliderRange(range, 7)
        assertEquals(-6f, snapSliderValue(-7f, range, 7), 0f)
        assertEquals(-3f, snapSliderValue(-4.5f, range, 7), 0f)
        assertEquals(-12f, snapSliderValue(-30f, range, 7), 0f)
        assertEquals(12f, snapSliderValue(30f, range, 7), 0f)
    }

    @Test fun continuousValueClampsWithoutQuantization() {
        assertEquals(0.123f, snapSliderValue(0.123f, 0f..1f, 0), 0f)
        assertEquals(1f, snapSliderValue(2f, 0f..1f, 0), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun collapsedRangeIsRejected() { validateSliderRange(1f..1f, 0) }

    @Test(expected = IllegalArgumentException::class)
    fun overflowingRangeIsRejected() { validateSliderRange(-Float.MAX_VALUE..Float.MAX_VALUE, 0) }

    @Test(expected = IllegalArgumentException::class)
    fun negativeStepsAreRejected() { validateSliderRange(0f..1f, -1) }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteValueIsRejected() { snapSliderValue(Float.NaN, 0f..1f, 0) }
}
