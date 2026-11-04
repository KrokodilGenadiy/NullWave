package com.zaus.nullwave.feature.player.ui

import android.content.Intent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.R as DesignSystemR
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.feature.player.presentation.PlayerIntent
import com.zaus.nullwave.feature.player.presentation.PlayerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Seek availability may reset its gesture node without replacing unrelated focused controls. */
class PlayerFullFocusInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<PlayerHostTestActivity>? = null
    private var originalInputMode: InputMode? = null

    @After
    fun close() {
        try { scenario?.close() } finally {
            PlayerHostTestActivity.fixture = null
            // Compose cannot request Touch mode on Android; restore it through instrumentation.
            originalInputMode?.let {
                InstrumentationRegistry.getInstrumentation().setInTouchMode(it == InputMode.Touch)
            }
        }
    }

    @Test
    fun changingOnlySeekCapabilityKeepsThePauseNodeAndKeyboardFocus() {
        val session = PlaybackSessionId("focus-session")
        val entry = QueueEntry(QueueEntryId("focus-entry"),
            MediaTrack(TrackId("focus-track"), "Low Orbit", artist = null, album = null, durationMs = 120_000))
        val target = PlaybackItemToken(session, entry.entryId, activation = 0)
        val queue = PlaybackQueue.create(entries = listOf(entry), current = target)
        val progress = PlaybackProgress(
            target = target, positionRevision = 0, positionMs = 30_000, durationMs = 120_000,
            bufferedPositionMs = null, sampledAtMs = 10_000, advancing = false,
        )
        fun playerState(canSeek: Boolean) = PlayerState(
            connection = PlaybackConnection.Connected(PlaybackSnapshot.create(
                sessionId = session,
                queue = queue,
                transport = PlaybackTransport(phase = PlaybackPhase.Ready, playRequested = true),
                capabilities = buildSet {
                    add(PlaybackCapability.SetPlayRequested)
                    if (canSeek) add(PlaybackCapability.SeekTo)
                },
            )),
            progress = progress,
        )
        val state = mutableStateOf(playerState(canSeek = true))
        val intents = mutableListOf<PlayerIntent>()
        val artwork = RecordingHostArtworkRenderer()
        lateinit var inputModeManager: InputModeManager
        PlayerHostTestActivity.fixture = PlayerHostActivityFixture(content = {
            val currentInputModeManager = LocalInputModeManager.current
            SideEffect { inputModeManager = currentInputModeManager }
            PlayerFullPlayer(
                state = state.value, canSubmit = true, onIntent = intents::add,
                clock = PlaybackClock { 10_000 }, artwork = { artwork },
                lifecycleOwner = LocalLifecycleOwner.current, onClose = {}, onOpenLibrary = {},
            )
        })
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch<PlayerHostTestActivity>(Intent(context, PlayerHostTestActivity::class.java))
        compose.waitUntil(5_000) { compose.onNodeWithTag("player_full").isDisplayed() }
        val pause = compose.onNode(
            hasContentDescription(context.getString(DesignSystemR.string.nw_player_pause)) and
                hasAnyAncestor(hasTestTag("player_full_transport")),
        )
        val seek = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and
                hasAnyAncestor(hasTestTag("player_full_seek")),
        )
        seek.assertIsEnabled()
        pause.performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle {
            originalInputMode = inputModeManager.inputMode
            assertTrue("The fixture must enable keyboard focus", inputModeManager.requestInputMode(InputMode.Keyboard))
        }
        pause.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        val originalId = pause.assertIsFocused().fetchSemanticsNode().id

        for (canSeek in listOf(false, true)) {
            compose.runOnIdle { state.value = playerState(canSeek) }
            // First prove the changed capability reached the composed surface.
            if (canSeek) seek.assertIsEnabled() else seek.assertIsNotEnabled()
            assertEquals("Changing seek availability must preserve the transport node", originalId,
                pause.assertIsEnabled().assertIsFocused().fetchSemanticsNode().id)
        }
        compose.runOnIdle { assertTrue("Focus and capability changes must not issue playback commands", intents.isEmpty()) }
    }
}
