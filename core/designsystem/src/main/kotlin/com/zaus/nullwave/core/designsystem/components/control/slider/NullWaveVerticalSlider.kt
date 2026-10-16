package com.zaus.nullwave.core.designsystem.components.control.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Controlled vertical range. Minimum is at the bottom, maximum at the top, including in RTL.
 * [steps] counts interior stops (0 is continuous); callbacks use the same clamp/snap as Slider.
 * Optional [neutralValue] marks a reference such as zero; the filled segment starts there instead
 * of the minimum. It does not change the range, stops, callbacks or accessibility value.
 *
 * Prefers 48 x 250 dp, respecting caller constraints; allow at least a 48 dp wide touch target.
 * Frequency/value labels, selected-band state and horizontal scrolling belong to the caller.
 * Up/Right increase, Down/Left decrease, Home/End reach bounds; PageUp/PageDown move larger steps.
 * [onValueChangeFinished] follows Material completion semantics, including drag cancellation.
 *
 * Material3 1.4 has no public vertical API. A measured rotation preserves its native pointer,
 * focus and progress semantics without copying gesture code or accessing internal APIs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NullWaveVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    valueDescription: String? = null,
    interactionSource: MutableInteractionSource? = null,
    neutralValue: Float? = null,
) {
    validateSliderRange(valueRange, steps)
    require(neutralValue == null || neutralValue.isFinite() && neutralValue in valueRange) {
        "Neutral value must be finite and inside the slider range"
    }
    val displayed = snapSliderValue(value, valueRange, steps)
    val origin = neutralValue ?: valueRange.start
    val originFraction = ((origin.toDouble() - valueRange.start) /
        (valueRange.endInclusive.toDouble() - valueRange.start)).toFloat()
    val touchTarget = NullWaveTheme.dimens.touchTarget
    var pageChanged by remember(enabled, valueRange, steps) { mutableStateOf(false) }
    Layout(
        modifier = modifier
            .onFocusChanged { if (!it.hasFocus) pageChanged = false }
            .onPreviewKeyEvent { event ->
                if (!enabled || event.key != Key.PageUp && event.key != Key.PageDown) false
                else when (event.type) {
                    KeyEventType.KeyDown -> {
                        val next = verticalSliderPageValue(displayed, valueRange, steps, event.key == Key.PageUp)
                        if (next != displayed) {
                            pageChanged = true
                            onValueChange(next)
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        val changed = pageChanged
                        pageChanged = false
                        if (changed) onValueChangeFinished?.invoke()
                        true
                    }
                    else -> false
                }
            },
        content = {
            // Horizontal Material input is rotated visually. RTL must not invert vertical values.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                NullWaveSliderControl(
                    value = displayed, onValueChange = onValueChange, label = label,
                    modifier = Modifier.graphicsLayer { rotationZ = -90f },
                    enabled = enabled, valueRange = valueRange, steps = steps,
                    onValueChangeFinished = onValueChangeFinished, valueDescription = valueDescription,
                    interactionSource = interactionSource,
                    thumb = { NullWaveSliderNode(enabled, displayed != origin) },
                    track = { NeutralSliderTrack(it, enabled, originFraction, neutralValue != null) },
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.constrainWidth(touchTarget.roundToPx())
        val height = constraints.constrainHeight(VerticalSliderHeight.roundToPx())
        val slider = measurables.single().measure(Constraints.fixed(width = height, height = width))
        layout(width, height) {
            // Rotate the child layer around the same centre as the upright layout/touch bounds.
            slider.place((width - height) / 2, (height - width) / 2)
        }
    }
}

/** Drawn horizontally inside the rotated control; the marker becomes a horizontal zero tick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeutralSliderTrack(state: SliderState, enabled: Boolean, originFraction: Float, showReference: Boolean) {
    val colors = NullWaveTheme.colors
    val hairline = NullWaveTheme.dimens.hairline
    Canvas(Modifier.fillMaxWidth().height(VerticalTrackWidth)) {
        val fraction = state.coercedValueAsFraction
        val position = size.width * fraction
        val origin = size.width * originFraction
        val active = if (enabled) colors.primary else colors.textSecondary
        fun drawTrack() {
            drawLine(colors.outline, Offset(0f, center.y), Offset(size.width, center.y), size.height, StrokeCap.Butt)
            drawLine(active, Offset(origin, center.y), Offset(position, center.y), size.height, StrokeCap.Butt)
            if (showReference) {
                val halfTick = ReferenceTickWidth.toPx() / 2f
                drawLine(colors.textSecondary, Offset(origin, center.y - halfTick),
                    Offset(origin, center.y + halfTick), hairline.toPx())
            }
            if (state.steps in 1..MaxVisibleVerticalSteps) for (index in 0..state.steps + 1) {
                val tickFraction = index.toFloat() / (state.steps + 1)
                val inFill = tickFraction in minOf(originFraction, fraction)..maxOf(originFraction, fraction)
                drawCircle(if (inFill) colors.surface else colors.textSecondary,
                    radius = size.height / 4f, center = Offset(size.width * tickFraction, center.y))
            }
        }
        // Material draws track after thumb; do not draw through the hollow octagonal node.
        val halfNode = SliderNodeSize.toPx() / 2f
        clipRect(right = (position - halfNode).coerceAtLeast(0f), top = -ReferenceTickWidth.toPx(),
            bottom = size.height + ReferenceTickWidth.toPx()) { drawTrack() }
        clipRect(left = (position + halfNode).coerceAtMost(size.width), top = -ReferenceTickWidth.toPx(),
            bottom = size.height + ReferenceTickWidth.toPx()) { drawTrack() }
    }
}

private val VerticalSliderHeight = 250.dp
private val VerticalTrackWidth = 4.dp
private val ReferenceTickWidth = 12.dp
private const val MaxVisibleVerticalSteps = 20
