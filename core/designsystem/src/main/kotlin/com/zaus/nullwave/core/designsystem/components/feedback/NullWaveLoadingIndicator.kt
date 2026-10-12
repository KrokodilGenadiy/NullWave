package com.zaus.nullwave.core.designsystem.components.feedback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Indeterminate status with a visible label and a static three-bar busy mark.
 * Deliberately motion-free until the shared motion policy is implemented. No fake percentage.
 * Show one indicator for a loading region, rather than labelling every skeleton row.
 */
@Composable
fun NullWaveLoadingIndicator(
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.nw_loading),
) {
    require(label.isNotBlank()) { "Loading label must not be blank" }
    val colors = NullWaveTheme.colors
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = label }
            .progressSemantics(),
        horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(NullWaveTheme.dimens.icon)) {
            // Proportions keep the mark distinct from an ellipsis, at any supplied theme size.
            val barWidth = size.width / 6f
            val gap = barWidth
            val heights = floatArrayOf(0.4f, 0.8f, 0.6f)
            heights.forEachIndexed { index, fraction ->
                val height = size.height * fraction
                drawRect(
                    color = colors.primary,
                    topLeft = Offset(barWidth / 2f + index * (barWidth + gap), (size.height - height) / 2f),
                    size = Size(barWidth, height),
                )
            }
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {},
            color = colors.textSecondary,
            style = NullWaveTheme.typography.body,
        )
    }
}
