package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSurface
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** A details field: prose uses caption, technical values may opt into mono. Neither is truncated. */
@Composable
fun NullWaveMetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
) {
    require(label.isNotBlank() && value.isNotBlank()) { "Metadata needs a label and a display value" }
    val colors = NullWaveTheme.colors
    val typography = NullWaveTheme.typography
    val spacing = NullWaveTheme.spacing
    BoxWithConstraints(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        if (maxWidth < InlineMetadataWidth || LocalDensity.current.fontScale > InlineMetadataFontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(label, color = colors.textSecondary, style = typography.mono)
                Text(value, color = colors.textPrimary, style = if (monospace) typography.mono else typography.caption)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(label, Modifier.weight(1f), color = colors.textSecondary, style = typography.mono)
                Text(value, Modifier.weight(2f), color = colors.textPrimary, style = if (monospace) typography.mono else typography.caption)
            }
        }
    }
}

/** Already formatted display strings, not counts/durations to be calculated by the design system. */
@Immutable
data class NullWaveStat(val label: String, val value: String) {
    init { require(label.isNotBlank() && value.isNotBlank()) { "Statistic needs a label and a display value" } }
}

/**
 * Read-only summary panel. At most three equal-width cells per row; narrow widths/large fonts
 * reduce the column count. Text wraps without truncation and each label/value is one reading group.
 * Empty input renders nothing. The caller owns localization, formatting and missing-value policy.
 */
@Composable
fun NullWaveStatBar(
    stats: List<NullWaveStat>,
    modifier: Modifier = Modifier,
) {
    if (stats.isEmpty()) return
    NullWaveSurface(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.padding(NullWaveTheme.spacing.sm)) {
            val spacing = NullWaveTheme.spacing.sm
            val cellWidth = StatMinWidth * LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columns = ((maxWidth + spacing) / (cellWidth + spacing)).toInt().coerceIn(1, MaxStatColumns)
            FlowRow(
                Modifier.fillMaxWidth(), maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                stats.forEach { stat ->
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs)) {
                        Text(stat.label, style = NullWaveTheme.typography.mono, color = NullWaveTheme.colors.textSecondary)
                        Text(stat.value, style = NullWaveTheme.typography.h2, color = NullWaveTheme.colors.textPrimary)
                    }
                }
            }
        }
    }
}

private val InlineMetadataWidth = 360.dp
private const val InlineMetadataFontScale = 1.3f
private val StatMinWidth = 96.dp
private const val MaxStatColumns = 3
