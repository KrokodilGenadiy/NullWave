package com.zaus.nullwave.core.designsystem.components.control.stepper

import java.math.BigDecimal
import java.math.RoundingMode

/** Decimal grid avoids accumulating binary Float error when the EQ step is 0.1 dB. */
internal class StepperScale(
    private val range: ClosedFloatingPointRange<Float>,
    step: Float,
) {
    init {
        require(range.start.isFinite() && range.endInclusive.isFinite() && range.start < range.endInclusive) {
            "Stepper range must have finite, increasing endpoints"
        }
        require(step.isFinite() && step > 0f) { "Stepper step must be finite and positive" }
        // Every grid interval must be distinguishable throughout the Float range. Silently
        // skipping many unrepresentable steps would misrepresent the requested precision.
        require(step >= maxOf(Math.ulp(range.start), Math.ulp(range.endInclusive))) {
            "Stepper step is too small for Float precision in this range"
        }
    }

    private val start = range.start.decimal()
    private val end = range.endInclusive.decimal()
    private val increment = step.decimal()

    fun clamp(value: Float): Float {
        require(value.isFinite()) { "Stepper value must be finite" }
        return value.coerceIn(range.start, range.endInclusive).let { if (it == 0f) 0f else it }
    }

    /** Next grid stop strictly in the requested direction; both bounds are always stops. */
    fun next(value: Float, increase: Boolean): Float {
        val current = clamp(value)
        if (increase && current == range.endInclusive || !increase && current == range.start) return current
        val direction = if (increase) BigDecimal.ONE else -BigDecimal.ONE
        val rounding = if (increase) RoundingMode.FLOOR else RoundingMode.CEILING
        var index = (current.decimal() - start).divide(increment, 0, rounding) + direction
        fun target(): Float = (start + increment * index).coerceIn(start, end).toFloat()
        var result = target()
        // Float.toString() is the canonical decimal representation, but can land just to one
        // side of a grid point. Skip that same representable value, never emit a no-op click.
        if (result == current) {
            index += direction
            result = target()
        }
        return if (result == 0f) 0f else result
    }
}

private fun Float.decimal(): BigDecimal = BigDecimal(toString())
