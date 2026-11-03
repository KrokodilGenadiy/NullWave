package com.zaus.nullwave.feature.player.ui

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.R as DesignSystemR
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.feature.player.impl.R
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Production host/Metro in a small viewport; no audio service, permissions or device setting changes. */
class PlayerCompactLayoutInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val testLocale = Locale.forLanguageTag("ru")
    private val fixture = PlayerHostActivityFixture(
        viewport = DpSize(320.dp, 240.dp),
        fontScale = 2f,
        locale = testLocale,
    )
    private var scenario: ActivityScenario<PlayerHostTestActivity>? = null

    @After
    fun close() {
        try { scenario?.close() } finally { PlayerHostTestActivity.fixture = null }
    }

    @Test
    fun longRussianNoticeKeepsTransportAndContentVisibleAndItsActionReachable() {
        launch()

        val viewport = compose.onNodeWithTag("host_test_viewport").getUnclippedBoundsInRoot()
        assertEquals(320f, (viewport.right - viewport.left).value, 0.5f)
        assertEquals(240f, (viewport.bottom - viewport.top).value, 0.5f)
        val content = compose.onNodeWithTag("player_content").getUnclippedBoundsInRoot()
        val notice = compose.onNodeWithTag("player_notice_viewport").getUnclippedBoundsInRoot()
        val mini = compose.onNodeWithTag("player_mini").getUnclippedBoundsInRoot()
        val tolerance = 0.5.dp
        assertTrue("Navigation must retain a visible viewport: $content", content.bottom > content.top)
        assertTrue("Notice must retain a visible viewport: $notice", notice.bottom > notice.top)
        assertTrue("Content must not extend underneath the notice: content=$content, notice=$notice", content.bottom <= notice.top + tolerance)
        assertTrue("Notice must not overlap transport: notice=$notice, mini=$mini", notice.bottom <= mini.top + tolerance)
        assertTrue("The complete MiniPlayer must fit: mini=$mini, viewport=$viewport", mini.top >= viewport.top && mini.bottom <= viewport.bottom + tolerance)
        assertTransportFits(DesignSystemR.string.nw_player_play)
        assertTransportFits(DesignSystemR.string.nw_player_next)

        val action = compose.onNodeWithText(text(R.string.player_open_library))
        action.performScrollTo().assertIsDisplayed()
        // A semantics click can succeed off screen. Tap the visible portion of the real button.
        val actionBounds = action.fetchSemanticsNode().boundsInRoot
        val viewportNode = compose.onNodeWithTag("host_test_viewport")
        val viewportBounds = viewportNode.fetchSemanticsNode().boundsInRoot
        val pixelsPerDp = viewportBounds.width / 320f
        assertTrue("Action must expose a full touch target after scrolling: $actionBounds", actionBounds.height / pixelsPerDp >= 47.5f)
        assertTrue("Action must expose a full touch target after scrolling: $actionBounds", actionBounds.width / pixelsPerDp >= 47.5f)
        viewportNode.performTouchInput { click(actionBounds.center - viewportBounds.topLeft) }
        compose.runOnIdle {
            assertEquals(1, fixture.libraryRequests)
            assertTrue("Open Library must not retry/play audio", fixture.playback.commands.isEmpty())
            assertEquals(0, fixture.playback.reconnects)
            assertEquals(1, fixture.playback.stateStarts.get())
            assertEquals(1, fixture.playback.progressStarts.get())
        }
        compose.onNodeWithTag("player_notice").assertIsDisplayed()
    }

    @Test
    fun dismissingNoticeAndRemovingQueueReturnTheirSpaceToNavigation() {
        val activity = launch()
        val withNotice = compose.onNodeWithTag("player_content").getUnclippedBoundsInRoot()
        compose.onNodeWithContentDescription(text(R.string.player_dismiss_notice))
            .performScrollTo().performClick()
        compose.waitUntil(5_000) { !compose.onNodeWithTag("player_notice").isDisplayed() }
        val withoutNotice = compose.onNodeWithTag("player_content").getUnclippedBoundsInRoot()
        val mini = compose.onNodeWithTag("player_mini").getUnclippedBoundsInRoot()
        assertTrue("Dismissed notice must not leave a gap", withoutNotice.bottom - withoutNotice.top > withNotice.bottom - withNotice.top)
        assertEquals(mini.top.value, withoutNotice.bottom.value, 0.5f)
        assertTransportFits(DesignSystemR.string.nw_player_play)
        assertTransportFits(DesignSystemR.string.nw_player_next)

        activity.onActivity {
            fixture.playback.connection.value = PlaybackConnection.Connected(
                PlaybackSnapshot.create(PlaybackSessionId("compact-empty")),
            )
        }
        compose.waitUntil(5_000) { !compose.onNodeWithTag("player_mini").isDisplayed() }
        val emptyQueue = compose.onNodeWithTag("player_content").getUnclippedBoundsInRoot()
        assertTrue("Empty queue must release the MiniPlayer height", emptyQueue.bottom - emptyQueue.top > withoutNotice.bottom - withoutNotice.top)
        assertEquals("No reserved bottom gap may remain", mini.bottom.value, emptyQueue.bottom.value, 0.5f)
        compose.onNodeWithTag("player_notice").assertDoesNotExist()
        compose.onNodeWithTag("player_mini").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(fixture.playback.commands.isEmpty())
            assertEquals(0, fixture.playback.reconnects)
        }
    }

    private fun assertTransportFits(description: Int) {
        val control = compose.onNodeWithContentDescription(text(description))
        control.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        assertInsideMini(control)
    }

    private fun assertInsideMini(control: SemanticsNodeInteraction) {
        val bounds = control.getUnclippedBoundsInRoot()
        val mini = compose.onNodeWithTag("player_mini").getUnclippedBoundsInRoot()
        val tolerance = 0.5.dp
        assertTrue("Transport target must fit inside MiniPlayer: target=$bounds, mini=$mini",
            bounds.left >= mini.left - tolerance && bounds.right <= mini.right + tolerance &&
                bounds.top >= mini.top - tolerance && bounds.bottom <= mini.bottom + tolerance)
    }

    private fun launch(): ActivityScenario<PlayerHostTestActivity> {
        fixture.playback.connection.value = hostProblem(PlaybackProblemCode.AccessDenied)
        PlayerHostTestActivity.fixture = fixture
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ActivityScenario.launch<PlayerHostTestActivity>(Intent(context, PlayerHostTestActivity::class.java))
            .also {
                scenario = it
                compose.waitUntil(5_000) {
                    compose.onNodeWithTag("player_mini").isDisplayed() &&
                        compose.onNodeWithTag("player_notice").isDisplayed()
                }
            }
    }

    private fun text(id: Int): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Use the test locale explicitly: Configuration.locale inside apply would read the device locale.
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(testLocale)
        val localized = context.createConfigurationContext(configuration)
        assertEquals("Selectors must use the fixture's Russian locale",
            testLocale.language, localized.resources.configuration.locales[0].language)
        return localized.getString(id)
    }
}
