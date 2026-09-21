package com.zaus.nullwave.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The Cyberdeck theme.
 *
 * Dark only, by design - there is no light mode and no dynamic colour. [variant] swaps the accent
 * tokens without touching layout.
 *
 * Reach the tokens through the [NullWaveTheme] object:
 * ```
 * Text(text = title, style = NullWaveTheme.typography.body, color = NullWaveTheme.colors.textPrimary)
 * ```
 *
 * A Material 3 [MaterialTheme] is installed underneath so that stock components (ripples, text
 * selection handles, the text cursor) pick up the right colours. Prefer the design system's own
 * components over Material ones - the system has no rounded pills, no FABs and no elevation.
 */
@Composable
fun NullWaveTheme(
    variant: NullWaveColorVariant = NullWaveColorVariant.Yellow,
    typography: NullWaveTypography = NullWaveTypography.Default,
    shapes: NullWaveShapes = NullWaveShapes.Default,
    spacing: NullWaveSpacing = NullWaveSpacing.Default,
    dimens: NullWaveDimens = NullWaveDimens.Default,
    motion: NullWaveMotion = NullWaveMotion.Default,
    content: @Composable () -> Unit,
) {
    val colors = variant.colors
    CompositionLocalProvider(
        LocalNullWaveColors provides colors,
        LocalNullWaveTypography provides typography,
        LocalNullWaveShapes provides shapes,
        LocalNullWaveSpacing provides spacing,
        LocalNullWaveDimens provides dimens,
        LocalNullWaveMotion provides motion,
        LocalContentColor provides colors.textPrimary,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            content = content,
        )
    }
}

/** Token accessors. Use these instead of reading the composition locals directly. */
object NullWaveTheme {
    val colors: NullWaveColors
        @Composable @ReadOnlyComposable get() = LocalNullWaveColors.current

    val typography: NullWaveTypography
        @Composable @ReadOnlyComposable get() = LocalNullWaveTypography.current

    val shapes: NullWaveShapes
        @Composable @ReadOnlyComposable get() = LocalNullWaveShapes.current

    val spacing: NullWaveSpacing
        @Composable @ReadOnlyComposable get() = LocalNullWaveSpacing.current

    val dimens: NullWaveDimens
        @Composable @ReadOnlyComposable get() = LocalNullWaveDimens.current

    val motion: NullWaveMotion
        @Composable @ReadOnlyComposable get() = LocalNullWaveMotion.current
}

val LocalNullWaveColors: ProvidableCompositionLocal<NullWaveColors> =
    staticCompositionLocalOf { NullWaveColors.Yellow }

val LocalNullWaveTypography: ProvidableCompositionLocal<NullWaveTypography> =
    staticCompositionLocalOf { NullWaveTypography.Default }

val LocalNullWaveShapes: ProvidableCompositionLocal<NullWaveShapes> =
    staticCompositionLocalOf { NullWaveShapes.Default }

val LocalNullWaveSpacing: ProvidableCompositionLocal<NullWaveSpacing> =
    staticCompositionLocalOf { NullWaveSpacing.Default }

val LocalNullWaveDimens: ProvidableCompositionLocal<NullWaveDimens> =
    staticCompositionLocalOf { NullWaveDimens.Default }

val LocalNullWaveMotion: ProvidableCompositionLocal<NullWaveMotion> =
    staticCompositionLocalOf { NullWaveMotion.Default }

/**
 * Bridges the tokens onto Material 3's slots. Only there so stock widgets do not render in
 * Material's default purple; the design system does not otherwise route through Material.
 */
private fun NullWaveColors.toMaterialColorScheme() = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = surfaceRaised,
    onPrimaryContainer = primary,
    secondary = secondary,
    onSecondary = bg,
    secondaryContainer = surfaceRaised,
    onSecondaryContainer = secondary,
    tertiary = success,
    onTertiary = bg,
    background = bg,
    onBackground = textPrimary,
    surface = surface,
    onSurface = textPrimary,
    surfaceVariant = surfaceRaised,
    onSurfaceVariant = textSecondary,
    surfaceContainer = surfaceRaised,
    surfaceContainerHigh = surfaceRaised,
    surfaceContainerHighest = surfaceRaised,
    surfaceContainerLow = surface,
    surfaceContainerLowest = bg,
    inverseSurface = textPrimary,
    inverseOnSurface = bg,
    error = danger,
    onError = bg,
    errorContainer = surfaceRaised,
    onErrorContainer = danger,
    outline = outline,
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
