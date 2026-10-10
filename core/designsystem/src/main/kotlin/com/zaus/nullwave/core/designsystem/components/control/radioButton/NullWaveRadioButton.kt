package com.zaus.nullwave.core.designsystem.components.control.radioButton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * One option in a single-selection group. The caller owns [selected], supplies selectableGroup
 * on the group and decides which option [onClick] selects; clicking again does not imply deselect.
 * Supply a localized name via Modifier.semantics when standalone.
 *
 * Set [onClick] to null in a selectable row. The parent then owns all semantics and interactions;
 * this control is decorative. Use enabled=false (not null) for a standalone disabled option.
 * Both modes reserve at least 48 dp, subject to parent constraints. The glyph is 24 dp.
 */
@Composable
fun NullWaveRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val shape = NullWaveTheme.shapes.node
    val accent = if (enabled && selected) colors.primary else colors.textSecondary
    val action = if (onClick != null) {
        Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            interactionSource = source,
            indication = ripple(),
            onClick = { if (enabled) onClick() },
        )
    } else Modifier

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = dimens.touchTarget, minHeight = dimens.touchTarget)
            .clip(shape)
            .then(action)
            .then(
                if (enabled && onClick != null && focused) {
                    Modifier.border(dimens.focusBorder, colors.textPrimary, shape)
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(dimens.icon)
                .border(RadioStrokeWidth, if (enabled || selected) accent else colors.outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(RadioMarkerSize).background(accent, shape))
        }
    }
}

private val RadioMarkerSize = 10.dp
private val RadioStrokeWidth = 2.dp
