package com.zaus.nullwave.feature.player.ui

import android.content.Intent
import android.view.KeyEvent
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.feature.player.impl.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerQueueInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var fixture = PlayerHostActivityFixture()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val session = PlaybackSessionId("queue-session")
    private var scenario: ActivityScenario<PlayerHostTestActivity>? = null

    @After
    fun close() {
        compose.mainClock.autoAdvance = true
        try { scenario?.close() } finally { PlayerHostTestActivity.fixture = null }
    }

    @Test
    fun backBeforeTheFirstQueueFrameReturnsToPlayerAndTheNextBackDismissesIt() {
        fixture = PlayerHostActivityFixture(reduceMotion = false)
        val first = entry(0)
        publish(listOf(first), first)
        launchFullPlayer(first.track.title)
        val action = compose.onNodeWithText(instrumentation.targetContext.getString(R.string.player_queue))
            .performScrollTo().fetchSemanticsNode()
        compose.runOnIdle {
            val view = (action.root as ViewRootForTest).view
            val dispatcher = checkNotNull(view.findViewTreeOnBackPressedDispatcherOwner()).onBackPressedDispatcher
            // Deliver both events in one main-thread turn, before Compose can install another handler.
            assertTrue(checkNotNull(action.config[SemanticsActions.OnClick].action).invoke())
            dispatcher.onBackPressed()
        }
        compose.onNodeWithTag("player_full_close").assertIsDisplayed()
        compose.onNodeWithTag("player_queue").assertDoesNotExist()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("player_full")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("player_mini").assertIsDisplayed()
        compose.runOnIdle { assertTrue(fixture.playback.commands.isEmpty()) }
    }

    @Test
    fun backDuringOpeningKeepsTheSheetAndRecreationRestoresTheRequestedQueue() {
        fixture = PlayerHostActivityFixture(reduceMotion = false)
        val entries = List(3, ::entry)
        publish(entries, entries.first())
        val activity = launchFullPlayer(entries.first().track.title)
        val queueAction = compose.onNodeWithText(instrumentation.targetContext.getString(R.string.player_queue))
        queueAction.performScrollTo()
        compose.mainClock.autoAdvance = false
        queueAction.performClick()
        compose.mainClock.advanceTimeBy(64)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("player_full_close").assertIsDisplayed()
        compose.onNodeWithTag("player_queue").assertDoesNotExist()

        queueAction.performClick()
        compose.mainClock.advanceTimeBy(64)
        activity.recreate()
        compose.mainClock.autoAdvance = true
        awaitVisible("player_queue")
        row(entries.first()).assertIsDisplayed()
        compose.runOnIdle { assertTrue(fixture.playback.commands.isEmpty()) }
    }

    @Test
    fun shuffledOrderAndCurrentMarkerFollowEntryIdsWhenTheSameTrackOccursTwice() {
        val first = entry(0)
        val middle = entry(1)
        val duplicate = first.copy(entryId = QueueEntryId("repeat"))
        val entries = listOf(first, middle, duplicate)
        val shuffled = listOf(middle, duplicate, first)
        publish(entries, duplicate, shuffled)
        launchQueue(duplicate.track.title)
        assertOrderAndCurrent(listOf(duplicate, first), duplicate)
        row(middle).assertDoesNotExist()
        val resources = instrumentation.targetContext.resources
        val count = resources.getQuantityString(R.plurals.player_queue_tracks, 3, 3)
        compose.onNodeWithText(resources.getString(R.string.player_queue_minutes, count, 6)).assertIsDisplayed()

        compose.runOnIdle { publish(entries, first) }
        assertOrderAndCurrent(entries, first)
        compose.onNodeWithText(resources.getString(R.string.player_queue_clear)).assertIsNotEnabled()
        compose.runOnIdle { assertTrue(fixture.playback.commands.isEmpty()) }
    }

    @Test
    fun queueOpensAtTheCurrentEntryAndRestoresBrowsingPositionBeforeBackReturnsToPlayer() {
        val entries = List(40, ::entry)
        publish(entries, entries[28])
        val activity = launchQueue(entries[28].track.title)
        row(entries[28]).assertIsDisplayed()
        compose.onNodeWithTag("player_queue").performScrollToNode(hasTestTag(rowTag(entries.last())))
        compose.runOnIdle { publish(entries, entries[29]) }
        row(entries.last()).assertIsDisplayed()
        activity.recreate()
        awaitVisible("player_queue")
        row(entries.last()).assertIsDisplayed()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitVisible("player_full_close")
        openQueue()
        row(entries[29]).assertIsDisplayed()
        compose.onNodeWithTag("player_queue_back").performClick()
        awaitVisible("player_full_close")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitVisible("player_mini")
        compose.runOnIdle {
            assertTrue(fixture.playback.commands.isEmpty())
            assertEquals(1, fixture.playback.maxActiveStates.get())
            assertEquals(1, fixture.playback.maxActiveProgress.get())
        }
    }

    @Test
    fun playerActionsFollowTheHeroArtboardAndQueueKeepsItsMiniTransport() {
        val first = entry(0).let { it.copy(track = it.track.copy(durationMs = null)) }
        publish(listOf(first), first)
        launchQueue(first.track.title)
        val resources = instrumentation.targetContext.resources
        compose.onNodeWithText(resources.getQuantityString(R.plurals.player_queue_tracks, 1, 1)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.player_queue_next)).assertDoesNotExist()
        for (label in listOf(R.string.player_queue_clear, R.string.player_queue_save, R.string.player_queue_shuffle_rest)) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(resources.getString(label), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(resources.getString(label), 1, layouts.single().lineCount)
        }
        val footer = hasAnyAncestor(hasTestTag("player_full")) and hasTestTag("player_mini")
        compose.onNode(footer).assertIsDisplayed()
        compose.onNode(hasAnyAncestor(footer) and hasText(first.track.title)).performClick()
        awaitVisible("player_full_close")
        val labels = listOf(R.string.player_action_fav, R.string.player_queue, R.string.player_action_lyrics, R.string.player_action_info)
        val actions = labels.map { compose.onNodeWithText(resources.getString(it)).performScrollTo() }
        val bounds = actions.map { it.fetchSemanticsNode().boundsInRoot }
        assertTrue("Expected four compact actions on the same baseline: $bounds", bounds.zipWithNext().all { (a, b) ->
            a.right <= b.left && a.center.y == b.center.y
        })
        actions.first().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(fixture.playback.commands.isEmpty()) }
    }

    @Test
    fun disconnectKeepsTheLastQueueAndNoticeUntilTheServiceConfirmsAnEmptyQueue() {
        val entry = entry(0)
        publish(listOf(entry), entry)
        launchQueue(entry.track.title)
        compose.runOnIdle {
            fixture.playback.connection.value = PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected)
        }
        awaitVisible("player_notice")
        row(entry).assertIsDisplayed()
        compose.runOnIdle { fixture.playback.connection.value = PlaybackConnection.Connected(PlaybackSnapshot.create(session)) }
        compose.waitUntil(5_000) { !compose.onNodeWithTag("player_full").isDisplayed() }
        compose.onNodeWithTag("player_queue").assertDoesNotExist()
        compose.onNodeWithTag("player_mini").assertDoesNotExist()
        compose.runOnIdle { assertTrue(fixture.playback.commands.isEmpty()) }
    }

    private fun entry(index: Int) = QueueEntry(
        QueueEntryId("entry-$index"), MediaTrack(TrackId("track-$index"), "Track $index", "Artist", null, 120_000),
    )

    private fun publish(entries: List<QueueEntry>, current: QueueEntry, order: List<QueueEntry> = entries) {
        fixture.playback.connection.value = PlaybackConnection.Connected(PlaybackSnapshot.create(
            session, queue = PlaybackQueue.create(entries, order.map { it.entryId },
                PlaybackItemToken(session, current.entryId, 0), shuffleEnabled = order != entries),
        ))
    }

    private fun launchQueue(title: String): ActivityScenario<PlayerHostTestActivity> {
        val activity = launchFullPlayer(title)
        openQueue()
        return activity
    }

    private fun launchFullPlayer(title: String): ActivityScenario<PlayerHostTestActivity> {
        PlayerHostTestActivity.fixture = fixture
        val activity = ActivityScenario.launch<PlayerHostTestActivity>(Intent(instrumentation.targetContext, PlayerHostTestActivity::class.java))
        scenario = activity
        awaitVisible("player_mini")
        compose.onNodeWithText(title).performClick()
        awaitVisible("player_full_close")
        return activity
    }

    private fun openQueue() {
        compose.onNodeWithText(instrumentation.targetContext.getString(R.string.player_queue)).performScrollTo().performClick()
        awaitVisible("player_queue")
    }

    private fun awaitVisible(tag: String) = compose.waitUntil(5_000) { compose.onNodeWithTag(tag).isDisplayed() }
    private fun rowTag(entry: QueueEntry) = "player_queue_entry:${entry.entryId.value}"
    private fun row(entry: QueueEntry) = compose.onNodeWithTag(rowTag(entry))

    private fun assertOrderAndCurrent(order: List<QueueEntry>, current: QueueEntry) {
        val marker = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
            instrumentation.targetContext.getString(R.string.player_queue_current))
        compose.waitUntil(5_000) { compose.onNode(marker and hasAnyAncestor(hasTestTag(rowTag(current)))).isDisplayed() }
        compose.onAllNodes(marker).assertCountEquals(1)
        val tops = order.map { entry ->
            compose.onNode(hasText(entry.track.title) and (hasTestTag(rowTag(entry)) or hasAnyAncestor(hasTestTag(rowTag(entry))))).assertIsDisplayed()
            row(entry).fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue("Expected rows in playback order $order, got y=$tops", tops.zipWithNext().all { (a, b) -> a < b })
    }
}
