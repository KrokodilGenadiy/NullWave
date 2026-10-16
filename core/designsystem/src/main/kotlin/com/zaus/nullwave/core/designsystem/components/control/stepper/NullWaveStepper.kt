package com.zaus.nullwave.core.designsystem.components.control.stepper

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Controlled precise adjustment with two independently accessible actions of at least 48 dp.
 * [valueLabel] is the caller-formatted, localized value (including units); it must describe the
 * clamped [value]. Labels wrap. When the value cannot fit between the buttons, it moves above them.
 * The parent must allow two touch targets and their spacing; font scaling may enlarge the buttons.
 *
 * Stops are [valueRange.start] + n * [step], plus the upper bound even for a partial last interval.
 * An off-grid value moves to the next stop in the requested direction. Decimal arithmetic avoids
 * accumulating 0.1-step drift; a step below Float resolution anywhere in the range is rejected.
 * Non-finite values/ranges and non-positive steps are invalid. Finite input values clamp to bounds.
 * Each activation emits one [onValueChange] then [onValueChangeFinished]; holding does not repeat.
 * The caller owns [value], so rejecting the update leaves this component unchanged.
 */
@Composable
fun NullWaveStepper(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    valueLabel: String,
    step: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    require(label.isNotBlank()) { "Stepper needs a non-blank label" }
    require(valueLabel.isNotBlank()) { "Stepper needs a non-blank value label" }
    val scale = remember(valueRange.start, valueRange.endInclusive, step) { StepperScale(valueRange, step) }
    val current = scale.clamp(value)
    val decrease = scale.next(current, increase = false)
    val increase = scale.next(current, increase = true)
    val spacing = NullWaveTheme.spacing
    val colors = NullWaveTheme.colors
    val typography = NullWaveTheme.typography
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val glyphWidth = listOf("+", "−").maxOf { measurer.measure(it, style = typography.h1, softWrap = false).size.width }
    val valueText = measurer.measure(valueLabel, style = typography.mono, softWrap = false)
    val buttonWidth = maxOf(NullWaveTheme.dimens.touchTarget, with(density) { glyphWidth.toDp() } + spacing.xs * 2)
    val decreaseLabel = stringResource(R.string.nw_stepper_decrease, label)
    val increaseLabel = stringResource(R.string.nw_stepper_increase, label)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(label, style = typography.caption, color = if (enabled) colors.textPrimary else colors.textSecondary)
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                StepperButton(
                    glyph = "−", description = decreaseLabel, valueLabel = valueLabel,
                    enabled = enabled && decrease < current, width = buttonWidth,
                    onClick = { onValueChange(decrease); onValueChangeFinished?.invoke() },
                )
                Text(
                    valueLabel, modifier = Modifier.clearAndSetSemantics {},
                    style = typography.mono, textAlign = TextAlign.Center,
                    color = if (enabled) colors.textPrimary else colors.textSecondary,
                )
                StepperButton(
                    glyph = "+", description = increaseLabel, valueLabel = valueLabel,
                    enabled = enabled && increase > current, width = buttonWidth,
                    onClick = { onValueChange(increase); onValueChangeFinished?.invoke() },
                )
            },
        ) { measurables, constraints ->
            val gap = spacing.xs.roundToPx()
            val controlWidth = buttonWidth.roundToPx()
            val desiredWidth = controlWidth * 2 + gap * 2 + valueText.size.width
            val width = constraints.constrainWidth(if (constraints.hasBoundedWidth) constraints.maxWidth else desiredWidth)
            val inline = width >= desiredWidth
            val buttonConstraints = Constraints.fixedWidth(controlWidth)
            val minus = measurables[0].measure(buttonConstraints)
            val plus = measurables[2].measure(buttonConstraints)
            val available = if (inline) width - controlWidth * 2 - gap * 2 else width
            val readout = measurables[1].measure(Constraints(maxWidth = available))
            val buttonsHeight = maxOf(minus.height, plus.height)
            val height = constraints.constrainHeight(
                if (inline) maxOf(buttonsHeight, readout.height) else readout.height + gap + buttonsHeight,
            )
            // Change placement, not composition: a growing number must not recreate the buttons
            // or lose keyboard focus when the readout moves above them.
            layout(width, height) {
                val buttonsY = if (inline) 0 else readout.height + gap
                val rowHeight = if (inline) height else buttonsHeight
                minus.placeRelative(0, buttonsY + (rowHeight - minus.height) / 2)
                readout.placeRelative((width - readout.width) / 2, if (inline) (height - readout.height) / 2 else 0)
                plus.placeRelative(width - plus.width, buttonsY + (rowHeight - plus.height) / 2)
            }
        }
    }
}

@Composable
private fun StepperButton(
    glyph: String,
    description: String,
    valueLabel: String,
    enabled: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val highlighted = enabled && pressed
    val foreground = when {
        !enabled -> colors.textSecondary
        highlighted -> colors.bg
        else -> colors.textPrimary
    }
    Box(
        modifier = Modifier.width(width).defaultMinSize(minHeight = dimens.touchTarget)
            .background(if (highlighted) colors.textPrimary else colors.surface)
            .border(
                width = if (enabled && focused) dimens.focusBorder else dimens.hairline,
                color = if (enabled && focused) foreground else colors.outline,
            )
            .clickable(
                interactionSource = source, indication = ripple(), enabled = enabled, role = Role.Button,
                // Direct semantics invocation also respects availability, like the other controls.
                onClick = { if (enabled) onClick() },
            )
            .semantics { contentDescription = description; stateDescription = valueLabel }
            .padding(NullWaveTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, modifier = Modifier.clearAndSetSemantics {}, style = NullWaveTheme.typography.h1, color = foreground)
    }
}
