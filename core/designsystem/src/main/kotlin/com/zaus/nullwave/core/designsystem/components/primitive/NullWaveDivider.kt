package com.zaus.nullwave.core.designsystem.components.primitive

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Decorative horizontal separator; does not introduce an accessibility node. */
@Composable
fun NullWaveDivider(
    modifier: Modifier = Modifier,
    color: Color = NullWaveTheme.colors.outline,
    thickness: Dp = NullWaveTheme.dimens.hairline,
) {
    HorizontalDivider(modifier = modifier, thickness = thickness, color = color)
}
