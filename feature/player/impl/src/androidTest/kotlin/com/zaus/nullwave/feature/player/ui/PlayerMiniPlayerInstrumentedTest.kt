package com.zaus.nullwave.feature.player.ui

import android.content.Intent
import android.graphics.Rect
import android.view.KeyEvent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.R as DesignSystemR
import com.zaus.nullwave.core.designsystem.theme.NullWaveColors
import com.zaus.nullwave.core.media.model.ArtworkRef
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
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
import com.zaus.nullwave.feature.player.impl.R
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real host and Activity; backend and artwork are deterministic and never open a media service. */
class PlayerMiniPlayerInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val fixture = PlayerHostActivityFixture()
    private var scenario: ActivityScenario<PlayerHostTestActivity>? = null
    private val track = MediaTrack(
        TrackId("mini-track"), "Low Orbit", "Nullwave", "Transmission", 120_000,
        artwork = ArtworkRef("external_primary:42", "thumbnail-v1"),
    )

    @After
    fun close() {
        try { scenario?.close() } finally { PlayerHostTestActivity.fixture = null }
    }

    @Test
    fun metadataArtworkAndMatchingProgressUpdateWithoutRecomposingArtwork() {
        val control = snapshot()
        fixture.playback.connection.value = PlaybackConnection.Connected(control)
        fixture.playback.progress.value = progress(control, positionMs = 30_000)
        val activity = launch()

        compose.onNodeWithTag("player_mini").assertIsDisplayed()
        compose.onNodeWithText(track.title).assertIsDisplayed().assert(!hasClickAction())
        compose.onNodeWithText(track.artist!!).assertIsDisplayed()
        awaitStrip(filledAt = 0.20f, emptyAt = 0.80f)
        var artworkCompositions = 0
        activity.onActivity {
            assertEquals(RecordingHostArtworkRenderer.Request(track.artwork, track.album!!, 40.dp), fixture.artwork.requests.last())
            artworkCompositions = fixture.artwork.requests.size
            fixture.playback.progress.value = progress(control, positionMs = 90_000)
        }
        awaitStrip(filledAt = 0.70f, emptyAt = 0.85f)
        activity.onActivity {
            assertEquals("Position sampling must not recompose the artwork slot", artworkCompositions, fixture.artwork.requests.size)
            assertEquals(1, fixture.playback.stateStarts.get())
            assertEquals(1, fixture.playback.progressStarts.get())
            // A foreign activation cannot paint an old track's position under this metadata.
            fixture.playback.progress.value = progress(control, 110_000).copy(
                target = control.queue.current!!.copy(activation = 1),
            )
        }
        awaitStrip(filledAt = null, emptyAt = 0.20f)

        val next = track.copy(title = "Signal", artist = null, album = null, artwork = null)
        activity.onActivity { fixture.playback.connection.value = PlaybackConnection.Connected(snapshot(track = next, activation = 1)) }
        compose.onNodeWithText("Signal").assertIsDisplayed()
        compose.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.player_unknown_artist),
        ).assertIsDisplayed()
        compose.onNodeWithText("Low Orbit").assertDoesNotExist()
        activity.onActivity {
            assertEquals(RecordingHostArtworkRenderer.Request(null, "Signal", 40.dp), fixture.artwork.requests.last())
            assertTrue(fixture.playback.commands.isEmpty())
        }
    }

    @Test
    fun transportUsesExplicitPlayIntentAndIndependentCapabilitiesWhileBusyBlocksBothActions() {
        fixture.playback.connection.value = PlaybackConnection.Connected(snapshot(playRequested = true, phase = PlaybackPhase.Buffering))
        val activity = launch()
        val gate = CompletableDeferred<Unit>()
        activity.onActivity { fixture.playback.commandGate = gate }
        action(DesignSystemR.string.nw_player_pause).assertIsEnabled().performClick()
        action(DesignSystemR.string.nw_player_pause).assertIsNotEnabled()
        action(DesignSystemR.string.nw_player_next).assertIsNotEnabled()
        activity.onActivity {
            assertEquals(listOf(PlaybackCommand.SetPlayRequested(false)), fixture.playback.commands)
            fixture.playback.connection.value = PlaybackConnection.Connected(snapshot())
            fixture.playback.commandGate = null
            gate.complete(Unit)
        }
        action(DesignSystemR.string.nw_player_play).assertIsEnabled().performClick()
        activity.onActivity {
            assertEquals(listOf(PlaybackCommand.SetPlayRequested(false), PlaybackCommand.SetPlayRequested(true)), fixture.playback.commands)
            fixture.playback.connection.value = PlaybackConnection.Connected(snapshot(capabilities = setOf(PlaybackCapability.Next)))
        }
        action(DesignSystemR.string.nw_player_play).assertIsNotEnabled()
        action(DesignSystemR.string.nw_player_next).assertIsEnabled().performClick()
        activity.onActivity {
            assertEquals(PlaybackCommand.Next, fixture.playback.commands.last())
            fixture.playback.connection.value = PlaybackConnection.Connected(snapshot(capabilities = setOf(PlaybackCapability.SetPlayRequested)))
        }
        action(DesignSystemR.string.nw_player_play).assertIsEnabled()
        action(DesignSystemR.string.nw_player_next).assertIsNotEnabled()
        activity.onActivity { assertEquals(3, fixture.playback.commands.size) }
    }

    @Test
    fun cachedMetadataSurvivesDisconnectWithDisabledControlsAndEmptyLiveQueueRemovesMini() {
        fixture.playback.connection.value = PlaybackConnection.Disconnected
        val activity = launch()
        compose.onNodeWithTag("player_mini").assertDoesNotExist()
        activity.onActivity { assertTrue(fixture.artwork.requests.isEmpty()) }
        activity.onActivity { fixture.playback.connection.value = PlaybackConnection.Connected(snapshot()) }
        compose.onNodeWithText(track.title).assertIsDisplayed()
        action(DesignSystemR.string.nw_player_play).assertIsEnabled()
        activity.onActivity { fixture.playback.connection.value = PlaybackConnection.Connecting }
        compose.onNodeWithText(track.title).assertIsDisplayed()
        action(DesignSystemR.string.nw_player_play).assertIsNotEnabled()
        action(DesignSystemR.string.nw_player_next).assertIsNotEnabled()
        activity.onActivity {
            fixture.playback.connection.value = PlaybackConnection.Connected(PlaybackSnapshot.create(PlaybackSessionId("mini-session")))
        }
        compose.onNodeWithTag("player_mini").assertDoesNotExist()
        activity.onActivity {
            fixture.playback.connection.value = PlaybackConnection.Disconnected
            assertTrue(fixture.playback.commands.isEmpty())
            assertEquals(0, fixture.playback.reconnects)
        }
        compose.onNodeWithTag("player_mini").assertDoesNotExist()
    }

    /** Requires an unlocked device with a software IME; no audio files or permission grants needed. */
    @Test
    fun imeHidesMiniAndNoticeWithoutRebindingAndBothReturnWithoutOverlappingContent() {
        val activity = launch() // Fatal playback fixture displays metadata and a global notice together.
        compose.onNodeWithTag("host_open_search").performClick()
        awaitImeAndHost(activity, visible = false, phase = "Before opening IME")
        assertBottomRegionsDoNotOverlap()
        compose.onNodeWithTag("host_search_input").performClick()
        awaitImeAndHost(activity, visible = true, phase = "After opening IME")
        compose.onNodeWithTag("host_search_input").performTextReplacement("Orbit")
        compose.onNodeWithTag("player_mini").assertDoesNotExist()
        compose.onNodeWithTag("player_notice").assertDoesNotExist()
        activity.onActivity {
            assertEquals(1, fixture.playback.activeStates.get())
            assertEquals(1, fixture.playback.activeProgress.get())
            assertEquals(1, fixture.playback.stateStarts.get())
            assertEquals(1, fixture.playback.progressStarts.get())
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitImeAndHost(activity, visible = false, phase = "After system Back closes IME")
        assertBottomRegionsDoNotOverlap()
        activity.onActivity {
            assertEquals(1, fixture.playback.stateStarts.get())
            assertEquals(1, fixture.playback.progressStarts.get())
            assertTrue(fixture.playback.commands.isEmpty())
        }
    }

    private fun launch(): ActivityScenario<PlayerHostTestActivity> {
        PlayerHostTestActivity.fixture = fixture
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ActivityScenario.launch<PlayerHostTestActivity>(Intent(context, PlayerHostTestActivity::class.java))
            .also {
                scenario = it
                compose.waitUntil(5_000) { fixture.playback.activeStates.get() == 1 && fixture.playback.activeProgress.get() == 1 }
            }
    }

    private fun action(id: Int) = compose.onNodeWithContentDescription(
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id),
    )

    private fun awaitStrip(filledAt: Float?, emptyAt: Float) {
        val cyan = NullWaveColors.Cyberpunk.secondary.toArgb()
        compose.waitUntil(5_000) {
            val image = compose.onNodeWithTag("player_mini").captureToImage()
            val pixels = image.toPixelMap()
            // The read-only strip is two dp high; this sample avoids its rounded endpoints and glow.
            val y = 1.coerceAtMost(image.height - 1)
            (filledAt == null || pixels[(image.width * filledAt).toInt(), y].toArgb() == cyan) &&
                pixels[(image.width * emptyAt).toInt(), y].toArgb() != cyan
        }
    }

    private fun assertBottomRegionsDoNotOverlap() {
        compose.onNodeWithTag("player_mini").assertIsDisplayed()
        compose.onNodeWithTag("player_notice").assertIsDisplayed()
        val mini = compose.onNodeWithTag("player_mini").getUnclippedBoundsInRoot()
        val notice = compose.onNodeWithTag("player_notice").getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag("host_search").getUnclippedBoundsInRoot()
        val tolerance = 0.5.dp
        assertTrue("Mini and notice overlap: mini=$mini, notice=$notice", mini.bottom <= notice.top + tolerance || notice.bottom <= mini.top + tolerance)
        assertTrue("Navigation overlaps the bottom host: content=$content, mini=$mini, notice=$notice", content.bottom <= minOf(mini.top, notice.top) + tolerance)
    }

    private fun awaitImeAndHost(activity: ActivityScenario<PlayerHostTestActivity>, visible: Boolean, phase: String) {
        var diagnostic = "Waiting for window and Player host"
        // Platform visibility may report the target before Compose receives animated insets.
        // Subscription startup also doesn't prove that the first snapshot has been rendered.
        try {
            compose.waitUntil(5_000) {
                var focused = false
                var imeVisible: Boolean? = null
                var visibleTop = Float.NaN
                var visibleBottom = Float.NaN
                activity.onActivity {
                    focused = it.hasWindowFocus()
                    imeVisible = ViewCompat.getRootWindowInsets(it.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime())
                    it.composeView?.let { view ->
                        val frame = Rect()
                        val origin = IntArray(2)
                        view.getWindowVisibleDisplayFrame(frame)
                        view.getLocationOnScreen(origin)
                        val density = view.resources.displayMetrics.density
                        // Visible frame already excludes IME; don't subtract adjustResize twice.
                        visibleTop = (frame.top - origin[1]) / density
                        visibleBottom = (frame.bottom - origin[1]) / density
                    }
                }
                val mini = compose.onNodeWithTag("player_mini")
                val notice = compose.onNodeWithTag("player_notice")
                val miniCount = compose.onAllNodesWithTag("player_mini").fetchSemanticsNodes().size
                val noticeCount = compose.onAllNodesWithTag("player_notice").fetchSemanticsNodes().size
                // During hide, nodes can disappear between separate display/bounds reads.
                val miniShown = !visible && imeVisible == false && mini.isDisplayed()
                val noticeShown = !visible && imeVisible == false && notice.isDisplayed()
                val miniBounds = if (miniShown) mini.getUnclippedBoundsInRoot() else null
                val noticeBounds = if (noticeShown) notice.getUnclippedBoundsInRoot() else null
                diagnostic = "focus=$focused, ime=$imeVisible (expected $visible), " +
                    "mini=$miniBounds (nodes=$miniCount), notice=$noticeBounds (nodes=$noticeCount), " +
                    "visible=[$visibleTop, $visibleBottom] dp"
                val hostReady = if (visible) {
                    miniCount == 0 && noticeCount == 0
                } else {
                    miniBounds != null && noticeBounds != null &&
                        miniBounds.top.value >= visibleTop - 0.5f && miniBounds.bottom.value <= visibleBottom + 0.5f &&
                        noticeBounds.top.value >= visibleTop - 0.5f && noticeBounds.bottom.value <= visibleBottom + 0.5f
                }
                focused && imeVisible == visible && hostReady
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("$phase: IME and rendered Player host did not settle; $diagnostic", timeout)
        }
        compose.waitForIdle()
    }

    private fun snapshot(
        track: MediaTrack = this.track,
        activation: Long = 0,
        playRequested: Boolean = false,
        phase: PlaybackPhase = PlaybackPhase.Ready,
        capabilities: Set<PlaybackCapability> = setOf(PlaybackCapability.SetPlayRequested, PlaybackCapability.Next),
    ): PlaybackSnapshot {
        val session = PlaybackSessionId("mini-session")
        val entry = QueueEntry(QueueEntryId("mini-entry"), track)
        return PlaybackSnapshot.create(
            sessionId = session,
            queue = PlaybackQueue.create(entries = listOf(entry), current = PlaybackItemToken(session, entry.entryId, activation)),
            transport = PlaybackTransport(phase = phase, playRequested = playRequested),
            capabilities = capabilities,
        )
    }

    private fun progress(snapshot: PlaybackSnapshot, positionMs: Long) = PlaybackProgress(
        target = snapshot.queue.current!!,
        positionRevision = snapshot.positionRevision,
        positionMs = positionMs,
        durationMs = 120_000,
        bufferedPositionMs = null,
        sampledAtMs = 10_000,
        advancing = false,
    )
}
