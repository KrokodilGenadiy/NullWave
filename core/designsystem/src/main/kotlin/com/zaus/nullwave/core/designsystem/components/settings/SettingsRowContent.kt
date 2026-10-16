package com.zaus.nullwave.core.designsystem.components.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Shared appearance only. The public row supplies exactly one clickable/toggleable modifier. */
@Composable
internal fun SettingsRowContent(
    label: String,
    supportingText: String?,
    @DrawableRes leadingIcon: Int?,
    enabled: Boolean,
    source: MutableInteractionSource,
    modifier: Modifier,
    action: Modifier,
    adaptiveTrailing: Boolean = false,
    value: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    require(label.isNotBlank()) { "Setting label must not be blank" }
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val colors = NullWaveTheme.colors
    val spacing = NullWaveTheme.spacing
    val dimens = NullWaveTheme.dimens
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = SettingRowMinHeight)
            .clip(RectangleShape)
            .background(if (enabled && pressed) colors.surfaceRaised else colors.bg)
            .drawBehind {
                val stroke = dimens.hairline.toPx()
                drawLine(colors.outline, Offset(0f, size.height - stroke / 2f),
                    Offset(size.width, size.height - stroke / 2f), stroke)
            }
            .then(if (enabled && focused) Modifier.border(dimens.focusBorder, colors.textPrimary, RectangleShape) else Modifier)
            .then(action)
            .padding(horizontal = dimens.gutter, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) NullWaveIcon(leadingIcon, contentDescription = null, tint = colors.textSecondary)
        BoxWithConstraints(Modifier.weight(1f)) {
            val density = LocalDensity.current
            var stacked = adaptiveTrailing && (maxWidth < InlineControlMinWidth || density.fontScale > InlineControlMaxFontScale)
            if (value != null && !stacked) {
                val measured = rememberTextMeasurer().measure(value, style = NullWaveTheme.typography.mono, softWrap = false)
                val available = with(density) { (maxWidth - MinLabelWidth - spacing.sm).toPx() }
                stacked = measured.size.width > available || measured.lineCount > 1
            }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    SettingLabel(label, supportingText, enabled)
                    trailing?.invoke()
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SettingLabel(label, supportingText, enabled) }
                    trailing?.invoke()
                }
            }
        }
    }
}

@Composable
private fun SettingLabel(label: String, supportingText: String?, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs)) {
        Text(label, color = if (enabled) NullWaveTheme.colors.textPrimary else NullWaveTheme.colors.textSecondary,
            style = NullWaveTheme.typography.bodyEmphasis)
        if (!supportingText.isNullOrBlank()) Text(supportingText, color = NullWaveTheme.colors.textSecondary,
            style = NullWaveTheme.typography.mono)
    }
}

private val SettingRowMinHeight = 64.dp
private val InlineControlMinWidth = 240.dp
private const val InlineControlMaxFontScale = 1.3f
private val MinLabelWidth = 160.dp
