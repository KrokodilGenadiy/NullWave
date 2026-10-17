package com.zaus.nullwave.core.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Dark only, matching the reference. No wallpaper colours or automatic light palette.
 * [effects] updates descendants live; nested themes inherit and may only restrict parent effects.
 * Components additionally respect system animation scale, including zero. Static glow is independent.
 */
@Composable
fun NullWaveTheme(
    variant: NullWaveColorVariant = NullWaveColorVariant.Cyberpunk,
    typography: NullWaveTypography = NullWaveTypography.Default,
    shapes: NullWaveShapes = NullWaveShapes.Default,
    spacing: NullWaveSpacing = NullWaveSpacing.Default,
    dimens: NullWaveDimens = NullWaveDimens.Default,
    effects: NullWaveEffects = NullWaveEffects.Default,
    content: @Composable () -> Unit,
) {
    val colors = variant.colors
    val effectiveEffects = LocalEffects.current.withRestrictions(effects.reduceMotion, effects.glowEnabled)
    val materialColors = remember(colors) { colors.toMaterialColorScheme() }
    val materialTypography = remember(typography) { typography.toMaterialTypography() }
    val materialShapes = remember(shapes) {
        Shapes(
            extraSmall = shapes.small,
            small = shapes.small,
            medium = shapes.medium,
            large = shapes.large,
            extraLarge = shapes.large,
        )
    }
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
        LocalShapes provides shapes,
        LocalSpacing provides spacing,
        LocalDimens provides dimens,
        LocalEffects provides effectiveEffects,
        LocalContentColor provides colors.textPrimary,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = materialTypography,
            shapes = materialShapes,
            content = content,
        )
    }
}

object NullWaveTheme {
    val colors: NullWaveColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val typography: NullWaveTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
    val shapes: NullWaveShapes
        @Composable @ReadOnlyComposable get() = LocalShapes.current
    val spacing: NullWaveSpacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val dimens: NullWaveDimens
        @Composable @ReadOnlyComposable get() = LocalDimens.current
    val effects: NullWaveEffects
        @Composable @ReadOnlyComposable get() = LocalEffects.current
}

private val LocalColors = staticCompositionLocalOf { NullWaveColors.Cyberpunk }
private val LocalTypography = staticCompositionLocalOf { NullWaveTypography.Default }
private val LocalShapes = staticCompositionLocalOf { NullWaveShapes.Default }
private val LocalSpacing = staticCompositionLocalOf { NullWaveSpacing.Default }
private val LocalDimens = staticCompositionLocalOf { NullWaveDimens.Default }
// Preferences change at runtime; invalidate readers rather than every descendant of the theme.
private val LocalEffects = compositionLocalOf { NullWaveEffects.Default }

private fun NullWaveColors.toMaterialColorScheme() = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = surfaceRaised,
    onPrimaryContainer = primary,
    inversePrimary = bg,
    secondary = secondary,
    onSecondary = bg,
    secondaryContainer = surfaceRaised,
    onSecondaryContainer = secondary,
    tertiary = success,
    onTertiary = bg,
    tertiaryContainer = surfaceRaised,
    onTertiaryContainer = success,
    background = bg,
    onBackground = textPrimary,
    surface = surface,
    onSurface = textPrimary,
    surfaceVariant = surfaceRaised,
    onSurfaceVariant = textSecondary,
    surfaceTint = primary,
    surfaceBright = surfaceRaised,
    surfaceDim = bg,
    surfaceContainerLowest = bg,
    surfaceContainerLow = surface,
    surfaceContainer = surfaceRaised,
    surfaceContainerHigh = surfaceRaised,
    surfaceContainerHighest = surfaceRaised,
    inverseSurface = textPrimary,
    inverseOnSurface = bg,
    error = danger,
    onError = bg,
    errorContainer = surfaceRaised,
    onErrorContainer = danger,
    outline = textSecondary,
    outlineVariant = outline,
    scrim = bg,
)

private fun NullWaveTypography.toMaterialTypography() = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = display,
    headlineLarge = h1,
    headlineMedium = h1,
    headlineSmall = h2,
    titleLarge = h1,
    titleMedium = h2,
    titleSmall = h2,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = button,
    labelMedium = tab,
    labelSmall = micro,
)
