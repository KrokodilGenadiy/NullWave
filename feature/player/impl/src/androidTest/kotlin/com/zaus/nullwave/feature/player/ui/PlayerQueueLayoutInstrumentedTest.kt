package com.zaus.nullwave.feature.player.ui

import android.content.Intent
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.feature.player.presentation.PlayerState
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerQueueLayoutInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<PlayerHostTestActivity>? = null
    private var session = PlaybackSessionId("session")
    private var currentOnCreation = 0
    private val entries = List(100) { index ->
        val track = index % 10
        QueueEntry(QueueEntryId("row-$index"), MediaTrack(TrackId("track-$track"), "Track $track", "Artist", null, 4_615_000))
    }
    private val list get() = compose.onNodeWithTag("player_queue")

    @After
    fun close() {
        try { scenario?.close() } finally { PlayerHostTestActivity.fixture = null }
    }

    @Test
    fun compactQueueCanBrowseWithLargeTextAnErrorAndMiniPlayer() {
        launch(width = 320, height = 240, fontScale = 2f, disconnected = true)
        val viewport = list.getUnclippedBoundsInRoot()
        val height = viewport.bottom - viewport.top
        assertTrue("Queue viewport is $height", height >= 48.dp)
        list.performScrollToKey("row-42")
        row(42).assertIsDisplayed()
        val notice = compose.onNodeWithTag("player_notice").assertIsDisplayed().getUnclippedBoundsInRoot()
        val mini = compose.onNodeWithTag("player_mini").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Expected list, notice and transport in separate bands", viewport.bottom <= notice.top && notice.bottom <= mini.top)
    }

    @Test
    fun longDurationBelowCreditLeavesTheUpcomingTitleReadable() {
        launch(width = 240, fontScale = 2f)
        list.performScrollToKey("row-1")
        val withinRow = hasAnyAncestor(hasTestTag("player_queue_entry:row-1"))
        val title = compose.onNode(withinRow and hasText("Track 1"), useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("Track 1 should fit on one line", layouts.single().isLineEllipsized(0))
        val credit = compose.onNode(withinRow and hasText("Artist"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val duration = compose.onNode(withinRow and hasText("1:16:55"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("Duration should follow the artist", duration.top >= credit.bottom)
    }

    @Test
    fun recreationAfterPlaybackAdvancesKeepsTheSameOccurrenceAndPixelOffset() {
        val activity = launch()
        list.performScrollToKey("row-15")
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 23f) }
        compose.waitForIdle()
        val before = relativeTop(15)
        assertTrue("Expected to be partway through row 15, top=$before", before < 0f)
        activity.onActivity { currentOnCreation = 10 }
        activity.recreate()
        assertEquals(before, relativeTop(15), 0.5f)
    }

    @Test
    fun recreationAfterTheAnchorHasPlayedStartsAtTheNewCurrentEntry() {
        val activity = launch()
        list.performScrollToKey("row-15")
        assertEquals(0f, relativeTop(15), 0.5f)
        activity.onActivity { currentOnCreation = 20 }
        activity.recreate()
        assertEquals(0f, relativeTop(20), 0.5f)
    }

    @Test
    fun newSessionReusingEntryIdsStartsAtTheQueueIntro() {
        val activity = launch()
        list.performScrollToKey("row-15")
        assertEquals(0f, relativeTop(15), 0.5f)
        activity.onActivity { session = PlaybackSessionId("new-session") }
        activity.recreate()
        row(0).assertIsDisplayed()
        assertTrue("Queue intro should precede the current row", relativeTop(0) > 0f)
    }

    private fun row(index: Int) = compose.onNodeWithTag("player_queue_entry:row-$index")

    private fun relativeTop(index: Int) =
        (row(index).getUnclippedBoundsInRoot().top - list.getUnclippedBoundsInRoot().top).value

    private fun launch(
        width: Int = 320,
        height: Int = 480,
        fontScale: Float = 1f,
        disconnected: Boolean = false,
    ): ActivityScenario<PlayerHostTestActivity> {
        val artwork = RecordingHostArtworkRenderer()
        PlayerHostTestActivity.fixture = PlayerHostActivityFixture(
            viewport = DpSize(width.dp, height.dp), fontScale = fontScale, locale = Locale.forLanguageTag("ru"),
            content = {
                val state = remember {
                    val token = PlaybackItemToken(session, entries[currentOnCreation].entryId, 0)
                    val snapshot = PlaybackSnapshot.create(session, queue = PlaybackQueue.create(entries, current = token))
                    if (disconnected) PlayerState(
                        connection = PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected),
                        lastKnownSnapshot = snapshot,
                    ) else PlayerState(connection = PlaybackConnection.Connected(snapshot))
                }
                PlayerFullPlayer(state, true, {}, PlaybackClock { 0 }, { artwork }, LocalLifecycleOwner.current, {}, {})
            },
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activity = ActivityScenario.launch<PlayerHostTestActivity>(Intent(context, PlayerHostTestActivity::class.java))
        scenario = activity
        compose.onNodeWithText("ОЧЕРЕДЬ").performScrollTo().performClick()
        return activity
    }
}
