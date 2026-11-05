package com.zaus.nullwave.feature.player.ui

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.feature.player.presentation.PlayerViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.zaus.nullwave.core.designsystem.R as DesignR

/** Real Activity host/VM/controls; the fake backend confirms modes independently of command replies. */
class PlayerModeControlsInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val fixture = PlayerHostActivityFixture()
    private var scenario: ActivityScenario<PlayerHostTestActivity>? = null
    private lateinit var model: PlayerViewModel
    private val track = MediaTrack(TrackId("mode-track"), "Signal Path", "Nullwave", "Transmission", 120_000)
    private var nextRevision = 0L

    @After
    fun close() {
        try { scenario?.close() } finally { PlayerHostTestActivity.fixture = null }
    }

    @Test
    fun shuffleUsesEngineConfirmationAndBusyDisablesBothModesAndTransport() {
        fixture.playback.connection.value = PlaybackConnection.Connected(snapshot())
        val gate = CompletableDeferred<Unit>()
        fixture.playback.commandGate = gate
        launchAndOpen()

        shuffle().performScrollTo().assertIsOff().assertIsEnabled().performClick()
        awaitCommands(1)
        awaitCanSubmit(false)
        shuffle().assertIsOff().assertIsNotEnabled()
        repeatControl().assertIsNotEnabled()
        control(DesignR.string.nw_player_play).assertIsNotEnabled()
        // Direct semantics actions must be harmless even when bypassing normal touch dispatch.
        listOf(shuffle(), repeatControl(), control(DesignR.string.nw_player_play)).forEach {
            it.performSemanticsAction(SemanticsActions.OnClick) { click -> click() }
        }
        compose.runOnIdle {
            assertEquals(listOf(PlaybackCommand.SetShuffle(true)), fixture.playback.commands)
        }

        publish(snapshot(shuffle = true))
        shuffle().assertIsOn().assertIsNotEnabled()
        compose.runOnIdle { gate.complete(Unit) }
        awaitCanSubmit(true)
        shuffle().assertIsOn().assertIsEnabled()
        repeatControl().assertIsEnabled()

        // The second command replies before an engine update; Success cannot uncheck the button.
        shuffle().performScrollTo().performClick()
        awaitCommands(2)
        awaitCanSubmit(true)
        shuffle().assertIsOn()
        compose.runOnIdle {
            assertEquals(listOf(PlaybackCommand.SetShuffle(true), PlaybackCommand.SetShuffle(false)), fixture.playback.commands)
        }
        publish(snapshot(shuffle = false))
        shuffle().assertIsOff()
        compose.runOnIdle { assertEquals(2, fixture.playback.commands.size) }
    }

    @Test
    fun repeatCyclesOffAllOneOffFromObservedModesWithoutOptimisticSelection() {
        fixture.playback.connection.value = PlaybackConnection.Connected(snapshot())
        launchAndOpen()
        val cycle = listOf(RepeatMode.Off, RepeatMode.All, RepeatMode.One, RepeatMode.Off)

        cycle.zipWithNext().forEachIndexed { index, (before, after) ->
            assertRepeat(before)
            repeatControl().performScrollTo().assertIsEnabled().performClick()
            awaitCommands(index + 1)
            awaitCanSubmit(true)
            assertRepeat(before)
            compose.runOnIdle { assertEquals(PlaybackCommand.SetRepeat(after), fixture.playback.commands.last()) }
            publish(snapshot(repeat = after))
            assertRepeat(after)
        }
        compose.runOnIdle {
            assertEquals(cycle.drop(1).map { PlaybackCommand.SetRepeat(it) }, fixture.playback.commands)
        }
    }

    @Test
    fun capabilitiesAreIndependentAndDisconnectRetainsOnlyDisabledModeIndicators() {
        fixture.playback.connection.value = PlaybackConnection.Connected(snapshot(
            capabilities = setOf(PlaybackCapability.SetShuffle, PlaybackCapability.SetPlayRequested),
        ))
        launchAndOpen()
        shuffle().performScrollTo().assertIsEnabled()
        repeatControl().assertIsNotEnabled()
        control(DesignR.string.nw_player_play).assertIsEnabled()
        repeatControl().performSemanticsAction(SemanticsActions.OnClick) { it() }

        // External changes update the indicators without generating a UI command.
        publish(snapshot(shuffle = true, repeat = RepeatMode.One,
            capabilities = setOf(PlaybackCapability.SetRepeat, PlaybackCapability.SetPlayRequested)))
        shuffle().assertIsOn().assertIsNotEnabled()
        repeatControl().assertIsEnabled()
        assertRepeat(RepeatMode.One)
        shuffle().performSemanticsAction(SemanticsActions.OnClick) { it() }
        control(DesignR.string.nw_player_play).assertIsEnabled()

        compose.runOnIdle { fixture.playback.connection.value = PlaybackConnection.Disconnected }
        compose.waitUntil(5_000) { model.snapshot.value.state.liveSnapshot == null }
        shuffle().assertIsOn().assertIsNotEnabled()
        repeatControl().assertIsNotEnabled()
        assertRepeat(RepeatMode.One)
        listOf(shuffle(), repeatControl()).forEach {
            it.performSemanticsAction(SemanticsActions.OnClick) { click -> click() }
        }
        compose.onNodeWithTag("player_full_title").assertExists()
        compose.runOnIdle { assertTrue(fixture.playback.commands.isEmpty()) }

        publish(snapshot(shuffle = false, repeat = RepeatMode.All))
        shuffle().assertIsOff().assertIsEnabled()
        repeatControl().assertIsEnabled()
        assertRepeat(RepeatMode.All)
        compose.runOnIdle { assertTrue(fixture.playback.commands.isEmpty()) }
    }

    @Test
    fun recreationKeepsTheSameVmPendingModeAndEngineSelectionWithoutReplay() {
        fixture.playback.connection.value = PlaybackConnection.Connected(snapshot(shuffle = true, repeat = RepeatMode.One))
        val gate = CompletableDeferred<Unit>()
        fixture.playback.commandGate = gate
        val activity = launchAndOpen()
        val original = model
        repeatControl().performScrollTo().performClick()
        awaitCommands(1)
        awaitCanSubmit(false)
        assertRepeat(RepeatMode.One)

        activity.recreate()
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("player_full").isDisplayed() &&
                fixture.playback.activeStates.get() == 1 && fixture.playback.activeProgress.get() == 1
        }
        activity.onActivity { assertSame(original, it.playerViewModel()) }
        shuffle().performScrollTo().assertIsOn().assertIsNotEnabled()
        repeatControl().assertIsNotEnabled()
        assertRepeat(RepeatMode.One)
        compose.runOnIdle {
            assertEquals(listOf(PlaybackCommand.SetRepeat(RepeatMode.Off)), fixture.playback.commands)
            assertEquals(1, fixture.playback.maxActiveStates.get())
            assertEquals(1, fixture.playback.maxActiveProgress.get())
            assertEquals(0, fixture.playback.reconnects)
            gate.complete(Unit)
        }
        awaitCanSubmit(true)
        assertRepeat(RepeatMode.One)
        publish(snapshot(shuffle = true, repeat = RepeatMode.Off))
        shuffle().assertIsOn().assertIsEnabled()
        repeatControl().assertIsEnabled()
        assertRepeat(RepeatMode.Off)
        compose.runOnIdle { assertEquals(1, fixture.playback.commands.size) }
    }

    private fun launchAndOpen(): ActivityScenario<PlayerHostTestActivity> {
        PlayerHostTestActivity.fixture = fixture
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activity = ActivityScenario.launch<PlayerHostTestActivity>(Intent(context, PlayerHostTestActivity::class.java))
        scenario = activity
        compose.waitUntil(5_000) { compose.onNodeWithTag("player_mini").isDisplayed() }
        activity.onActivity { model = it.playerViewModel() }
        compose.onNodeWithText(track.title).performClick()
        compose.waitUntil(5_000) { compose.onNodeWithTag("player_full").isDisplayed() }
        compose.waitForIdle()
        return activity
    }

    private fun publish(value: PlaybackSnapshot) {
        compose.runOnIdle { fixture.playback.connection.value = PlaybackConnection.Connected(value) }
        compose.waitUntil(5_000) { model.snapshot.value.state.liveSnapshot === value }
        compose.waitForIdle()
    }

    private fun awaitCommands(count: Int) {
        compose.waitUntil(5_000) { fixture.playback.commands.size == count }
        compose.waitForIdle()
    }

    private fun awaitCanSubmit(expected: Boolean) {
        compose.waitUntil(5_000) { model.snapshot.value.operations[PlayerViewModel.RequestKey].canSubmit == expected }
        compose.waitForIdle()
    }

    private fun shuffle() = control(DesignR.string.nw_player_shuffle)
    private fun repeatControl() = control(DesignR.string.nw_player_repeat)

    private fun control(description: Int) = compose.onNode(
        hasContentDescription(text(description)) and hasAnyAncestor(hasTestTag("player_full_transport")),
    )

    private fun assertRepeat(mode: RepeatMode) {
        val description = when (mode) {
            RepeatMode.Off -> DesignR.string.nw_player_repeat_off
            RepeatMode.All -> DesignR.string.nw_player_repeat_all
            RepeatMode.One -> DesignR.string.nw_player_repeat_one
        }
        repeatControl().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(description)))
    }

    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun snapshot(
        shuffle: Boolean = false,
        repeat: RepeatMode = RepeatMode.Off,
        capabilities: Set<PlaybackCapability> = setOf(
            PlaybackCapability.SetShuffle, PlaybackCapability.SetRepeat, PlaybackCapability.SetPlayRequested,
        ),
    ): PlaybackSnapshot {
        val session = PlaybackSessionId("mode-session")
        val entry = QueueEntry(QueueEntryId("mode-entry"), track)
        val revision = nextRevision++
        return PlaybackSnapshot.create(
            sessionId = session,
            revision = revision,
            queue = PlaybackQueue.create(
                entries = listOf(entry), current = PlaybackItemToken(session, entry.entryId, 0),
                revision = revision, shuffleEnabled = shuffle, repeatMode = repeat,
            ),
            transport = PlaybackTransport(phase = PlaybackPhase.Ready),
            capabilities = capabilities,
        )
    }
}
