package com.zaus.nullwave.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens for the Cyberdeck design system.
 *
 * Dark only — there is no light mode. Three variants ([NullWaveColors.Yellow],
 * [NullWaveColors.Crimson], [NullWaveColors.Dive]) are pure token swaps: the same names, the same
 * layout, different accents.
 *
 * Rules the design system relies on:
 * - [primary] and [secondary] are the only colours that carry interaction.
 * - [danger] frames destructive actions; it never sets body text (it fails contrast at small sizes).
 * - Every text/background pair reaches 4.5:1, icons 3:1.
 * - Album art is the only place arbitrary saturated colour appears, and only as a low-opacity wash.
 */
@Immutable
data class NullWaveColors(
    /** Screen background. */
    val bg: Color,
    /** Cards, sheets, app bar. */
    val surface: Color,
    /** Selected rows, dialogs, drawer. */
    val surfaceRaised: Color,
    /** Default 1dp borders and dividers. */
    val outline: Color,
    /** Play button, active tab, focus, primary CTA, active glow. */
    val primary: Color,
    /** Pressed / disabled primary. */
    val primaryDim: Color,
    /** Seek progress, links, secondary icons, info. */
    val secondary: Color,
    /** Destructive actions, HUD framing accents, glitch channel. */
    val danger: Color,
    /** Scan complete, import OK. */
    val success: Color,
    /** Titles, track names. */
    val textPrimary: Color,
    /** Artist, metadata. */
    val textSecondary: Color,
    /** Mono micro-labels, disabled. */
    val textTertiary: Color,
    /** The OST-sleeve off-white. Generated cover art only — never UI chrome. */
    val paper: Color,
) {
    /** Content colour that reads on top of [primary] fills. */
    val onPrimary: Color get() = bg

    companion object {
        /** Default variant. Yellow primary, cyan secondary, red alert. */
        val Yellow: NullWaveColors = NullWaveColors(
            bg = Color(0xFF07090B),
            surface = Color(0xFF0E1317),
            surfaceRaised = Color(0xFF141A20),
            outline = Color(0xFF243038),
            primary = Color(0xFFFCEE0A),
            primaryDim = Color(0xFF8A8206),
            secondary = Color(0xFF00F0FF),
            danger = Color(0xFFFF2E3C),
            success = Color(0xFF39FF88),
            textPrimary = Color(0xFFE6EDF2),
            textSecondary = Color(0xFF93A3AD),
            textTertiary = Color(0xFF5A6975),
            paper = Color(0xFFEDE8DC),
        )

        /**
         * Arasaka red. Yellow is demoted to a rare highlight.
         *
         * [danger] shifts to yellow here: red is the brand colour in this variant, so a destructive
         * action drawn in red would be indistinguishable from an ordinary primary action. The
         * hi-res quality-tier stripe keeps its fixed red — tier colours are a data encoding and are
         * exempt from the theme swap (see [QualityTierColors]) — which is why [primary] is nudged
         * off `#FF2E3C` to `#FF3B47` so a hi-res stripe never reads as brand chrome.
         */
        val Crimson: NullWaveColors = Yellow.copy(
            primary = Color(0xFFFF3B47),
            primaryDim = Color(0xFF8A1018),
            secondary = Color(0xFFFF6B52),
            danger = Color(0xFFFCEE0A),
        )

        /** Braindance. Cooler and more clinical; danger stays red. */
        val Dive: NullWaveColors = Yellow.copy(
            primary = Color(0xFF00F0FF),
            primaryDim = Color(0xFF056C73),
            secondary = Color(0xFF39FF88),
            danger = Color(0xFFFF2E3C),
        )
    }
}

/**
 * Audio quality tier accents, borrowed from the inventory's rarity coding.
 *
 * Fixed encoding: these are **exempt from the theme swap** and never change with the variant. They
 * appear only as the 3dp leading stripe on a track row, and only while "show quality tier" is on in
 * Settings. Never as a fill, never as text.
 */
@Immutable
object QualityTierColors {
    /** Lossy, <= 128 kbps. */
    val Low: Color = Color(0xFF2F81F7)

    /** 192-256 kbps. */
    val Standard: Color = Color(0xFF39FF88)

    /** 320 kbps. */
    val High: Color = Color(0xFFFCEE0A)

    /** Lossless. */
    val Lossless: Color = Color(0xFFFF8A1F)

    /** Hi-res. */
    val HiRes: Color = Color(0xFFFF2E3C)
}

/** The three shipped colour variants, in the order the Settings screen lists them. */
enum class NullWaveColorVariant(val colors: NullWaveColors) {
    Yellow(NullWaveColors.Yellow),
    Crimson(NullWaveColors.Crimson),
    Dive(NullWaveColors.Dive),
}
