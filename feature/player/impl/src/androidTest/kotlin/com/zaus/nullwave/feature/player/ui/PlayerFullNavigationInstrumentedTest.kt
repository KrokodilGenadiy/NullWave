package com.zaus.nullwave.feature.player.ui

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.feature.player.impl.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The real sheet/Activity own UI state; the navigation boundary and audio backend are recorded. */
class PlayerFullNavigationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val fixture = PlayerHostActivityFixture()
    private var scenario: ActivityScenario<PlayerHostTestActivity>? = null

    @After
    fun close() {
        compose.mainClock.autoAdvance = true
        try { scenario?.close() } finally { PlayerHostTestActivity.fixture = null }
    }

    @Test
    fun openLibraryCommitsBeforeAnimationFramesAndStaysClosedAfterImmediateRecreation() {
        val activity = launchFullPlayer()
        val action = compose.onNodeWithText(openLibraryLabel()).performScrollTo().assertIsDisplayed()
        val click = checkNotNull(action.fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        compose.mainClock.autoAdvance = false
        try {
            // A second callback can arrive before recomposition removes the sheet's old nodes.
            compose.runOnIdle { click(); click() }
            // No hide-animation frame can finish between the click and these lifecycle actions.
            activity.onActivity { assertLibraryRequestedOnceWithoutAudio() }
            activity.recreate()
        } finally {
            compose.mainClock.autoAdvance = true
        }

        awaitClosedHost()
        compose.onNodeWithTag("player_mini").assertIsDisplayed()
        activity.onActivity { assertLibraryRequestedOnceWithoutAudio() }
    }

    @Test
    fun emptyQueueAfterOpenLibraryCannotCancelOrRepeatTheNavigationRequest() {
        val activity = launchFullPlayer()
        val action = compose.onNodeWithText(openLibraryLabel()).performScrollTo().assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            action.performClick()
            activity.onActivity {
                assertLibraryRequestedOnceWithoutAudio()
                fixture.playback.connection.value = PlaybackConnection.Connected(
                    PlaybackSnapshot.create(PlaybackSessionId("navigation-empty")),
                )
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }

        awaitClosedHost()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("player_mini").fetchSemanticsNodes().isEmpty() }
        activity.onActivity { assertLibraryRequestedOnceWithoutAudio() }
    }

    private fun launchFullPlayer(): ActivityScenario<PlayerHostTestActivity> {
        fixture.playback.connection.value = hostProblem(PlaybackProblemCode.AccessDenied)
        PlayerHostTestActivity.fixture = fixture
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activity = ActivityScenario.launch<PlayerHostTestActivity>(Intent(context, PlayerHostTestActivity::class.java))
            .also { scenario = it }
        compose.waitUntil(5_000) { compose.onNodeWithTag("player_mini").isDisplayed() }
        compose.onNodeWithText("Host track").performClick()
        compose.waitUntil(5_000) { compose.onNodeWithTag("player_full").isDisplayed() }
        compose.waitForIdle()
        return activity
    }

    private fun awaitClosedHost() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("player_full").fetchSemanticsNodes().isEmpty() &&
                compose.onNodeWithTag("host_library").isDisplayed() &&
                fixture.playback.activeStates.get() == 1 && fixture.playback.activeProgress.get() == 1
        }
    }

    private fun openLibraryLabel(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.player_open_library)

    /** The existing fixture records the navigation callback; it does not run app stack mutation. */
    private fun assertLibraryRequestedOnceWithoutAudio() {
        assertEquals("Open Library must commit once without waiting for sheet animation", 1, fixture.libraryRequests)
        assertTrue("UI navigation must not send audio commands", fixture.playback.commands.isEmpty())
        assertEquals(0, fixture.playback.reconnects)
    }
}
