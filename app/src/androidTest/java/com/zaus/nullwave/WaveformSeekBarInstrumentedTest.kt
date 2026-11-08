package com.zaus.nullwave

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.R as DesignR
import com.zaus.nullwave.core.designsystem.components.player.NullWaveSeekBar
import com.zaus.nullwave.core.designsystem.theme.NullWaveColors
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WaveformSeekBarInstrumentedTest {
    private val motionScale = object : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(1f)
    }
    private var peaks by mutableStateOf<List<Float>?>(null)
    private var reduceMotion by mutableStateOf(false)

    @get:Rule
    val compose = createComposeRule(effectContext = motionScale)

    private val slider get() = compose.onNodeWithContentDescription("Seek")

    @Test fun loadingRulerIsReplacedByCompleteWaveformInLtr() = verifyTimeline(LayoutDirection.Ltr)
    @Test fun loadingRulerIsReplacedByCompleteWaveformInRtl() = verifyTimeline(LayoutDirection.Rtl)

    @Test
    fun seekStaysAvailableDuringTheScanAndBothHalvesOfTheWaveformGrowAroundThePlayhead() {
        compose.mainClock.autoAdvance = false
        val seeks = mutableListOf<Long>()
        showTimeline(onSeek = { seeks += it })
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val time = context.getString(DesignR.string.nw_seek_position, "500", "1000")
        val preparing = context.getString(DesignR.string.nw_seek_preparing_waveform, time)
        compose.mainClock.advanceTimeBy(64)
        assertEquals(time, slider.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        compose.mainClock.advanceTimeBy(400)
        assertEquals(preparing, slider.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertTrue("The scan should move with a stationary playhead", sliderPixelsChange())
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        compose.runOnIdle { assertEquals(listOf(750L), seeks) }

        val ruler = slider.captureToImage().toPixelMap()
        val colors = NullWaveColors.Cyberpunk
        val playheadColor = colors.primary.toArgb()
        val playheadX = (0 until ruler.width).first { x -> ruler[x, ruler.height / 2].toArgb() == playheadColor }
        val playhead = ruler.verticalSpan(playheadX, playheadColor)
        compose.runOnUiThread { peaks = listOf(1f, 0.65f) }
        compose.mainClock.advanceTimeBy(96)
        assertEquals(time, slider.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        val growing = slider.captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(400)
        val complete = slider.captureToImage().toPixelMap()
        // Both the played and the unplayed bar already occupy their own time interval mid-animation.
        for ((x, color) in listOf(growing.width / 4 to colors.secondary, growing.width * 3 / 4 to colors.outline)) {
            val intermediate = growing.verticalSpan(x, color.toArgb())
            val full = complete.verticalSpan(x, color.toArgb())
            assertTrue("Bar at x=$x should grow upwards and downwards: $intermediate to $full",
                full.first < intermediate.first && intermediate.last < full.last)
            assertEquals("Bar center at x=$x", (full.first + full.last) / 2.0,
                (intermediate.first + intermediate.last) / 2.0, 1.0)
        }
        assertEquals(playhead, growing.verticalSpan(playheadX, playheadColor))
        assertEquals(playhead, complete.verticalSpan(playheadX, playheadColor))
        assertFalse("Completed peaks should settle at their measured heights", sliderPixelsChange())
    }

    @Test
    fun cacheHitsAndMotionRestrictionsDrawTheFinalBarsImmediately() {
        compose.mainClock.autoAdvance = false
        showTimeline()

        fun assertImmediatePeaks() {
            compose.runOnUiThread { peaks = listOf(1f, 1f) }
            compose.mainClock.advanceTimeByFrame()
            val first = slider.captureToImage()
            assertTrue("The first frame should already have tall bars",
                first.toPixelMap().coverage(true, 0.3f, NullWaveColors.Cyberpunk.secondary.toArgb()) > 0.9f)
            compose.mainClock.advanceTimeBy(400)
            assertTrue("The ready bars should stay at full height",
                first.asAndroidBitmap().sameAs(slider.captureToImage().asAndroidBitmap()))
        }

        compose.mainClock.advanceTimeBy(64)
        assertImmediatePeaks()
        compose.runOnUiThread {
            reduceMotion = true
            peaks = null
        }
        compose.mainClock.advanceTimeBy(400)
        assertImmediatePeaks()
        compose.runOnUiThread {
            reduceMotion = false
            motionScale.scaleFactor = 0f
            peaks = null
        }
        compose.mainClock.advanceTimeBy(400)
        assertImmediatePeaks()
    }

    @Test
    fun motionRestrictionsKeepTheLoadingCueStaticAndUnavailableClearsIt() {
        compose.mainClock.autoAdvance = false
        reduceMotion = true
        showTimeline()
        compose.mainClock.advanceTimeBy(64)
        val initial = slider.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(400)
        val reduced = slider.captureToImage().asAndroidBitmap()
        assertFalse("Reduced motion should still show the preparation cue", initial.sameAs(reduced))
        assertFalse("Reduce motion should keep the scan still", sliderPixelsChange())

        compose.runOnUiThread {
            reduceMotion = false
            motionScale.scaleFactor = 0f
        }
        compose.mainClock.advanceTimeBy(64)
        assertFalse("System animation scale zero should keep the scan still", sliderPixelsChange())
        compose.runOnUiThread { motionScale.scaleFactor = 1f }
        compose.mainClock.advanceTimeBy(64)
        assertTrue("Restoring motion should restart the loading scan", sliderPixelsChange())

        compose.runOnUiThread { peaks = emptyList() }
        compose.mainClock.advanceTimeBy(64)
        assertFalse("Unavailable audio should leave a stationary rail", sliderPixelsChange())
    }

    private fun sliderPixelsChange(): Boolean {
        val first = slider.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(400)
        return !first.sameAs(slider.captureToImage().asAndroidBitmap())
    }

    private fun PixelMap.verticalSpan(x: Int, color: Int): IntRange {
        val rows = (0 until height).filter { y -> this[x, y].toArgb() == color }
        assertTrue("Expected a visible stroke at x=$x", rows.isNotEmpty())
        return rows.first()..rows.last()
    }

    private fun showTimeline(direction: LayoutDirection = LayoutDirection.Ltr, onSeek: (Long) -> Unit = {}) {
        compose.setContent {
            NullWaveTheme(effects = NullWaveEffects(reduceMotion = reduceMotion, glowEnabled = false)) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    NullWaveSeekBar(
                        mediaKey = "recording",
                        positionMs = 500,
                        durationMs = 1_000,
                        onSeek = onSeek,
                        formatTime = Long::toString,
                        label = "Seek",
                        modifier = Modifier.width(240.dp).background(NullWaveTheme.colors.bg),
                        waveform = peaks,
                        glowEnabled = false,
                    )
                }
            }
        }
    }

    private fun verifyTimeline(direction: LayoutDirection) {
        showTimeline(direction)
        val playedOnLeft = direction == LayoutDirection.Ltr
        val active = NullWaveColors.Cyberpunk.secondary.toArgb()
        val outline = NullWaveColors.Cyberpunk.outline.toArgb()
        val ruler = slider.captureToImage().toPixelMap()
        assertTrue("Pending ruler needs separate played ticks",
            ruler.coverage(playedOnLeft, 0.5f, active) in 0.03f..0.4f)
        assertTrue("Pending ruler needs separate unplayed ticks",
            ruler.coverage(!playedOnLeft, 0.5f, outline) in 0.03f..0.4f)

        assertTrue("The loading ruler must not show amplitude peaks", ruler.coverage(playedOnLeft, 0.3f, active) == 0f)

        compose.runOnIdle { peaks = List(128) { if (it < 64) 1f else 0f } }
        val complete = slider.captureToImage().toPixelMap()
        assertTrue("The loud first half must show tall peaks", complete.coverage(playedOnLeft, 0.3f, active) > 0.2f)
        assertTrue("Measured silence replaces the sparse ruler", complete.coverage(!playedOnLeft, 0.5f, outline) > 0.4f)
        assertTrue("Silence must have no tall bars", complete.coverage(!playedOnLeft, 0.3f, outline) == 0f)

        compose.runOnIdle { peaks = emptyList() }
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
