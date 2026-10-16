package com.zaus.nullwave.core.designsystem.components.control.slider

import org.junit.Assert.assertEquals
import org.junit.Test

class VerticalSliderValuesTest {
    @Test fun continuousPagesMoveUpAndDownAcrossZero() {
        assertEquals(1.4f, verticalSliderPageValue(-1f, -12f..12f, 0, true), 0.0001f)
        assertEquals(-1.4f, verticalSliderPageValue(1f, -12f..12f, 0, false), 0.0001f)
    }

    @Test fun denseEqPagesUseTenStopsAndReachBounds() {
        assertEquals(1f, verticalSliderPageValue(0f, -12f..12f, 239, true), 0f)
        assertEquals(-1f, verticalSliderPageValue(0f, -12f..12f, 239, false), 0f)
        assertEquals(12f, verticalSliderPageValue(11.8f, -12f..12f, 239, true), 0f)
        assertEquals(-12f, verticalSliderPageValue(-11.8f, -12f..12f, 239, false), 0f)
    }

    @Test fun sparsePagesStayOnDirectionalStop() {
        assertEquals(0f, verticalSliderPageValue(-6f, -12f..12f, 3, true), 0f)
        assertEquals(-6f, verticalSliderPageValue(0f, -12f..12f, 3, false), 0f)
        assertEquals(12f, verticalSliderPageValue(12f, -12f..12f, 3, true), 0f)
    }
}
