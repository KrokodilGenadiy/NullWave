package com.zaus.nullwave.core.designsystem.components.primitive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Centred content clipped to the theme's octagon. Caller supplies size and any inner padding.
 * A decorative frame only: selection, clicks and touch targets belong to the enclosing control.
 */
@Composable
fun NullWaveOctagonFrame(
    modifier: Modifier = Modifier,
    color: Color = NullWaveTheme.colors.surface,
    contentColor: Color = contentColorFor(color),
    border: BorderStroke? = BorderStroke(
        NullWaveTheme.dimens.hairline,
        NullWaveTheme.colors.outline,
    ),
    content: @Composable BoxScope.() -> Unit = {},
) {
    NullWaveSurface(
        modifier = modifier,
        shape = NullWaveTheme.shapes.node,
        color = color,
        contentColor = contentColor,
        border = border,
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}
