package com.zaus.nullwave

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.player.NullWaveSeekBar
import com.zaus.nullwave.core.designsystem.theme.NullWaveColors
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WaveformSeekBarInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun loadingRulerIsReplacedByCompleteWaveformInLtr() = verifyTimeline(LayoutDirection.Ltr)
    @Test fun loadingRulerIsReplacedByCompleteWaveformInRtl() = verifyTimeline(LayoutDirection.Rtl)

    private fun verifyTimeline(direction: LayoutDirection) {
        val peaks = mutableStateOf<List<Float>?>(null)
        compose.setContent {
            NullWaveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    NullWaveSeekBar(
                        mediaKey = "recording",
                        positionMs = 500,
                        durationMs = 1_000,
                        onSeek = {},
                        formatTime = Long::toString,
                        label = "Seek",
                        modifier = Modifier.width(240.dp).background(NullWaveTheme.colors.bg),
                        waveform = peaks.value,
                        glowEnabled = false,
                    )
                }
            }
        }

        val slider = compose.onNodeWithContentDescription("Seek")
        val playedOnLeft = direction == LayoutDirection.Ltr
        val active = NullWaveColors.Cyberpunk.secondary.toArgb()
        val outline = NullWaveColors.Cyberpunk.outline.toArgb()
        val ruler = slider.captureToImage().toPixelMap()
        assertTrue("Pending ruler needs separate played ticks",
            ruler.coverage(playedOnLeft, 0.5f, active) in 0.03f..0.4f)
        assertTrue("Pending ruler needs separate unplayed ticks",
            ruler.coverage(!playedOnLeft, 0.5f, outline) in 0.03f..0.4f)

        assertTrue("The loading ruler must not show amplitude peaks", ruler.coverage(playedOnLeft, 0.3f, active) == 0f)

        compose.runOnIdle { peaks.value = List(128) { if (it < 64) 1f else 0f } }
        val complete = slider.captureToImage().toPixelMap()
        assertTrue("The loud first half must show tall peaks", complete.coverage(playedOnLeft, 0.3f, active) > 0.2f)
        assertTrue("Measured silence replaces the sparse ruler", complete.coverage(!playedOnLeft, 0.5f, outline) > 0.4f)
        assertTrue("Silence must have no tall bars", complete.coverage(!playedOnLeft, 0.3f, outline) == 0f)

        compose.runOnIdle { peaks.value = emptyList() }
        val unavailable = slider.captureToImage().toPixelMap()
        assertTrue("An unavailable waveform uses the continuous rail", unavailable.coverage(!playedOnLeft, 0.5f, outline) > 0.95f)
    }

    /** Sample inside one half, away from the invisible thumb slot and the playhead at the midpoint. */
    private fun PixelMap.coverage(left: Boolean, row: Float, color: Int): Float {
        val start = (width * if (left) 0.15f else 0.6f).toInt()
        val end = (width * if (left) 0.4f else 0.85f).toInt()
        val y = (height * row).toInt()
        return (start until end).count { x -> this[x, y].toArgb() == color }.toFloat() / (end - start)
    }
}
