package com.zaus.nullwave.core.designsystem.components.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.modifier.nullWaveBorder
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * One selectable destination. Place sibling destinations in a selectableGroup in the caller.
 * Selection uses a leading spine (mirrored in RTL), raised surface and accent, independent of focus.
 * [badge] is compact, noninteractive content, merged with the label for accessibility. Icons are
 * decorative. Labels wrap and the row grows from 52 dp; routing and drawer dismissal stay outside.
 * Disabled items keep the selected geometry with neutral colors, ignoring the active accent.
 */
@Composable
fun NullWaveDrawerItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    badge: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
) {
    require(label.isNotBlank()) { "Drawer label must not be blank" }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val shape = NullWaveTheme.shapes.small
    val accent = when {
        !enabled -> colors.textSecondary
        selected -> colors.primary
        else -> colors.textPrimary
    }
    CompositionLocalProvider(LocalContentColor provides accent) {
        Row(
            modifier.fillMaxWidth().defaultMinSize(minHeight = DrawerItemMinHeight)
                .clip(shape)
                .background(if (selected || enabled && pressed) colors.surfaceRaised else colors.surface)
                .drawBehind {
                    if (selected) {
                        val width = DrawerSpineWidth.toPx().coerceAtMost(size.width)
                        val start = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
                        drawRect(accent, Offset(start, 0f), Size(width, size.height))
                    }
                }
                .then(
                    if (enabled && focused) Modifier.nullWaveBorder(
                        BorderStroke(dimens.focusBorder, colors.textPrimary), shape, NullWaveBorderStyle.Closed,
                    ) else if (selected) Modifier.nullWaveBorder(
                        BorderStroke(dimens.hairline, colors.outline), shape, borderStyle,
                    ) else Modifier,
                )
                .selectable(
                    selected = selected, enabled = enabled, role = Role.Tab,
                    interactionSource = source, indication = ripple(),
                    onClick = { if (enabled) onClick() },
                )
                .padding(horizontal = NullWaveTheme.spacing.md, vertical = NullWaveTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) NullWaveIcon(icon, contentDescription = null)
            Text(label, Modifier.weight(1f), style = NullWaveTheme.typography.bodyEmphasis)
            badge?.invoke()
        }
    }
}

private val DrawerItemMinHeight = 52.dp
private val DrawerSpineWidth = 3.dp
