package com.zaus.nullwave.core.designsystem.components.primitive

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Non-interactive, clipped container. Padding and layout belong to its content.
 * Uses exact palette colours even inside an elevated Material surface.
 * For a custom background, pass a matching [contentColor] explicitly.
 */
@Composable
fun NullWaveSurface(
    modifier: Modifier = Modifier,
    shape: Shape = NullWaveTheme.shapes.medium,
    color: Color = NullWaveTheme.colors.surface,
    contentColor: Color = contentColorFor(color),
    border: BorderStroke? = BorderStroke(
        NullWaveTheme.dimens.hairline,
        NullWaveTheme.colors.outline,
    ),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTonalElevationEnabled provides false) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border,
            content = content,
        )
    }
}
