package com.zaus.nullwave.feature.player.ui

import android.content.Intent
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.R as DesignSystemR
import com.zaus.nullwave.core.designsystem.theme.NullWaveColors
import com.zaus.nullwave.core.media.model.ArtworkRef
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.media.waveform.Waveform
import com.zaus.nullwave.core.media.waveform.WaveformSource
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.feature.player.impl.R
import com.zaus.nullwave.feature.player.presentation.PlayerIntent
import com.zaus.nullwave.feature.player.presentation.PlayerState
import com.zaus.nullwave.feature.player.presentation.PlayerViewModel
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real Activity/host ownership; direct content is used only for controlled viewport and gestures. */
class PlayerFullPlayerInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var fixture = PlayerHostActivityFixture()
    private var scenario: ActivityScenario<PlayerHostTestActivity>? = null
    private val track = MediaTrack(
        TrackId("full-track"), "Low Orbit", "Nullwave", "Transmission", 120_000,
        artwork = ArtworkRef("external_primary:42", "thumbnail-v1"),
    )

    @After
    fun close() {
        try { scenario?.close() } finally { PlayerHostTestActivity.fixture = null }
    }

    @Test
    fun openingClosingBackAndRecreationKeepTheActivityPlayerAndSingleBinding() {
        connect(snapshot())
        val activity = launch()
        lateinit var original: PlayerViewModel
        activity.onActivity { original = it.playerViewModel() }
        val contentBefore = compose.onNodeWithTag("player_content").getUnclippedBoundsInRoot()

        openFull()
        // A disabled sheet drag gesture must not leave a second clickable toolbar above our header.
        compose.onAllNodes(
            hasClickAction() and
                hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)) and
                !hasAnyAncestor(hasTestTag("player_full")),
        ).assertCountEquals(0)
        val contentUnderFull = compose.onNodeWithTag("player_content").getUnclippedBoundsInRoot()
        assertEquals("Expansion must preserve the navigation viewport", contentBefore, contentUnderFull)
        compose.onNodeWithTag("player_full_artwork").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, fixture.playback.stateStarts.get())
            assertEquals(1, fixture.playback.progressStarts.get())
            assertTrue(fixture.artwork.requests.any {
                it.artwork == track.artwork && it.title == track.album && it.size > 40.dp
            })
        }
        compose.onNodeWithTag("player_full_close").performClick()
        awaitClosed()
        compose.onNodeWithTag("player_mini").assertIsDisplayed()
        openFull()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitClosed()
        compose.onNodeWithTag("host_library").assertIsDisplayed()
        assertEquals("Collapsing must preserve the navigation viewport", contentBefore,
            compose.onNodeWithTag("player_content").getUnclippedBoundsInRoot())
        openFull()

        activity.recreate()
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("player_full").isDisplayed() &&
                fixture.playback.activeStates.get() == 1 && fixture.playback.activeProgress.get() == 1
        }
        compose.onNodeWithTag("player_full_title").assertIsDisplayed()
        activity.onActivity { assertSame(original, it.playerViewModel()) }
        compose.runOnIdle {
            assertEquals(1, fixture.playback.maxActiveStates.get())
            assertEquals(1, fixture.playback.maxActiveProgress.get())
            assertTrue("Opening/restoring/collapsing UI must not change sound", fixture.playback.commands.isEmpty())
            assertEquals(0, fixture.playback.reconnects)
        }
        compose.onNodeWithTag("player_full_close").performClick()
        awaitClosed()
        compose.onNodeWithTag("player_mini").assertIsDisplayed()
    }

    @Test
    fun transportAndSeekUseTheSharedControllerWithoutOptimisticPlaybackState() {
        val initial = snapshot()
        connect(initial)
        launch()
        openFull()

        control(DesignSystemR.string.nw_player_previous).performScrollTo().performClick()
        awaitCommandCount(1)
        control(DesignSystemR.string.nw_player_play).performClick()
        awaitCommandCount(2)
        // The fake backend does not publish a new snapshot: Play remains Play until it does.
        control(DesignSystemR.string.nw_player_play).assertIsDisplayed()
        control(DesignSystemR.string.nw_player_next).performClick()
        awaitCommandCount(3)
        seek().performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        awaitCommandCount(4)
        compose.runOnIdle {
            assertEquals(listOf(
                PlaybackCommand.Previous,
                PlaybackCommand.SetPlayRequested(true),
                PlaybackCommand.Next,
                PlaybackCommand.SeekTo(initial.queue.current!!, 90_000),
            ), fixture.playback.commands)
            assertEquals(30_000L, fixture.playback.progress.value?.positionMs)
        }
        assertEquals("Completed seek draft must return to the published position",
            0.25f, seek().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f)
    }

    @Test
    fun fullSheetDrawsOnlySelectedTrackPeaksAndSeekWorksDuringLoading() {
        val secondTrack = track.copy(id = TrackId("second"), title = "Second")
        val thirdTrack = track.copy(id = TrackId("third"), title = "Third")
        val requests = CopyOnWriteArrayList<PeakRequest>()
        fixture = PlayerHostActivityFixture(waveformSource = recordingSource(requests, lateReplyForRequest = 2))
        val first = snapshot()
        connect(first)
        launch()
        openFull()
        compose.waitUntil(5_000) { requests.size == 1 }
        seek().performScrollTo().assertIsEnabled()
        assertUnplayedRail(segmentTicks = true)
        seek().performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        awaitCommandCount(1)
        assertEquals(PlaybackCommand.SeekTo(first.queue.current!!, 60_000), fixture.playback.commands.single())
        assertTallWaveformBar(false)

        val rising = Waveform.of(listOf(0f, 1f, 1f, 1f))
        requests.first().reply.complete(rising)
        assertTallWaveformBar(true, startPercent = 33, endPercent = 42)
        assertTallWaveformBar(true)
        compose.runOnIdle { connect(snapshot(activation = 1, item = secondTrack)) }
        compose.waitUntil(5_000) { requests.size == 2 }
        val second = requests.last()
        try {
            assertTallWaveformBar(false)
            compose.runOnIdle { connect(snapshot(activation = 2, item = thirdTrack)) }
            compose.waitUntil(5_000) { requests.size == 3 }
            assertEquals(listOf(track.id, secondTrack.id, thirdTrack.id), requests.map { it.id })
            assertTrue(second.job.isCancelled)
            // Simulate a late decoder reply for the superseded track and an unavailable current file.
            second.reply.complete(rising)
            requests.last().reply.complete(null)
            compose.waitUntil(5_000) { second.job.isCompleted && requests.last().job.isCompleted }
            assertUnplayedRail(segmentTicks = false)
            assertTallWaveformBar(false)
            seek().assertIsEnabled()
        } finally {
            second.reply.complete(null)
        }
    }

    @Test
    fun rapidAToBToAUsesTheSecondAWhenTheFirstARepliesLast() {
        val other = track.copy(id = TrackId("other"), title = "Other")
        val requests = CopyOnWriteArrayList<PeakRequest>()
        fixture = PlayerHostActivityFixture(waveformSource = recordingSource(requests, lateReplyForRequest = 1))
        connect(snapshot())
        launch()
        openFull()
        compose.waitUntil(5_000) { requests.size == 1 }
        val original = requests.single()
        try {
            compose.runOnIdle { connect(snapshot(activation = 1, item = other)) }
            compose.waitUntil(5_000) { requests.size == 2 }
            compose.runOnIdle { connect(snapshot(activation = 2, item = track)) }
            compose.waitUntil(5_000) { requests.size == 3 }
            assertEquals(listOf(track.id, other.id, track.id), requests.map { it.id })
            assertTrue(original.job.isCancelled)
            assertTrue(requests[1].job.isCancelled)
            assertFalse(original.job.isCompleted)
            seek().performScrollTo()
            assertUnplayedRail(segmentTicks = true)

            requests.last().reply.complete(Waveform.of(listOf(0f, 1f)))
            assertTallWaveformBar(true)
            original.reply.complete(Waveform.of(listOf(1f, 0f)))
            compose.waitUntil(5_000) { original.job.isCompleted }
            assertTallWaveformBar(true)
        } finally {
            original.reply.complete(null)
        }
    }

    @Test
    fun waveformReadBelongsToTheExpandedStartedSheet() {
        val requests = CopyOnWriteArrayList<PeakRequest>()
        fixture = PlayerHostActivityFixture(waveformSource = recordingSource(requests, lateReplyForRequest = 2))
        connect(snapshot())
        val activity = launch()
        compose.runOnIdle { assertTrue(requests.isEmpty()) }
        openFull()
        compose.waitUntil(5_000) { requests.size == 1 }
        val first = requests.single()
        first.reply.complete(Waveform.of(listOf(0f, 1f)))
        seek().performScrollTo()
        assertTallWaveformBar(true)
        activity.moveToState(Lifecycle.State.CREATED)
        activity.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5_000) { requests.size > 1 && requests.last().job.isActive }
        assertTallWaveformBar(false)
        val pending = requests.last()
        try {
            activity.moveToState(Lifecycle.State.CREATED)
            compose.waitUntil(5_000) { pending.job.isCancelled }
            activity.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(5_000) { requests.last() !== pending && requests.last().job.isActive }
            pending.reply.complete(Waveform.of(listOf(1f, 1f)))
            compose.waitUntil(5_000) { pending.job.isCompleted }
            assertUnplayedRail(segmentTicks = true)
        } finally {
            pending.reply.complete(null)
        }
        val resumed = requests.last()
        compose.onNodeWithTag("player_full_close").performClick()
        awaitClosed()
        compose.waitUntil(5_000) { resumed.job.isCancelled }
        compose.runOnIdle {
            assertTrue(requests.none { it.job.isActive })
            assertTrue(fixture.playback.commands.isEmpty())
        }
    }

    private class PeakRequest(val id: TrackId, val job: Job) {
        val reply = CompletableDeferred<Waveform?>()
    }

    private fun recordingSource(
        requests: MutableList<PeakRequest>,
        lateReplyForRequest: Int? = null,
    ) = WaveformSource { trackId ->
        val request = PeakRequest(trackId, currentCoroutineContext().job)
        requests += request
        if (requests.size == lateReplyForRequest) withContext(NonCancellable) { request.reply.await() }
        else request.reply.await()
    }

    private fun assertUnplayedRail(segmentTicks: Boolean) {
        val outline = NullWaveColors.Cyberpunk.outline.toArgb()
        compose.waitUntil(5_000) {
            val pixels = seek().captureToImage().toPixelMap()
            val region = (pixels.width * 60 / 100) until (pixels.width * 85 / 100)
            val filled = region.count { x -> pixels[x, pixels.height / 2].toArgb() == outline }
            if (segmentTicks) filled in 1 until region.count() / 2 else filled == region.count()
        }
    }

    private fun assertTallWaveformBar(expected: Boolean, startPercent: Int = 66, endPercent: Int = 80) {
        // Check the unplayed region above the midline so the playhead and time ticks are excluded.
        val outline = NullWaveColors.Cyberpunk.outline.toArgb()
        compose.waitUntil(5_000) {
            val image = seek().captureToImage()
            val pixels = image.toPixelMap()
            val y = image.height / 3
            val region = (image.width * startPercent / 100) until (image.width * endPercent / 100)
            val tallBar = region.count { x -> pixels[x, y].toArgb() == outline } > region.count() / 2
            tallBar == expected
        }
    }

    @Test
    fun playingSeekKeepsPauseDuringDragAndBufferingAndCommitsOnlyOnRelease() {
        val initial = snapshot(playRequested = true)
        connect(initial)
        val activity = launch()
        openFull()
        seek().performScrollTo().assertIsEnabled()
        val root = compose.onNodeWithTag("player_full")
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val slider = seek().fetchSemanticsNode().boundsInRoot
        root.performTouchInput {
            down(Offset(slider.left + slider.width * 0.25f, slider.center.y) - rootBounds.topLeft)
            moveTo(Offset(slider.left + slider.width * 0.70f, slider.center.y) - rootBounds.topLeft)
        }
        compose.waitUntil(5_000) {
            seek().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current > 0.4f
        }
        control(DesignSystemR.string.nw_player_pause).assertIsEnabled()
        control(DesignSystemR.string.nw_player_play).assertDoesNotExist()
        compose.runOnIdle { assertTrue("A draft must not change playback or dispatch seek", fixture.playback.commands.isEmpty()) }

        root.performTouchInput { up() }
        awaitCommandCount(1)
        compose.runOnIdle {
            val command = fixture.playback.commands.single() as PlaybackCommand.SeekTo
            assertEquals(initial.queue.current, command.target)
            assertTrue("Released seek must use the drafted position", command.positionMs in 60_000L..100_000L)
        }
        lateinit var model: PlayerViewModel
        activity.onActivity {
            model = it.playerViewModel()
            fixture.playback.connection.value = PlaybackConnection.Connected(
                snapshot(playRequested = true, phase = PlaybackPhase.Buffering),
            )
        }
        // Pause was already shown before buffering, so first prove the host consumed the new phase.
        compose.waitUntil(5_000) {
            model.snapshot.value.state.liveSnapshot?.transport?.phase == PlaybackPhase.Buffering
        }
        val pause = control(DesignSystemR.string.nw_player_pause).assertIsEnabled().fetchSemanticsNode()
        assertFalse("Engine buffering must not add a UI state announcement",
            pause.config.contains(SemanticsProperties.StateDescription))
        control(DesignSystemR.string.nw_player_play).assertDoesNotExist()
        compose.runOnIdle { assertEquals("Buffering must not issue Play/Pause or repeat seek", 1, fixture.playback.commands.size) }
    }

    @Test
    fun disconnectKeepsCachedFullMetadataButAnEmptyLiveQueueClosesIt() {
        connect(snapshot())
        val activity = launch()
        openFull()
        activity.onActivity { fixture.playback.connection.value = PlaybackConnection.Disconnected }
        compose.waitUntil(5_000) { control(DesignSystemR.string.nw_player_play).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
        compose.onNodeWithTag("player_full_title").assertIsDisplayed()
        compose.onNodeWithText(track.title).assertIsDisplayed()
        control(DesignSystemR.string.nw_player_previous).assertIsNotEnabled()
        control(DesignSystemR.string.nw_player_next).assertIsNotEnabled()
        seek().assertIsNotEnabled()

        activity.onActivity {
            fixture.playback.connection.value = PlaybackConnection.Connected(
                PlaybackSnapshot.create(PlaybackSessionId("full-empty")),
            )
        }
        awaitClosed()
        compose.onNodeWithTag("player_mini").assertDoesNotExist()
        compose.onNodeWithTag("host_library").assertIsDisplayed()
        compose.runOnIdle { assertTrue(fixture.playback.commands.isEmpty()) }
    }

    @Test
    fun fullPlayerOwnsTheSingleNoticeAndOpenLibraryCollapsesBeforeNavigation() {
        fixture.playback.connection.value = hostProblem(PlaybackProblemCode.AccessDenied)
        launch()
        openFull("Host track")
        compose.onAllNodesWithTag("player_notice").assertCountEquals(1)
        compose.onNodeWithText(text(R.string.player_open_library)).performScrollTo().performClick()
        awaitClosed()
        compose.runOnIdle {
            assertEquals(1, fixture.libraryRequests)
            assertTrue(fixture.playback.commands.isEmpty())
            assertEquals(0, fixture.playback.reconnects)
        }
        compose.onNodeWithTag("host_library").assertIsDisplayed()
        compose.onAllNodesWithTag("player_notice").assertCountEquals(1)
    }

    @Test
    fun replacingTargetDurationOrEnabledDuringATouchDragCannotCommitTheOldSeek() {
        val initial = snapshot()
        val state = mutableStateOf(displayState(initial))
        val canSubmit = mutableStateOf(true)
        val intents = mutableListOf<PlayerIntent>()
        val artwork = RecordingHostArtworkRenderer()
        fixture = PlayerHostActivityFixture(content = {
            PlayerFullPlayer(
                state = state.value, canSubmit = canSubmit.value, onIntent = intents::add,
                clock = PlaybackClock { 10_000 }, artwork = { artwork },
                lifecycleOwner = LocalLifecycleOwner.current, onClose = {}, onOpenLibrary = {},
            )
        })
        launch()
        val changes: List<() -> Unit> = listOf(
            { state.value = displayState(snapshot(activation = 1)) },
            { state.value = displayState(initial, durationMs = 180_000) },
            { canSubmit.value = false },
        )
        changes.forEachIndexed { index, change ->
            compose.runOnIdle { state.value = displayState(initial); canSubmit.value = true }
            seek().performScrollTo().assertIsEnabled()
            val root = compose.onNodeWithTag("player_full")
            val rootBounds = root.fetchSemanticsNode().boundsInRoot
            val slider = seek().fetchSemanticsNode().boundsInRoot
            root.performTouchInput {
                down(Offset(slider.left + slider.width * 0.25f, slider.center.y) - rootBounds.topLeft)
                moveTo(Offset(slider.left + slider.width * 0.70f, slider.center.y) - rootBounds.topLeft)
            }
            compose.waitUntil(5_000) {
                seek().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current > 0.4f
            }
            compose.runOnIdle(change)
            compose.waitForIdle()
            root.performTouchInput { up() }
            compose.runOnIdle { assertTrue("Invalidated drag $index must not submit a seek", intents.isEmpty()) }
        }
    }

    @Test
    fun compactRussianFullPlayerKeepsCloseTransportAndLongNoticeActionsReachable() {
        val russian = Locale.forLanguageTag("ru")
        val problem = hostProblem(PlaybackProblemCode.AccessDenied)
        val artwork = RecordingHostArtworkRenderer()
        var libraryRequests = 0
        fixture = PlayerHostActivityFixture(
            viewport = DpSize(320.dp, 240.dp), fontScale = 2f, locale = russian,
            content = {
                PlayerFullPlayer(
                    state = PlayerState(connection = problem), canSubmit = true, onIntent = {},
                    clock = PlaybackClock { 10_000 }, artwork = { artwork },
                    lifecycleOwner = LocalLifecycleOwner.current, onClose = {},
                    onOpenLibrary = { libraryRequests++ },
                )
            },
        )
        launch()
        val viewport = compose.onNodeWithTag("host_test_viewport").getUnclippedBoundsInRoot()
        assertEquals(240f, (viewport.bottom - viewport.top).value, 0.5f)
        compose.onNodeWithTag("player_full_close").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        listOf(
            DesignSystemR.string.nw_player_previous, DesignSystemR.string.nw_player_play, DesignSystemR.string.nw_player_next,
            DesignSystemR.string.nw_player_shuffle, DesignSystemR.string.nw_player_repeat,
        )
            .forEach { id ->
                val button = control(id).performScrollTo()
                button.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                val bounds = button.getUnclippedBoundsInRoot()
                assertTrue("Complete control must fit after scrolling: $bounds in $viewport",
                    bounds.left >= viewport.left - 0.5.dp && bounds.right <= viewport.right + 0.5.dp &&
                        bounds.top >= viewport.top - 0.5.dp && bounds.bottom <= viewport.bottom + 0.5.dp)
            }
        val action = compose.onNodeWithText(text(R.string.player_open_library)).performScrollTo().assertIsDisplayed()
        val actionBounds = action.fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("player_full")
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val pixelsPerDp = rootBounds.width / 320f
        assertTrue("The scrolled action must expose a complete touch target", actionBounds.height / pixelsPerDp >= 47.5f)
        root.performTouchInput { click(actionBounds.center - rootBounds.topLeft) }
        compose.runOnIdle { assertEquals(1, libraryRequests) }
    }

    private fun connect(snapshot: PlaybackSnapshot) {
        fixture.playback.connection.value = PlaybackConnection.Connected(snapshot)
        fixture.playback.progress.value = progress(snapshot)
    }

    private fun launch(): ActivityScenario<PlayerHostTestActivity> {
        PlayerHostTestActivity.fixture = fixture
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ActivityScenario.launch<PlayerHostTestActivity>(Intent(context, PlayerHostTestActivity::class.java)).also {
            scenario = it
            compose.waitUntil(5_000) {
                if (fixture.content != null) compose.onNodeWithTag("player_full").isDisplayed()
                else compose.onNodeWithTag("player_mini").isDisplayed()
            }
        }
    }

    private fun openFull(title: String = track.title) {
        compose.onNodeWithText(title).performClick()
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("player_full").isDisplayed() &&
                compose.onNodeWithTag("player_full_close").isDisplayed()
        }
        compose.waitForIdle()
    }

    private fun awaitClosed() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("player_full").fetchSemanticsNodes().isEmpty() }
    }

    private fun awaitCommandCount(expected: Int) {
        compose.waitUntil(5_000) { fixture.playback.commands.size == expected }
        compose.waitForIdle()
    }

    private fun control(description: Int) = compose.onNode(
        hasContentDescription(text(description)) and hasAnyAncestor(hasTestTag("player_full")),
    )

    private fun seek() = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and hasAnyAncestor(hasTestTag("player_full_seek")),
    )

    private fun text(id: Int): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = fixture.locale ?: return context.getString(id)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration).getString(id)
    }

    private fun displayState(snapshot: PlaybackSnapshot, durationMs: Long = 120_000) = PlayerState(
        connection = PlaybackConnection.Connected(snapshot), progress = progress(snapshot, durationMs),
    )

    private fun snapshot(
        activation: Long = 0,
        playRequested: Boolean = false,
        phase: PlaybackPhase = PlaybackPhase.Ready,
        item: MediaTrack = track,
    ): PlaybackSnapshot {
        val session = PlaybackSessionId("full-session")
        val entry = QueueEntry(QueueEntryId("full-entry"), item)
        return PlaybackSnapshot.create(
            sessionId = session,
            queue = PlaybackQueue.create(entries = listOf(entry), current = PlaybackItemToken(session, entry.entryId, activation)),
            transport = PlaybackTransport(phase = phase, playRequested = playRequested),
            capabilities = setOf(PlaybackCapability.SetPlayRequested, PlaybackCapability.Previous, PlaybackCapability.Next, PlaybackCapability.SeekTo),
        )
    }

    private fun progress(snapshot: PlaybackSnapshot, durationMs: Long = 120_000) = PlaybackProgress(
        target = snapshot.queue.current!!, positionRevision = snapshot.positionRevision,
        positionMs = 30_000, durationMs = durationMs, bufferedPositionMs = null,
        sampledAtMs = 10_000, advancing = false,
    )
}
