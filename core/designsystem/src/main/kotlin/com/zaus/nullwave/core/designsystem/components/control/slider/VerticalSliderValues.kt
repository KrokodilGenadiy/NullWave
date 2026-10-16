package com.zaus.nullwave.core.designsystem.components.control.slider

/** Material 1.4 reverses PageUp/PageDown; keep the vertical direction consistent with Up/Down. */
internal fun verticalSliderPageValue(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    increase: Boolean,
): Float {
    validateSliderRange(range, steps)
    val current = snapSliderValue(value, range, steps)
    val intervals = if (steps == 0) 100 else steps + 1
    val page = (intervals / 10).coerceIn(1, 10)
    val increment = (range.endInclusive.toDouble() - range.start) / intervals * page
    val next = (current.toDouble() + if (increase) increment else -increment)
        .coerceIn(range.start.toDouble(), range.endInclusive.toDouble()).toFloat()
    return snapSliderValue(next, range, steps)
}
