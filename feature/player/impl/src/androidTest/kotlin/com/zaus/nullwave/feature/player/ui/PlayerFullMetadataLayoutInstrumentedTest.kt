package com.zaus.nullwave.feature.player.ui

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
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
import com.zaus.nullwave.feature.player.impl.R
import com.zaus.nullwave.feature.player.presentation.PlayerState
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The subtitle follows the last title line while controls and scroll limits stay in place. */
@RunWith(Parameterized::class)
class PlayerFullMetadataLayoutInstrumentedTest(
    private val widthDp: Int,
    private val heightDp: Int,
    private val fontScale: Float,
    private val language: String,
    private val wrappedTitle: String,
) {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<PlayerHostTestActivity>? = null
    private val locale = Locale.forLanguageTag(language)
    private val short = MetadataCase("short", "Low Orbit")
    private val tracks = listOf(
        short,
        MetadataCase(
            "long-title", "Total War THREE KINGDOMS — Dong Zhuo ".repeat(3),
            titleFit = Fit.Truncated,
        ),
        MetadataCase("wraps-without-ellipsis", wrappedTitle, titleFit = Fit.TwoLines),
        MetadataCase(
            "long-japanese", "風の谷のナウシカ 交響組曲 ".repeat(8),
            "東京交響楽団 ".repeat(12), album = null,
            titleFit = Fit.Truncated, subtitleFit = Fit.Truncated,
        ),
        MetadataCase(
            "long-latin",
            "A very long live recording from the edge of the city ".repeat(4),
            "A very long artist credit with multiple guest performers ".repeat(3),
            "The complete anniversary edition with bonus tracks ".repeat(3),
            titleFit = Fit.Truncated, subtitleFit = Fit.Truncated,
        ),
        MetadataCase(
            "long-mixed",
            "Ночной эфир — Night Transmission\n" + "Очень длинное название концертной записи ".repeat(4),
            "Исполнитель и приглашённые музыканты ".repeat(3),
            "Transmission — концертный альбом ".repeat(3),
            titleFit = Fit.Truncated, subtitleFit = Fit.Truncated,
        ),
        MetadataCase("missing", "Эфир", artist = null, album = null, subtitleFit = Fit.Complete),
    )
    private val entries = tracks.map { it.entry }
    private val state = mutableStateOf(playerState(short, activation = 0))

    @After
    fun close() {
        try { scenario?.close() } finally { PlayerHostTestActivity.fixture = null }
    }

    @Test
    fun switchingShortLongAndMissingMetadataPreservesLayoutAtTopAndAfterScrolling() {
        val artwork = RecordingHostArtworkRenderer()
        PlayerHostTestActivity.fixture = PlayerHostActivityFixture(
            viewport = DpSize(widthDp.dp, heightDp.dp), fontScale = fontScale, locale = locale,
            content = {
                PlayerFullPlayer(
                    state = state.value, canSubmit = true, onIntent = {},
                    clock = PlaybackClock { 10_000 }, artwork = { artwork },
                    lifecycleOwner = LocalLifecycleOwner.current, onClose = {}, onOpenLibrary = {},
                )
            },
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(Intent(context, PlayerHostTestActivity::class.java))
        compose.waitUntil(5_000) { compose.onNodeWithTag("player_full").isDisplayed() }
        compose.waitForIdle()

        assertTrackChangesPreserveGeometry()
        val scroll = compose.onNodeWithTag("player_full_scroll")
        val maximum = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        if (fontScale == 2f) assertTrue("The compact large-text case must exercise scrolling", maximum > 0f)
        if (maximum > 0f) {
            scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, maximum) }
            compose.waitForIdle()
            assertEquals("Start the second sequence at the scroll limit", maximum,
                scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.5f)
            assertTrackChangesPreserveGeometry()
        }
    }

    private fun assertTrackChangesPreserveGeometry() {
        val baseline = geometry()
        assertMetadataStack(short, baseline.bounds.getValue("player_full_metadata"))
        for (case in tracks.drop(1) + short) {
            compose.runOnIdle {
                val activation = checkNotNull(state.value.liveSnapshot?.queue?.current).activation + 1
                state.value = playerState(case, activation)
            }
            val track = case.entry.track
            compose.onNodeWithTag("player_full_title").assertTextEquals(track.title)
            compose.onNodeWithTag("player_full_subtitle").assertTextEquals(subtitle(track))
            val actual = geometry()
            for ((tag, expected) in baseline.bounds) {
                val bounds = actual.bounds.getValue(tag)
                assertEquals("$tag left changed for ${track.id}", expected.left.value, bounds.left.value, 0.5f)
                assertEquals("$tag top changed for ${track.id}", expected.top.value, bounds.top.value, 0.5f)
                assertEquals("$tag right changed for ${track.id}", expected.right.value, bounds.right.value, 0.5f)
                assertEquals("$tag bottom changed for ${track.id}", expected.bottom.value, bounds.bottom.value, 0.5f)
            }
            assertEquals("${track.id} scroll offset", baseline.offset, actual.offset, 0.5f)
            assertEquals("${track.id} scroll limit", baseline.maximum, actual.maximum, 0.5f)
            assertMetadataStack(case, actual.bounds.getValue("player_full_metadata"))
        }
    }

    private fun assertMetadataStack(case: MetadataCase, region: DpRect) {
        val title = textLayout("player_full_title")
        val subtitle = textLayout("player_full_subtitle")
        val titleBounds = compose.onNodeWithTag("player_full_title").getUnclippedBoundsInRoot()
        val subtitleBounds = compose.onNodeWithTag("player_full_subtitle").getUnclippedBoundsInRoot()
        with(title.layoutInput.density) {
            assertEquals("The title starts at the top of the metadata region",
                region.top.toPx(), titleBounds.top.toPx(), 1f)
            assertTrue("The metadata region must fit the subtitle",
                subtitleBounds.bottom.roundToPx() <= region.bottom.roundToPx())
            // A Text node may be taller than its final line when minLines reserves empty space.
            val titleBottom = titleBounds.top.toPx() + title.getLineBottom(title.lineCount - 1)
            val subtitleTop = subtitleBounds.top.toPx() + subtitle.getLineTop(0)
            assertEquals("Gap below the last title line for ${case.entry.entryId}",
                4.dp.roundToPx().toFloat(), subtitleTop - titleBottom, 1f)
        }
        assertLinesFit(title, titleBounds, case.titleFit)
        assertLinesFit(subtitle, subtitleBounds, case.subtitleFit)
    }

    private fun assertLinesFit(layout: TextLayoutResult, bounds: DpRect, expected: Fit) {
        val text = layout.layoutInput.text.text
        assertTrue("Expected one or two lines for $text", layout.lineCount in 1..2)
        expected.lineCount?.let { assertEquals(text, it, layout.lineCount) }
        val last = layout.lineCount - 1
        val height = with(layout.layoutInput.density) { (bounds.bottom - bounds.top).toPx() }
        assertTrue("First line clipped at the top for $text", layout.getLineTop(0) >= -1f)
        assertTrue("Last line clipped at the bottom for $text", layout.getLineBottom(last) <= height + 1f)
        assertEquals("Ellipsis for $text", expected == Fit.Truncated, layout.isLineEllipsized(last))
        if (expected != Fit.Truncated) {
            assertEquals("All characters fit for $text", text.length, layout.getLineEnd(last, visibleEnd = true))
        }
    }

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            assertTrue(it(results))
        }
        return results.single()
    }

    private fun geometry(): Geometry {
        compose.waitForIdle()
        val bounds = listOf(
            "player_full_artwork", "player_full_metadata", "player_full_seek", "player_full_transport",
        ).associateWith { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        val scroll = compose.onNodeWithTag("player_full_scroll").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        return Geometry(bounds, scroll.value(), scroll.maxValue())
    }

    private fun subtitle(track: MediaTrack): String {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = base.createConfigurationContext(Configuration(base.resources.configuration).also {
            it.setLocale(locale)
        })
        val artist = track.artist ?: resources.getString(R.string.player_unknown_artist)
        return track.album?.let { resources.getString(R.string.player_artist_album, artist, it) } ?: artist
    }

    private fun playerState(case: MetadataCase, activation: Long): PlayerState {
        val session = PlaybackSessionId("metadata-session")
        val target = PlaybackItemToken(session, case.entry.entryId, activation)
        val snapshot = PlaybackSnapshot.create(
            sessionId = session,
            queue = PlaybackQueue.create(entries = entries, current = target, revision = activation),
            transport = PlaybackTransport(phase = PlaybackPhase.Ready, playRequested = true),
            capabilities = setOf(
                PlaybackCapability.SetPlayRequested, PlaybackCapability.Previous, PlaybackCapability.Next,
                PlaybackCapability.SeekTo, PlaybackCapability.SetShuffle, PlaybackCapability.SetRepeat,
            ),
        )
        return PlayerState(
            connection = PlaybackConnection.Connected(snapshot),
            progress = PlaybackProgress(
                target = target, positionRevision = snapshot.positionRevision, positionMs = 30_000,
                durationMs = 120_000, bufferedPositionMs = null, sampledAtMs = 10_000, advancing = false,
            ),
        )
    }

    private data class Geometry(val bounds: Map<String, DpRect>, val offset: Float, val maximum: Float)

    private enum class Fit(val lineCount: Int?) {
        OneLine(1), TwoLines(2), Truncated(2), Complete(null),
    }

    private class MetadataCase(
        id: String,
        title: String,
        artist: String? = "NW",
        album: String? = "EP",
        val titleFit: Fit = Fit.OneLine,
        val subtitleFit: Fit = Fit.OneLine,
    ) {
        val entry = QueueEntry(QueueEntryId(id), MediaTrack(TrackId(id), title, artist, album, 120_000))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}x{1}dp,font={2},locale={3}")
        fun windows(): List<Array<Any>> = listOf(
            arrayOf(360, 720, 1f, "en", "風の谷のナウシカ交響組曲"),
            arrayOf(412, 820, 1.5f, "ja", "風の谷のナウシカ交響組曲"),
            arrayOf(320, 240, 2f, "ru", "風の谷のナウシカ"),
        )
    }
}
