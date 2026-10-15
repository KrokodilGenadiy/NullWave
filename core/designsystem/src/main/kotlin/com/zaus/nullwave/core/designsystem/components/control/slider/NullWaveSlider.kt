package com.zaus.nullwave.core.designsystem.components.control.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Controlled horizontal slider. Update [value] from [onValueChange]; completion is a separate event.
 * [steps] counts interior stops (0 is continuous). Finite values clamp and snap to the given range.
 * [label] is the accessible name; [valueDescription] can supply localized units instead of a number.
 * Caller must allow at least 48 dp height; 176 dp is the preferred minimum width, not a forced overflow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NullWaveSlider(
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
) {
    NullWaveSliderControl(value, onValueChange, label, modifier, enabled, valueRange, steps,
        onValueChangeFinished, valueDescription, interactionSource)
}

/** Shared Material interaction/semantics shell; SeekBar supplies its timeline drawing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NullWaveSliderControl(
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
    thumb: (@Composable (SliderState) -> Unit)? = null,
    track: (@Composable (SliderState) -> Unit)? = null,
) {
    validateSliderRange(valueRange, steps)
    require(label.isNotBlank()) { "Slider needs an accessible label" }
    require(valueDescription == null || valueDescription.isNotBlank()) { "Value description must not be blank" }
    val displayed = snapSliderValue(value, valueRange, steps)
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    Slider(
        value = displayed,
        onValueChange = { if (enabled && it.isFinite()) onValueChange(snapSliderValue(it, valueRange, steps)) },
        onValueChangeFinished = { if (enabled) onValueChangeFinished?.invoke() },
        enabled = enabled, valueRange = valueRange, steps = steps, interactionSource = source,
        modifier = modifier.defaultMinSize(minWidth = SliderMinWidth, minHeight = dimens.touchTarget)
            .then(if (enabled && focused) Modifier.border(dimens.focusBorder, colors.textPrimary, NullWaveTheme.shapes.small) else Modifier)
            .semantics {
                contentDescription = label
                if (valueDescription != null) stateDescription = valueDescription
            },
        thumb = thumb ?: { NullWaveSliderNode(enabled, it.coercedValueAsFraction > 0f) },
        track = track ?: { NullWaveSliderTrack(it, enabled) },
    )
}

@Composable
internal fun NullWaveSliderNode(enabled: Boolean, active: Boolean = true) {
    val colors = NullWaveTheme.colors
    val shape = NullWaveTheme.shapes.node
    Box(Modifier.size(SliderNodeSize).background(colors.surface, shape)
        .border(NullWaveTheme.dimens.hairline, if (enabled && active) colors.primary else colors.outline, shape))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NullWaveSliderTrack(state: SliderState, enabled: Boolean) {
    val colors = NullWaveTheme.colors
    Canvas(Modifier.fillMaxWidth().height(SliderTrackHeight)) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        val start = if (rtl) size.width else 0f
        val end = if (rtl) 0f else size.width
        val position = start + (end - start) * state.coercedValueAsFraction
        fun drawTrack() {
            drawLine(colors.outline, Offset(start, center.y), Offset(end, center.y), size.height, StrokeCap.Butt)
            drawLine(if (enabled) colors.primary else colors.textSecondary, Offset(start, center.y), Offset(position, center.y), size.height, StrokeCap.Butt)
            // Sparse steps are useful ticks; dense steps still snap, without painting a solid dot band.
            if (state.steps in 1..MaxVisibleSteps) for (index in 0..state.steps + 1) {
                val fraction = index.toFloat() / (state.steps + 1)
                val x = start + (end - start) * fraction
                drawCircle(if (fraction <= state.coercedValueAsFraction) colors.surface else colors.textSecondary,
                    radius = size.height / 4f, center = Offset(x, center.y))
            }
        }
        // Material draws the track after its thumb slot; leave room so it cannot cross the node.
        val halfNode = SliderNodeSize.toPx() / 2f
        clipRect(right = (position - halfNode).coerceAtLeast(0f)) { drawTrack() }
        clipRect(left = (position + halfNode).coerceAtMost(size.width)) { drawTrack() }
    }
}

internal val SliderNodeSize = 28.dp
private val SliderMinWidth = 176.dp
private val SliderTrackHeight = 4.dp
private const val MaxVisibleSteps = 20
