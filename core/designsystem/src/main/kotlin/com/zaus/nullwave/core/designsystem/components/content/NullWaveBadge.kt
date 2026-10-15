package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSurface
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

enum class NullWaveBadgeVariant { Neutral, Primary, Secondary }

/**
 * Noninteractive label, not a chip or a quality classifier. Text carries meaning, color decorates it.
 * Preserves case, wraps at large font sizes, and exposes the full label to accessibility.
 */
@Composable
fun NullWaveBadge(
    label: String,
    modifier: Modifier = Modifier,
    variant: NullWaveBadgeVariant = NullWaveBadgeVariant.Neutral,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.ClippedCorners,
) {
    require(label.isNotBlank()) { "Badge label must not be blank" }
    val colors = NullWaveTheme.colors
    val tint = when (variant) {
        NullWaveBadgeVariant.Neutral -> colors.textSecondary
        NullWaveBadgeVariant.Primary -> colors.primary
        NullWaveBadgeVariant.Secondary -> colors.secondary
    }
    NullWaveSurface(
        modifier = modifier,
        shape = NullWaveTheme.shapes.small,
        color = colors.surfaceRaised,
        contentColor = tint,
        border = BorderStroke(NullWaveTheme.dimens.hairline, if (variant == NullWaveBadgeVariant.Neutral) colors.outline else tint),
        borderStyle = borderStyle,
    ) {
        Text(label, Modifier.padding(horizontal = NullWaveTheme.spacing.xs, vertical = NullWaveTheme.spacing.xxs), style = NullWaveTheme.typography.mono)
    }
}
