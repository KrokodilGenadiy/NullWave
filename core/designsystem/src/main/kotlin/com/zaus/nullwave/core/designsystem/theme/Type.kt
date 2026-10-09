package com.zaus.nullwave.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zaus.nullwave.core.designsystem.R

/**
 * Chakra Petch — Latin branding only (for example, NULLWAVE).
 * Localized interface text uses Exo 2, whose glyph coverage includes Cyrillic and Latin.
 */
val ChakraPetch: FontFamily = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

/** Shared heading family for Cyrillic, Latin and mixed-language titles. */
val Exo2: FontFamily = FontFamily(
    variableFont(R.font.exo2_variable, FontWeight.Medium),
    variableFont(R.font.exo2_variable, FontWeight.SemiBold),
    variableFont(R.font.exo2_variable, FontWeight.Bold),
)

/**
 * Inter — the neutral body face. Track names, artists, metadata, anything set in a list.
 *
 * Shipped as a variable font, so each weight is the same file with a different `wght` axis value.
 */
val Inter: FontFamily = FontFamily(
    variableFont(R.font.inter_variable, FontWeight.Normal),
    variableFont(R.font.inter_variable, FontWeight.Medium),
    variableFont(R.font.inter_variable, FontWeight.SemiBold),
)

/**
 * JetBrains Mono — numerals, timers, micro-labels and file paths. Tabular figures throughout;
 * see [NullWaveTypography.mono], which sets `tnum` so digits do not jitter as a timer counts.
 */
val JetBrainsMono: FontFamily = FontFamily(
    variableFont(R.font.jetbrains_mono_variable, FontWeight.Normal),
    variableFont(R.font.jetbrains_mono_variable, FontWeight.Medium),
)

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * The type scale. Six named steps, plus the three derived styles the components sheet uses
 * (button, tab, mono).
 *
 * `micro` is drawn uppercase everywhere it appears — `TextStyle` cannot case-transform, so
 * uppercase the string at the call site, when the content calls for it.
 */
@Immutable
data class NullWaveTypography(
    /** Display 28/32 — Exo 2 700. Expanded player title, empty-state headlines. */
    val display: TextStyle,
    /** H1 22/28 — Exo 2 600. Screen headers, album and artist names on detail screens. */
    val h1: TextStyle,
    /** H2 18/24 — Exo 2 500. Top app bar title, dialog titles, section headers. */
    val h2: TextStyle,
    /** Body 15/22 — Inter 400. Track titles and list content. */
    val body: TextStyle,
    /** Body 15/22 — Inter 500. The now-playing row and other emphasised list content. */
    val bodyEmphasis: TextStyle,
    /** Caption 13/18 — Inter 400. Artist lines, metadata, supporting copy. */
    val caption: TextStyle,
    /** Micro 12/16 — JetBrains Mono 400, 0.12em. Readable technical labels. */
    val micro: TextStyle,
    /** Tabular mono 12/16 — elapsed/remaining, dB readouts, bitrates, file sizes. */
    val mono: TextStyle,
    /** Button label 14 — Exo 2 700, 0.08em, uppercase at the call site. */
    val button: TextStyle,
    /** Tab label 13 — Exo 2 600, 0.14em, uppercase at the call site. */
    val tab: TextStyle,
) {
    companion object {
        val Default: NullWaveTypography = NullWaveTypography(
            display = TextStyle(
                fontFamily = Exo2,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.04.em,
            ),
            h1 = TextStyle(
                fontFamily = Exo2,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.06.em,
            ),
            h2 = TextStyle(
                fontFamily = Exo2,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.08.em,
            ),
            body = TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
            bodyEmphasis = TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
            caption = TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            micro = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.12.em,
            ),
            mono = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFeatureSettings = "tnum",
            ),
            button = TextStyle(
                fontFamily = Exo2,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.08.em,
                textAlign = TextAlign.Center,
            ),
            tab = TextStyle(
                fontFamily = Exo2,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.14.em,
            ),
        )
    }
}
