package com.zaus.nullwave.core.designsystem.components.control.slider

import kotlin.math.roundToInt

internal fun validateSliderRange(range: ClosedFloatingPointRange<Float>, steps: Int) {
    require(range.start.isFinite() && range.endInclusive.isFinite() && range.start < range.endInclusive &&
        (range.endInclusive - range.start).isFinite()) { "Slider range must be finite and increasing" }
    require(steps in 0..(Int.MAX_VALUE - 2)) { "Steps must be nonnegative without overflowing the endpoints" }
}

/** Match Material's endpoint-inclusive steps, including accessibility changes in negative ranges. */
internal fun snapSliderValue(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    require(value.isFinite()) { "Slider value must be finite" }
    val clamped = value.coerceIn(range)
    if (steps == 0) return clamped
    val intervals = steps + 1
    val span = range.endInclusive.toDouble() - range.start
    val fraction = (clamped.toDouble() - range.start) / span
    return (range.start + (fraction * intervals).roundToInt().toDouble() / intervals * span).toFloat().coerceIn(range)
}
