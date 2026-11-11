package com.zaus.nullwave.feature.player.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayerQueueSwipeInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val current = QueueEntry(QueueEntryId("current"), MediaTrack(TrackId("track"), "Echo", "Artist", null, 120_000))
    private val next = current.copy(entryId = QueueEntryId("next"))
    private val copies = List(60) { current.copy(entryId = QueueEntryId("copy-$it")) }
    private val list get() = compose.onNodeWithTag("player_queue")
    private val selections = mutableListOf<QueueEntryId>()
    private val removals = mutableListOf<QueueEntryId>()
    private var canRemove by mutableStateOf(true)
    private fun row(entry: QueueEntry) = compose.onNodeWithTag("player_queue_entry:${entry.entryId.value}")

    @Before fun showQueue() {
        val queue = PlaybackQueue.create(listOf(current, next) + copies,
            current = PlaybackItemToken(PlaybackSessionId("session"), current.entryId, 0))
        val artwork = RecordingHostArtworkRenderer()
        compose.setContent {
            NullWaveTheme {
                PlayerQueue(queue, 0, artwork, onSelectEntry = { selections.add(it) },
                    onRemoveEntry = if (canRemove) { id -> removals.add(id) } else null)
            }
        }
    }

    @Test fun mostlyVerticalFlickScrollsWithoutSelectingOrRemoving() {
        list.performScrollToKey(copies[40].entryId.value)
        val before = scrollPosition()
        row(copies[42]).assertIsDisplayed().performTouchInput {
            down(center)
            // Both axes cross touch slop in the first move; total leftward travel exceeds removal distance.
            moveBy(Offset(-14.dp.toPx(), -34.dp.toPx()))
            moveBy(Offset(-30.dp.toPx(), -80.dp.toPx()))
            moveBy(Offset(-57.dp.toPx(), -120.dp.toPx()))
            up()
        }
        compose.runOnIdle {
            assertEquals(emptyList<QueueEntryId>(), removals)
            assertEquals(emptyList<QueueEntryId>(), selections)
        }
        assertTrue("The list should scroll forward", scrollPosition() > before)
    }

    @Test fun mostlyHorizontalSwipeRemovesOnlyTheTouchedDuplicateWithoutScrolling() {
        list.performScrollToKey(copies[40].entryId.value)
        val before = scrollPosition()
        val touched = copies[42]
        row(touched).assertIsDisplayed().performTouchInput {
            down(center)
            moveBy(Offset(-34.dp.toPx(), -14.dp.toPx()))
            moveBy(Offset(-70.dp.toPx(), -30.dp.toPx()))
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(touched.entryId), removals)
            assertEquals(emptyList<QueueEntryId>(), selections)
        }
        assertEquals(before, scrollPosition(), 0f)
    }

    private fun scrollPosition(): Float =
        list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun revokingRemovalAfterDownConsumesTheSwipeAndPreservesTheNextTap() {
        row(next).performTouchInput { down(percentOffset(.8f, .5f)) }
        compose.runOnIdle { canRemove = false }
        row(next).performTouchInput {
            moveTo(percentOffset(.55f, .5f))
            moveTo(percentOffset(.25f, .5f))
            up()
        }
        row(next).performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(next.entryId), selections)
            assertEquals(emptyList<QueueEntryId>(), removals)
        }
    }

    @Test fun restoringRemovalDuringADragRequiresAFreshSwipe() {
        row(next).performTouchInput {
            down(percentOffset(.8f, .5f))
            moveTo(percentOffset(.3f, .5f))
        }
        compose.runOnIdle { canRemove = false }
        compose.runOnIdle { canRemove = true }
        row(next).performTouchInput {
            moveTo(percentOffset(.15f, .5f))
            up()
        }
        compose.runOnIdle { assertEquals(emptyList<QueueEntryId>(), removals) }
        row(next).performTouchInput { swipe(percentOffset(.8f, .5f), percentOffset(.2f, .5f)) }
        compose.runOnIdle {
            assertEquals(listOf(next.entryId), removals)
            assertEquals(emptyList<QueueEntryId>(), selections)
        }
    }

    @Test fun swipingWithinTheCurrentRowPreservesItsOrdinaryTap() {
        row(current).performTouchInput { swipe(percentOffset(.8f, .5f), percentOffset(.2f, .5f)) }
        row(current).performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(current.entryId), selections)
            assertEquals(emptyList<QueueEntryId>(), removals)
        }
    }
}
