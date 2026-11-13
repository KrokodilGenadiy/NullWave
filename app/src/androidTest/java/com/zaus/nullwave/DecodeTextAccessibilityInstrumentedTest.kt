package com.zaus.nullwave

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDecodeText
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.settings.api.R as SettingsR
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DecodeTextAccessibilityInstrumentedTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    @Test fun settingsHeadingUsesTurkishCasingForTheFallbackLabel() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(target.resources.configuration).apply { setLocale(Locale.forLanguageTag("tr-TR")) }
        val screen = target.createConfigurationContext(config)
        assertEquals("Settings", screen.getString(SettingsR.string.settings_title))
        assertEquals("SETTİNGS", screen.screenTitle(SettingsR.string.settings_title))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides screen, LocalConfiguration provides config) {
                NullWaveTheme { NullWaveTopBar(screen.getString(SettingsR.string.settings_title)) }
            }
        }
        compose.onNodeWithText("SETTİNGS").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test fun screenTitleDecodesOnChangeAndKeepsItsFinalHeading() {
        val title = mutableStateOf("Library")
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent { NullWaveTheme { NullWaveTopBar(title.value) } }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText("LIBRARY").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            compose.runOnUiThread { title.value = "Эквалайзер" }
            compose.mainClock.advanceTimeBy(80)
            val heading = compose.onNodeWithText("ЭКВАЛАЙЗЕР")
            heading.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            val decoding = heading.captureToImage()
            compose.mainClock.advanceTimeBy(1_000)
            assertFalse(decoding.asAndroidBitmap().sameAs(heading.captureToImage().asAndroidBitmap()))
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun finalLabelIsVisibleToAndroidAccessibilityThroughoutTheDecode() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
        val label = "Сигнал\u202f🇵🇱"
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent { NullWaveTheme { NullWaveDecodeText(label, Modifier.testTag("decode")) } }
            compose.mainClock.advanceTimeBy(80)
            assertAccessibleLabel(label)
            val during = compose.onNodeWithTag("decode").captureToImage()
            compose.mainClock.advanceTimeBy(1_000)
            assertAccessibleLabel(label)
            val after = compose.onNodeWithTag("decode").captureToImage()
            assertFalse("The first capture must show the animation", during.asAndroidBitmap().sameAs(after.asAndroidBitmap()))
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun assertAccessibleLabel(label: String) {
        compose.onAllNodes(hasAnyAncestor(hasTestTag("decode")) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(1)
        val node = compose.onNodeWithText(label).fetchSemanticsNode()
        compose.runOnIdle {
            val provider = checkNotNull((node.root as ViewRootForTest).view.accessibilityNodeProvider)
            val info = checkNotNull(provider.createAccessibilityNodeInfo(node.id))
            assertEquals(label, info.text.toString())
            assertTrue("Android must expose the final label while cipher glyphs are drawn", info.isVisibleToUser)
        }
    }
}
