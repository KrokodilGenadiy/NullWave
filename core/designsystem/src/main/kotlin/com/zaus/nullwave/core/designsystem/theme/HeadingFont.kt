package com.zaus.nullwave.core.designsystem.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle as PlatformFontStyle
import androidx.annotation.FontRes
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.zaus.nullwave.core.designsystem.R

/** Chakra Petch for supported glyphs, then Exo 2 for Cyrillic, even within the same text run. */
val NullWaveHeading: FontFamily = FontFamily(
    HeadingFont(R.font.chakra_petch_regular, FontWeight.Normal),
    HeadingFont(R.font.chakra_petch_medium, FontWeight.Medium),
    HeadingFont(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    HeadingFont(R.font.chakra_petch_bold, FontWeight.Bold),
)

// Compose's FontFamily list selects a weight/style; it is not a per-glyph fallback chain.
// Keep resource loading in its resolver, not static initialization or composition.
private data class HeadingFont(
    @FontRes val latinResource: Int,
    override val weight: FontWeight,
) : AndroidFont(FontLoadingStrategy.Blocking, HeadingTypefaceLoader, FontVariation.Settings()) {
    override val style: FontStyle = FontStyle.Normal
}

private object HeadingTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface {
        font as HeadingFont
        val weight = font.weight.weight
        val latin = PlatformFont.Builder(context.resources, font.latinResource).setWeight(weight).build()
        val cyrillic = PlatformFont.Builder(context.resources, R.font.exo2_variable)
            .setFontVariationSettings("'wght' $weight")
            .setWeight(weight)
            .build()
        return Typeface.CustomFallbackBuilder(PlatformFontFamily.Builder(latin).build())
            .addCustomFallback(PlatformFontFamily.Builder(cyrillic).build())
            .setSystemFallback("sans-serif")
            .setStyle(PlatformFontStyle(weight, PlatformFontStyle.FONT_SLANT_UPRIGHT))
            .build()
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        error("HeadingFont uses blocking local resource loading")
}
