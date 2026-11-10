package com.zaus.nullwave.feature.player.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayerQueueSwipeInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val current = QueueEntry(QueueEntryId("current"), MediaTrack(TrackId("track"), "Echo", "Artist", null, 120_000))
    private val next = current.copy(entryId = QueueEntryId("next"))
    private val selections = mutableListOf<QueueEntryId>()
    private val removals = mutableListOf<QueueEntryId>()
    private var canRemove by mutableStateOf(true)
    private fun row(entry: QueueEntry) = compose.onNodeWithTag("player_queue_entry:${entry.entryId.value}")

    @Before fun showQueue() {
        val queue = PlaybackQueue.create(listOf(current, next),
            current = PlaybackItemToken(PlaybackSessionId("session"), current.entryId, 0))
        val artwork = RecordingHostArtworkRenderer()
        compose.setContent {
            NullWaveTheme {
                PlayerQueue(queue, 0, artwork, onSelectEntry = { selections.add(it) },
                    onRemoveEntry = if (canRemove) { id -> removals.add(id) } else null)
            }
        }
    }

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
