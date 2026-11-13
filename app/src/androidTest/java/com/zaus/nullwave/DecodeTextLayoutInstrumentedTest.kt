package com.zaus.nullwave

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDecodeText
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DecodeTextLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    /**
     * The glyph overlay always spans the whole slot. The final copy must span it too, or centred
     * and end-aligned text would sit in a different place once the decode ends.
     */
    @Test fun finalCopyFillsAWideSlotSoAlignedTextDoesNotJumpWhenTheDecodeEnds() {
        val label = "NULLWAVE"
        compose.setContent {
            NullWaveTheme {
                NullWaveDecodeText(label, Modifier.width(320.dp).testTag("decode"),
                    style = NullWaveTheme.typography.h2.copy(textAlign = TextAlign.Center))
            }
        }
        val slot = compose.onNodeWithTag("decode").getUnclippedBoundsInRoot()
        val copy = compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(slot.left, copy.left)
        assertEquals(slot.right, copy.right)
    }
}
