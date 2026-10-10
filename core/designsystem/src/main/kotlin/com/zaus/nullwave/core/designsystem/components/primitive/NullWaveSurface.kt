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
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.modifier.nullWaveBorder
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
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTonalElevationEnabled provides false) {
        Surface(
            modifier = modifier.then(
                if (border != null && borderStyle == NullWaveBorderStyle.ClippedCorners) {
                    Modifier.nullWaveBorder(border, shape, borderStyle)
                } else Modifier,
            ),
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = if (borderStyle == NullWaveBorderStyle.Closed) border else null,
            content = content,
        )
    }
}
