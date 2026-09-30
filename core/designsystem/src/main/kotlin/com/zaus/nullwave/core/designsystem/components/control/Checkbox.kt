package com.zaus.nullwave.core.designsystem.components.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

/**
 * The selection-mode checkbox. Chamfered, never rounded.
 *
 * **48dp by default, not a small tick box.** In selection mode it takes the place of a track row's
 * artwork, so the design sizes it to match: a solid `secondary` square with the tick in `bg`. The
 * design reviewed that solid fill and kept it deliberately.
 *
 * `secondary` owns selection throughout the app - the action bar, these boxes and the row tint all
 * use it, which is what keeps selection distinct from the `primary` "now playing" state.
 *
 * The chamfer scales with [size] rather than being fixed: 8dp of cut on the 48dp default, and
 * proportionally less on a smaller one, so a settings-sized box does not lose its corners entirely.
 *
 * `toggleable` with `Role.Checkbox` rather than `clickable` gives screen readers the checked state
 * and the right announcement for free.
 */
@Composable
fun NullWaveCheckbox(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    size: Dp = DefaultSize,
) {
    val colors = NullWaveTheme.colors
    val shape = chamfer(size / ChamferRatio)

    val fill = when {
        !isChecked -> Color.Transparent
        isEnabled -> colors.secondary
        else -> colors.outline
    }
    val borderColor = when {
        !isEnabled -> colors.outline
        isChecked -> colors.secondary
        else -> colors.textSecondary
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = isChecked,
                enabled = isEnabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .size(size)
            .clip(shape)
            .background(fill)
            // Closed border, unlike the button and chip. At 20dp with 6dp cuts an open bracket
            // removes roughly a third of every edge and reads as a rendering fault rather than a
            // style. Small controls keep their frame shut.
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (isChecked) {
            Icon(
                painter = painterResource(NullWaveIcons.Confirm),
                contentDescription = null,
                tint = if (isEnabled) colors.bg else colors.textTertiary,
                modifier = Modifier.size(size / TickRatio),
            )
        }
    }
}

/** The artwork slot it replaces in selection mode. */
private val DefaultSize = 48.dp

/** 48 / 6 = the design's 8dp cut, and it stays proportional at other sizes. */
private const val ChamferRatio = 6f

/** 48 / 2 = a 24dp tick, matching the icon grid. */
private const val TickRatio = 2f

@Preview(name = "Checkbox", showBackground = true)
@Composable
private fun CheckboxPreview() {
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
                NullWaveCheckbox(isChecked = false, onCheckedChange = {})
                NullWaveCheckbox(isChecked = true, onCheckedChange = {})
                NullWaveMicroLabel("unchecked / checked")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NullWaveCheckbox(isChecked = false, onCheckedChange = {}, isEnabled = false)
                NullWaveCheckbox(isChecked = true, onCheckedChange = {}, isEnabled = false)
                NullWaveMicroLabel("disabled")
            }
        }
    }
}
