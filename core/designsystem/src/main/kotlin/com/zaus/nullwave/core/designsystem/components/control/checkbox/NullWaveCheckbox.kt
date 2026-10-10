package com.zaus.nullwave.core.designsystem.components.control.checkbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

/**
 * Two-state selection with the legacy secondary-filled, chamfered box. [size] is the visual box;
 * the layout reserves at least 48 dp in either mode, provided parent constraints allow it.
 * [checked] belongs to the caller. Supply a localized name via Modifier.semantics when standalone.
 *
 * Use a null [onCheckedChange] inside a parent toggleable row: this control then has no action or
 * state semantics of its own. The parent owns the label, Checkbox role, checked/disabled state,
 * focus and callback. Null is NOT a substitute for enabled=false on a standalone control.
 * A supplied [interactionSource] remains owned by the caller.
 */
@Composable
fun NullWaveCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = DefaultCheckboxSize,
    interactionSource: MutableInteractionSource? = null,
) {
    require(size.value.isFinite() && size > 0.dp) { "Checkbox size must be finite and positive." }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val targetShape = NullWaveTheme.shapes.small
    val boxShape = remember(size) { chamfer(size / 6f) }
    val accent = if (enabled) colors.secondary else colors.textSecondary
    val action = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            interactionSource = source,
            indication = ripple(),
            onValueChange = { if (enabled) onCheckedChange(it) },
        )
    } else Modifier

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = dimens.touchTarget, minHeight = dimens.touchTarget)
            .clip(targetShape)
            .then(action)
            .then(
                if (enabled && onCheckedChange != null && focused) {
                    Modifier.border(dimens.focusBorder, colors.textPrimary, targetShape)
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(size).clip(boxShape)
                .background(if (checked) accent else Color.Transparent)
                .border(
                    dimens.hairline,
                    when {
                        checked -> accent
                        enabled -> colors.textSecondary
                        else -> colors.outline
                    },
                    boxShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                NullWaveIcon(
                    icon = NullWaveIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(size / 2f),
                    tint = colors.bg,
                )
            }
        }
    }
}

private val DefaultCheckboxSize = 48.dp
