package com.zaus.nullwave.core.designsystem.components.feedback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import java.text.NumberFormat

/**
 * Determinate, non-interactive progress. Finite values are clamped to 0..1 for both drawing and
 * semantics. Unknown totals belong in [NullWaveLoadingIndicator], not a fabricated fraction.
 * Values update immediately; operation lifetime and completion messages belong to the caller.
 */
@Composable
fun NullWaveProgress(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
) {
    require(progress.isFinite()) { "Progress must be finite" }
    require(label.isNotBlank()) { "Progress label must not be blank" }
    val fraction = progress.coerceIn(0f, 1f)
    val colors = NullWaveTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val percentage = formatter.format(fraction.toDouble())
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = label }
            .progressSemantics(fraction),
        verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs),
        ) {
            Text(label, color = colors.textPrimary, style = NullWaveTheme.typography.body)
            Text(percentage, color = colors.primary, style = NullWaveTheme.typography.mono)
        }
        Canvas(Modifier.fillMaxWidth().height(ProgressTrackHeight)) {
            drawRect(colors.outline)
            val width = size.width * fraction
            drawRect(
                color = colors.primary,
                topLeft = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f, 0f),
                size = Size(width, size.height),
            )
        }
    }
}

private val ProgressTrackHeight = 4.dp
