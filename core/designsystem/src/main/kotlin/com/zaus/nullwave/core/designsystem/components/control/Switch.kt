package com.zaus.nullwave.core.designsystem.components.control

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.modifier.chamferBorder
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

/**
 * The chamfered rocker that replaces Material's switch, whose rounded pill breaks the no-radii rule.
 *
 * **Not a sliding thumb.** The component sheet shows two fixed halves with `ON` and `OFF` both
 * permanently visible, and the *active* half filled:
 *
 * - checked - `ON` half filled `primary` with a dark label; `OFF` half transparent, dim
 * - unchecked - `OFF` half filled `outline` with a light label; `ON` half transparent, dim
 *
 * So both states are legible at rest, with no need to infer meaning from a thumb's position. Only
 * the fill colours animate, which is draw-phase work and never re-measures anything.
 *
 * 64dp x 26dp, chamfer 6 - straight from the sheet.
 */
@Composable
fun NullWaveSwitch(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    val colors = NullWaveTheme.colors

    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = isChecked,
                enabled = isEnabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .size(width = TrackWidth, height = TrackHeight)
            .clip(TrackShape)
            .chamferBorder(colors.outline, TrackShape),
    ) {
        RockerHalf(
            label = "ON",
            isActive = isChecked,
            activeFill = if (isEnabled) colors.primary else colors.outline,
            activeContent = if (isEnabled) colors.onPrimary else colors.textTertiary,
            isEnabled = isEnabled,
        )
        RockerHalf(
            label = "OFF",
            isActive = !isChecked,
            activeFill = colors.outline,
            activeContent = if (isEnabled) colors.textPrimary else colors.textTertiary,
            isEnabled = isEnabled,
        )
    }
}

@Composable
private fun RowScope.RockerHalf(
    label: String,
    isActive: Boolean,
    activeFill: Color,
    activeContent: Color,
    isEnabled: Boolean,
) {
    val colors = NullWaveTheme.colors
    val motion = NullWaveTheme.motion
    val spec = tween<Color>(motion.stateMillis, easing = motion.easing)

    val fill by animateColorAsState(
        targetValue = if (isActive) activeFill else Color.Transparent,
        animationSpec = spec,
        label = "rockerFill",
    )
    val content by animateColorAsState(
        targetValue = if (isActive) activeContent else colors.textTertiary,
        animationSpec = spec,
        label = "rockerContent",
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        NullWaveMicroLabel(text = label, color = content)
    }
}

private val TrackWidth = 64.dp
private val TrackHeight = 26.dp
private val TrackShape = chamfer(6.dp)

@Preview(name = "Switch", showBackground = true)
@Composable
private fun SwitchPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Column(
            modifier = Modifier
                .background(colors.bg)
                .padding(NullWaveTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NullWaveSwitch(isChecked = true, onCheckedChange = {})
                NullWaveSwitch(isChecked = false, onCheckedChange = {})
                NullWaveMicroLabel("on / off")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NullWaveSwitch(isChecked = true, onCheckedChange = {}, isEnabled = false)
                NullWaveSwitch(isChecked = false, onCheckedChange = {}, isEnabled = false)
                NullWaveMicroLabel("disabled")
            }
        }
    }
}
