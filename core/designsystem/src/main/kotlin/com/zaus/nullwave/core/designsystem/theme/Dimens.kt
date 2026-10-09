package com.zaus.nullwave.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class NullWaveSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
) {
    companion object { val Default = NullWaveSpacing() }
}

/** Shared measurements only. Component-specific sizes belong with their future components. */
@Immutable
data class NullWaveDimens(
    val gutter: Dp = 16.dp,
    /** Minimum size, not a fixed height: controls must grow with font scaling. */
    val touchTarget: Dp = 48.dp,
    val hairline: Dp = 1.dp,
    val icon: Dp = 24.dp,
) {
    companion object { val Default = NullWaveDimens() }
}
