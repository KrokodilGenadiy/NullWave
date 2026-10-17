package com.zaus.nullwave

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.chip.NullWaveChip
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Real Compose measurement regression; run on a device separately from JVM tests. */
@RunWith(Parameterized::class)
class ChipSizingInstrumentedTest(
    private val fontScale: Float,
    private val direction: LayoutDirection,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun shortLabelsUseNaturalWidthInsideHorizontalScroll() {
        compose.setContent {
            TestTheme {
                Row(Modifier.width(240.dp).horizontalScroll(rememberScrollState())) {
                    // EQ gives each band a minimum width, with no maximum.
                    Column(Modifier.widthIn(min = 48.dp)) {
                        NullWaveChip("125", false, {}, Modifier.testTag("frequency"))
                    }
                    Column(Modifier.widthIn(min = 48.dp)) {
                        NullWaveChip("500", false, {}, Modifier.testTag("with-icon"),
                            leadingIcon = NullWaveIcons.Filter)
                    }
                }
            }
        }

        for ((tag, label) in listOf("frequency" to "125", "with-icon" to "500")) {
            val text = textLayout(tag, label)
            assertEquals("$tag must not split a short frequency", 1, text.lineCount)
            assertFalse("$tag must fit its complete label", text.hasVisualOverflow)
            compose.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun boundedLongLabelStillWrapsWithLeadingIcon() {
        val label = "Длинная подпись выбранного фильтра"
        compose.setContent {
            TestTheme {
                NullWaveChip(label, false, {}, Modifier.width(144.dp).testTag("bounded"),
                    leadingIcon = NullWaveIcons.Filter)
            }
        }

        val text = textLayout("bounded", label)
        assertTrue("A constrained long label must wrap", text.lineCount > 1)
        assertFalse("Wrapping must retain all text", text.hasVisualOverflow)
        compose.onNodeWithTag("bounded").assertWidthIsEqualTo(144.dp).assertHeightIsAtLeast(48.dp)
    }

    private fun textLayout(tag: String, label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(results))
            }
        return results.single()
    }

    @Composable
    private fun TestTheme(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalDensity provides Density(LocalDensity.current.density, fontScale),
            LocalLayoutDirection provides direction,
        ) {
            NullWaveTheme(content = content)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "fontScale={0}, direction={1}")
        fun configurations(): List<Array<Any>> = listOf(
            arrayOf(1f, LayoutDirection.Ltr),
            arrayOf(2f, LayoutDirection.Ltr),
            arrayOf(1f, LayoutDirection.Rtl),
            arrayOf(2f, LayoutDirection.Rtl),
        )
    }
}
