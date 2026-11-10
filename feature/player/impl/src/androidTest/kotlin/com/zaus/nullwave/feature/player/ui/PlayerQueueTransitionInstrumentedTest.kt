package com.zaus.nullwave.feature.player.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveColors
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerQueueTransitionInstrumentedTest {
    private val systemMotion = object : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(1f)
    }
    @get:Rule val compose = createComposeRule(effectContext = systemMotion)
    private var open by mutableStateOf(false)
    private var reduceMotion by mutableStateOf(false)
    private var active by mutableStateOf(true)
    private var coverColor by mutableStateOf(Color.Red)
    private val tappedPages = mutableListOf<Boolean>()
    private val sheet get() = compose.onNodeWithTag("sheet")
    private val colors = NullWaveColors.Cyberpunk

    @Test
    fun scanRevealsBothPagesWithTheirArtworkAtItsFinalSizeAndPosition() {
        showSheet()
        val player = frame()
        navigate(true, 144)
        val entering = frame()
        advance(480)
        val queue = frame()
        assertSplitFrame(entering, player, queue)
        navigate(false, 144)
        assertSplitFrame(frame(), player, queue)
        advance(480)
        assertRestingPage(false)
    }

    @Test
    fun backBetweenPressAndReleaseBeforeTheNextFrameCancelsTheQueueTap() {
        showSheet()
        navigate(true, 304)
        sheet.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(listOf(true), tappedPages) }
        val node = sheet.fetchSemanticsNode()
        val point = node.boundsInRoot.center
        val view = (node.root as ViewRootForTest).view
        compose.runOnIdle {
            val downTime = SystemClock.uptimeMillis()
            fun dispatch(action: Int) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
                try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    view.dispatchTouchEvent(event)
                } finally {
                    event.recycle()
                }
            }
            val back = checkNotNull(view.findViewTreeOnBackPressedDispatcherOwner()).onBackPressedDispatcher
            dispatch(MotionEvent.ACTION_DOWN)
            back.onBackPressed()
            dispatch(MotionEvent.ACTION_UP)
            assertEquals(listOf(true), tappedPages)
        }
        advance(480)
        assertRestingPage(false)
    }

    @Test
    fun quickBackReversesTheScanAndOnlyTheDestinationAcceptsInput() {
        showSheet()
        navigate(true, 160)
        compose.onNodeWithTag("player").assertDoesNotExist()
        sheet.performTouchInput { click(bottomCenter - Offset(0f, 4f)) }
        val before = frame()
        val x = before.width / 4
        val beforeY = before.scanY(x)
        navigate(false, 16)
        val turnY = frame().scanY(x)
        assertTrue("Back jumped from $beforeY to $turnY",
            kotlin.math.abs(turnY - beforeY) < before.height / 6)
        advance(144)
        val afterY = frame().scanY(x)
        assertTrue("Back should move toward the footer: $turnY -> $afterY", afterY > turnY)
        compose.onNodeWithTag("queue").assertDoesNotExist()
        sheet.performTouchInput { click(topCenter + Offset(0f, 4f)) }
        assertEquals(listOf(true, false), tappedPages)
        advance(480)
        assertRestingPage(false)
    }

    @Test
    fun onlyTheRevealedSideOfTheChamferAcceptsNewTapsInEitherDirection() {
        showSheet()
        for (queue in listOf(true, false)) {
            navigate(queue, 144)
            val pixels = frame()
            val left = 2
            val right = pixels.width * 9 / 10
            val y = (pixels.scanY(left) + pixels.scanY(right)) / 2
            // Same height, opposite sides of the cut. The right pixel belongs to the queue.
            assertEquals(Color.White, pixels[left, y])
            assertEquals(Color.Blue, pixels[right, y])
            val hidden = Offset((if (queue) left else right).toFloat(), y.toFloat())
            val visible = Offset((if (queue) right else left).toFloat(), y.toFloat())
            sheet.performTouchInput { click(hidden) }
            compose.runOnIdle { assertTrue(tappedPages.isEmpty()) }
            sheet.performTouchInput { click(visible) }
            compose.runOnIdle {
                assertEquals(listOf(queue), tappedPages)
                tappedPages.clear()
            }
            advance(480)
        }
    }

    @Test
    fun liftingAHiddenPressAfterTheQueueIsRevealedDoesNotClickThrough() {
        showSheet()
        navigate(true, 64)
        val point = Offset(10f, 10f)
        assertEquals(Color.White, frame()[10, 10])
        sheet.performTouchInput { down(point) }
        // The destination becomes fully interactive while the finger is still down.
        compose.runOnUiThread { reduceMotion = true }
        advance(16)
        assertEquals(Color.Blue, frame()[10, 10])
        sheet.performTouchInput { up() }
        compose.runOnIdle { assertTrue(tappedPages.isEmpty()) }
        sheet.performTouchInput { click(point) }
        compose.runOnIdle { assertEquals(listOf(true), tappedPages) }
    }

    @Test
    fun changingTrackInTheQueueShowsTheNewArtworkOnReturn() {
        showSheet()
        navigate(true, 480)
        compose.runOnUiThread { coverColor = Color.Green }
        advance(16)
        val queue = frame()
        assertEquals(Color.Green, queue[2, queue.height - 3])
        navigate(false, 144)
        val returning = frame()
        advance(480)
        val player = frame()
        assertEquals(Color.Green, player[player.width / 2, 2])
        assertSplitFrame(returning, player, queue)
    }

    @Test
    fun restoredDestinationAndLiveMotionRestrictionsSettleImmediately() {
        open = true
        showSheet()
        assertRestingPage(true)
        navigate(false, 144)
        compose.runOnUiThread { reduceMotion = true }
        advance(16)
        assertRestingPage(false)
        compose.runOnUiThread { reduceMotion = false; systemMotion.scaleFactor = 0f; open = true }
        advance(16)
        assertRestingPage(true)
        compose.runOnUiThread { systemMotion.scaleFactor = 1f }
        advance(16)
        navigate(false, 144)
        compose.runOnUiThread { active = false }
        advance(16)
        assertRestingPage(false)
        compose.runOnUiThread { active = true }
        advance(16)
        assertRestingPage(false)
    }

    private fun showSheet() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BackHandler(enabled = open) { open = false }
            NullWaveTheme(effects = NullWaveEffects(reduceMotion = reduceMotion)) {
                PlayerQueueTransition({ open }, active, Modifier.size(180.dp, 240.dp).testTag("sheet")) { queue ->
                    Box(Modifier.fillMaxSize().background(if (queue) Color.Blue else Color.White)
                        .testTag(if (queue) "queue" else "player")
                        .clickable(interactionSource = null, indication = null) { tappedPages += queue }) {
                        val size = if (queue) 28.dp else 100.dp
                        Box(Modifier.align(if (queue) Alignment.BottomStart else Alignment.TopCenter)
                            .size(size).background(coverColor), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(size / 2).background(Color.Magenta))
                        }
                    }
                }
            }
        }
        advance(16)
    }

    private fun navigate(queue: Boolean, timeMs: Long) {
        compose.runOnUiThread { open = queue }
        advance(timeMs)
    }

    private fun advance(timeMs: Long) = compose.mainClock.advanceTimeBy(timeMs)
    private fun frame() = sheet.captureToImage().toPixelMap()
    private fun PixelMap.scanY(x: Int) = (0 until height).first { y -> this[x, y] == colors.primary }

    private fun assertSplitFrame(moving: PixelMap, player: PixelMap, queue: PixelMap) {
        val right = moving.width * 9 / 10
        assertTrue("The scan must have a chamfer", moving.scanY(2) > moving.scanY(right))
        assertTrue("Missing second rail", (0 until moving.height).any { moving[right, it] == colors.secondary })
        val railMargin = with(compose.density) { 8.dp.roundToPx() }
        for (x in 2 until moving.width step 13) {
            val edge = moving.scanY(x)
            for (y in 2 until moving.height step 11) {
                if (y in edge - railMargin..edge + railMargin) continue
                val expected = if (y < edge) player else queue
                assertEquals("Page or artwork moved at ($x, $y)", expected[x, y], moving[x, y])
            }
        }
    }

    private fun assertRestingPage(queue: Boolean) {
        val pixels = frame()
        val background = if (queue) Color.Blue else Color.White
        // Both corners are outside either cover. A partially drawn page or rail will change this column.
        val x = pixels.width - 2
        assertTrue("The destination should fill the sheet", (0 until pixels.height).all { pixels[x, it] == background })
    }
}
