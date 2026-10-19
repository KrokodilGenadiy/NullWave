package com.zaus.nullwave

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.theme.ChakraPetch
import com.zaus.nullwave.core.designsystem.theme.Exo2
import com.zaus.nullwave.core.designsystem.theme.NullWaveTypography
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real font resolution and glyph metrics, including mixed-script fallback at each UI weight. */
@RunWith(AndroidJUnit4::class)
class TypographyInstrumentedTest {
    @Test
    fun headingsUseChakraForLatinAndExoForCyrillicInTheSameRun() {
        val resolver = createFontFamilyResolver(InstrumentationRegistry.getInstrumentation().targetContext)
        fun resolve(family: FontFamily, weight: FontWeight) = resolver.resolve(
            fontFamily = family, fontWeight = weight, fontSynthesis = FontSynthesis.None,
        ).value as Typeface

        val type = NullWaveTypography.Default
        for (style in listOf(type.display, type.h1, type.h2, type.button, type.tab)) {
            val weight = checkNotNull(style.fontWeight)
            val actual = resolve(checkNotNull(style.fontFamily), weight)
            val latin = resolve(ChakraPetch, weight)
            val cyrillic = resolve(Exo2, weight)
            val expected = advances(latin, "LIBRARY") + advances(cyrillic, "БИБЛИОТЕКАЁЖ")
            assertArrayEquals("Mixed run at weight ${weight.weight}", expected,
                advances(actual, "LIBRARYБИБЛИОТЕКАЁЖ"), 0.01f)
        }
    }

    private fun advances(typeface: Typeface, text: String): FloatArray = FloatArray(text.length).also {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 48f
        }.getTextWidths(text, it)
    }
}
