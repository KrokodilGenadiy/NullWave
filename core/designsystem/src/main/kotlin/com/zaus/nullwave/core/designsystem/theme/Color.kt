package com.zaus.nullwave.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Dark Cyberdeck palette. Accents change with the variant; layout and text colours do not. */
@Immutable
data class NullWaveColors(
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    /** Decorative dividers, not the sole indication that a control is interactive. */
    val outline: Color,
    val primary: Color,
    val secondary: Color,
    val danger: Color,
    val success: Color,
    val textPrimary: Color,
    val textSecondary: Color,
) {
    val onPrimary: Color get() = bg

    companion object {
        val Cyberpunk = NullWaveColors(
            bg = Color(0xFF07090B),
            surface = Color(0xFF0E1317),
            surfaceRaised = Color(0xFF141A20),
            outline = Color(0xFF243038),
            primary = Color(0xFFFCEE0A),
            secondary = Color(0xFF00F0FF),
            danger = Color(0xFFFF2E3C),
            success = Color(0xFF39FF88),
            textPrimary = Color(0xFFE6EDF2),
            textSecondary = Color(0xFF93A3AD),
        )
        val Arasaka = Cyberpunk.copy(
            primary = Color(0xFFFF2E3C),
            secondary = Color(0xFFFF6B52),
            danger = Color(0xFFFCEE0A),
        )
        val Braindance = Cyberpunk.copy(
            primary = Color(0xFF00F0FF),
            secondary = Color(0xFF39FF88),
        )
    }
}

enum class NullWaveColorVariant(val colors: NullWaveColors) {
    Cyberpunk(NullWaveColors.Cyberpunk),
    Arasaka(NullWaveColors.Arasaka),
    Braindance(NullWaveColors.Braindance),
}
