package com.zaus.nullwave.core.designsystem.components.overlay

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.modifier.nullWaveBorder
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * One action/one accessibility target, not a selectable item. Optional icon is decorative.
 * Labels wrap; height grows from 48 dp. Parent constraints must allow that minimum.
 * A click does not automatically dismiss any enclosing panel. Dangerous actions still need the
 * caller's confirmation policy; [danger] only changes styling. Focus always has a closed border.
 */
@Composable
fun NullWaveMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
) {
    require(label.isNotBlank()) { "Menu label must not be blank" }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val colors = NullWaveTheme.colors
    val spacing = NullWaveTheme.spacing
    val dimens = NullWaveTheme.dimens
    val shape = NullWaveTheme.shapes.small
    val isFocused = enabled && focused
    val accent = when {
        !enabled -> colors.textSecondary
        danger -> colors.danger
        else -> colors.textPrimary
    }
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = dimens.touchTarget)
            .clip(shape)
            .background(if (enabled && pressed) colors.surfaceRaised else colors.surface)
            .then(
                if (isFocused || danger) Modifier.nullWaveBorder(
                    BorderStroke(
                        if (isFocused) dimens.focusBorder else dimens.hairline,
                        if (isFocused) colors.textPrimary else if (enabled) colors.danger else colors.outline,
                    ),
                    shape,
                    if (isFocused) NullWaveBorderStyle.Closed else borderStyle,
                ) else Modifier,
            )
            .clickable(
                interactionSource = source, indication = ripple(), enabled = enabled, role = Role.Button,
                // Also guard direct semantics invocation in the current Foundation version.
                onClick = { if (enabled) onClick() },
            )
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) NullWaveIcon(leadingIcon, contentDescription = null, tint = accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(label, color = accent, style = NullWaveTheme.typography.bodyEmphasis)
            if (!supportingText.isNullOrBlank()) {
                Text(supportingText, color = colors.textSecondary, style = NullWaveTheme.typography.caption)
            }
        }
    }
}
