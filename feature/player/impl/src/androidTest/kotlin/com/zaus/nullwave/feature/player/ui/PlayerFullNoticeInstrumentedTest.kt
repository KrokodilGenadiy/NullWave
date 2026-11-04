package com.zaus.nullwave.feature.player.ui

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.mvi.ui.collectMviUiWithLifecycle
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackProblem
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.player.impl.R
import com.zaus.nullwave.feature.player.presentation.PlayerViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.zaus.nullwave.core.designsystem.R as DesignSystemR

/** The real VM drives full content directly so a modal window cannot escape the compact viewport. */
class PlayerFullNoticeInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val testLocale = Locale.forLanguageTag("ru")
    private val clock = PlaybackClock { 10_000 }
    private lateinit var fixture: PlayerHostActivityFixture
    private lateinit var viewModel: PlayerViewModel
    private var scenario: ActivityScenario<PlayerHostTestActivity>? = null

    @After
    fun close() {
        try { scenario?.close() } finally { PlayerHostTestActivity.fixture = null }
    }

    @Test
    fun delayedCommandFailureIsVisibleWithoutScrollingTheNoticeAndKeepsAContentViewport() {
        val reply = CompletableDeferred<Unit>()
        launch(commandGate = reply)
        compose.onNodeWithTag("player_notice").assertDoesNotExist()

        play().performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { fixture.playback.commands.size == 1 }
        compose.onNodeWithTag("player_notice").assertDoesNotExist()
        compose.runOnIdle { reply.complete(Unit) }
        awaitNotice()

        // Do not scroll either the message or its ancestors: a newly reported failure must be visible.
        compose.onNodeWithTag("player_notice").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.player_start_not_allowed)).assertIsDisplayed()
        compose.onNodeWithTag("player_full_close").assertIsDisplayed()
        val full = compose.onNodeWithTag("player_full").getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag("player_full_scroll").getUnclippedBoundsInRoot()
        val notice = compose.onNodeWithTag("player_notice").getUnclippedBoundsInRoot()
        assertEquals("The fixture must constrain the full surface", 240f, (full.bottom - full.top).value, 0.5f)
        assertTrue("Artwork/controls must retain a viewport: $content", content.bottom > content.top)
        assertTrue("Notice must occupy its own space: content=$content, notice=$notice",
            content.bottom <= notice.top + 0.5.dp)
        assertTrue("Notice must fit inside the full surface: notice=$notice, full=$full",
            notice.top >= full.top && notice.bottom <= full.bottom + 0.5.dp)
        compose.runOnIdle {
            assertEquals(listOf(PlaybackCommand.SetPlayRequested(true)), fixture.playback.commands)
            assertEquals(1, fixture.playback.stateStarts.get())
            assertEquals(1, fixture.playback.progressStarts.get())
        }
    }

    @Test
    fun progressPreservesNoticeScrollButANewProblemOccurrenceStartsAtTheTop() {
        launch()
        val firstProblem = PlaybackProblemId("notice-first-problem")
        compose.runOnIdle { publishProblem(firstProblem) }
        compose.waitUntil(5_000) { viewModel.snapshot.value.state.visibleProblem?.id == firstProblem }
        compose.waitForIdle()
        val notice = compose.onNodeWithTag("player_notice")
        assertTrue("Large Russian text must exercise internal message scrolling",
            notice.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
        notice.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100f) }
        compose.waitForIdle()
        val scrolled = noticeScroll()
        assertTrue("The message must actually scroll before testing preservation", scrolled > 0f)

        compose.runOnIdle {
            fixture.playback.progress.value = checkNotNull(fixture.playback.progress.value).copy(
                positionMs = 45_000, sampledAtMs = 11_000,
            )
        }
        compose.waitUntil(5_000) { viewModel.snapshot.value.state.progress?.positionMs == 45_000L }
        compose.waitForIdle()
        assertEquals("Progress must not reset a reader's position", scrolled, noticeScroll(), 0.5f)

        // Identical code/text, but a new engine occurrence with no intervening absent notice.
        val nextProblem = PlaybackProblemId("notice-next-problem")
        compose.runOnIdle { publishProblem(nextProblem) }
        compose.waitUntil(5_000) { viewModel.snapshot.value.state.visibleProblem?.id == nextProblem }
        compose.waitForIdle()
        assertEquals("A new failure must expose the start of its message", 0f, noticeScroll(), 0.5f)
        compose.onNodeWithText(text(R.string.player_access_denied)).assertIsDisplayed()
        compose.runOnIdle { assertTrue("Rendering engine errors must not issue commands", fixture.playback.commands.isEmpty()) }
    }

    private fun publishProblem(id: PlaybackProblemId) {
        val previous = (fixture.playback.connection.value as PlaybackConnection.Connected).snapshot
        fixture.playback.connection.value = PlaybackConnection.Connected(PlaybackSnapshot.create(
            sessionId = previous.sessionId,
            revision = previous.revision + 1,
            queue = previous.queue,
            transport = PlaybackTransport(
                problem = PlaybackProblem(id, PlaybackProblemCode.AccessDenied, previous.queue.current),
            ),
            positionRevision = previous.positionRevision,
            capabilities = previous.capabilities,
        ))
    }

    private fun launch(commandGate: CompletableDeferred<Unit>? = null) {
        fixture = PlayerHostActivityFixture(
            viewport = DpSize(320.dp, 240.dp), fontScale = 2f, locale = testLocale,
            content = {
                val model = metroViewModel<PlayerViewModel>(key = PlayerViewModelKey)
                val lifecycleOwner = LocalLifecycleOwner.current
                val ui = model.collectMviUiWithLifecycle(lifecycleOwner, minActiveState = Lifecycle.State.STARTED)
                SideEffect { viewModel = model }
                PlayerFullPlayer(
                    state = ui.state,
                    canSubmit = ui.operations[PlayerViewModel.RequestKey].canSubmit,
                    onIntent = ui.onIntent,
                    clock = clock,
                    artwork = { fixture.artwork },
                    lifecycleOwner = lifecycleOwner,
                    onClose = {},
                    onOpenLibrary = {},
                )
            },
        )
        val session = PlaybackSessionId("notice-session")
        val entry = QueueEntry(
            QueueEntryId("notice-entry"), MediaTrack(TrackId("notice-track"), "Трек", "Исполнитель", "Альбом", 120_000),
        )
        val target = PlaybackItemToken(session, entry.entryId, activation = 0)
        val snapshot = PlaybackSnapshot.create(
            sessionId = session,
            queue = PlaybackQueue.create(entries = listOf(entry), current = target),
            transport = PlaybackTransport(phase = PlaybackPhase.Ready),
            capabilities = setOf(PlaybackCapability.SetPlayRequested),
        )
        fixture.playback.connection.value = PlaybackConnection.Connected(snapshot)
        fixture.playback.progress.value = PlaybackProgress(
            target = target, positionRevision = snapshot.positionRevision,
            positionMs = 30_000, durationMs = 120_000, bufferedPositionMs = null,
            sampledAtMs = 10_000, advancing = false,
        )
        fixture.playback.commandReply = AppResult.Failure(PlaybackCommandError.StartNotAllowed)
        fixture.playback.commandGate = commandGate
        PlayerHostTestActivity.fixture = fixture
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(Intent(context, PlayerHostTestActivity::class.java))
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("player_full").isDisplayed() && ::viewModel.isInitialized &&
                viewModel.snapshot.value.state.liveSnapshot != null && fixture.playback.activeProgress.get() == 1
        }
    }

    private fun awaitNotice() {
        compose.waitUntil(5_000) { viewModel.snapshot.value.state.commandNotice != null }
        compose.waitForIdle()
    }

    private fun noticeScroll() = compose.onNodeWithTag("player_notice").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun play() = compose.onNode(
        hasContentDescription(text(DesignSystemR.string.nw_player_play)) and hasAnyAncestor(hasTestTag("player_full")),
    )

    private fun text(id: Int): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(testLocale)
        return context.createConfigurationContext(configuration).getString(id)
    }
}
