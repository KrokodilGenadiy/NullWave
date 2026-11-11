package com.zaus.nullwave

import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDecodeText
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DecodeTextAccessibilityInstrumentedTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

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
