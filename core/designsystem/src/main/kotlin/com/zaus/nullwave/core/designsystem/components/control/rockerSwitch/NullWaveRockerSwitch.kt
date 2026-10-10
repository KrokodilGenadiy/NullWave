package com.zaus.nullwave.core.designsystem.components.control.rockerSwitch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.modifier.nullWaveBorder
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

/**
 * A single binary switch, not two buttons: either half toggles the externally owned [checked].
 * The fixed ON/OFF halves follow artboard 24. Labels are localized and the body grows with text.
 * Its minimum body is 64 x 26 dp inside a layout of at least 64 x 48 dp (parents must allow it).
 *
 * Supply a localized feature name via Modifier.semantics when standalone. The visual ON/OFF
 * labels are decorative; toggleable supplies Switch role and checked/disabled state.
 * Use null [onCheckedChange] in a parent toggleable row, which then owns the name, role, state,
 * focus and callback. This control adds no semantics or action in that mode.
 * [borderStyle] affects the compact body only; the outer keyboard-focus outline stays closed.
 */
@Composable
fun NullWaveRockerSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val onLabel = stringResource(R.string.nw_switch_on)
    val offLabel = stringResource(R.string.nw_switch_off)
    val action = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = source,
            indication = ripple(),
            onValueChange = { if (enabled) onCheckedChange(it) },
        )
    } else Modifier

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = RockerWidth, minHeight = dimens.touchTarget)
            .clip(NullWaveTheme.shapes.small)
            .then(action)
            .then(
                if (enabled && onCheckedChange != null && focused) {
                    Modifier.border(dimens.focusBorder, colors.textPrimary, NullWaveTheme.shapes.small)
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                // Equal-width halves use the larger label's intrinsic width; both labels are always
                // measured, so switching never changes the width, including at large font scales.
                .width(IntrinsicSize.Max).height(IntrinsicSize.Min)
                .defaultMinSize(minWidth = RockerWidth, minHeight = RockerHeight)
                .clip(RockerShape)
                .nullWaveBorder(BorderStroke(dimens.hairline, colors.textSecondary), RockerShape, borderStyle)
                .clearAndSetSemantics {},
        ) {
            for ((label, active) in listOf(onLabel to checked, offLabel to !checked)) {
                val fill = when {
                    !active -> Color.Transparent
                    !enabled -> colors.surfaceRaised
                    checked -> colors.primary
                    else -> colors.outline
                }
                val content = when {
                    !enabled -> colors.textSecondary
                    active && checked -> colors.bg
                    active -> colors.textPrimary
                    else -> colors.textSecondary
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().background(fill)
                        .then(if (active && !enabled) Modifier.border(dimens.hairline, colors.textSecondary) else Modifier)
                        .padding(horizontal = NullWaveTheme.spacing.xxs, vertical = NullWaveTheme.spacing.xxs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = NullWaveTheme.typography.mono, color = content, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private val RockerWidth = 64.dp
private val RockerHeight = 26.dp
private val RockerShape = chamfer(6.dp)
