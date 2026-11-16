package com.zaus.nullwave.feature.library.ui

import com.zaus.nullwave.feature.library.api.ui.LibraryAccessUi
import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.testTag
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.core.designsystem.R as DesignR
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.presentation.LibraryBody
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.player.api.launch.PlaybackLaunchState
import com.zaus.nullwave.feature.library.presentation.LibraryState
import com.zaus.nullwave.feature.library.presentation.RefreshState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class LibraryScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val robot by lazy { LibraryScreenRobot(compose) }
    private val track = MediaTrack(TrackId("test:1"), "Low Orbit", null, null, 203_000)

    @Test
    fun currentTrackIsAnnouncedIndependentlyOfPlayOrPause() {
        val other = track.copy(id = TrackId("test:2"), title = "Other")
        robot.show(LibraryBody.Ready(listOf(track, other)))
        val current = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, robot.text(DesignR.string.nw_track_current))
        robot.updateCurrentTrack(track.id)
        compose.onNode(current and hasAnyAncestor(hasTestTag("library_track:${track.id.value}"))).assertIsDisplayed()
        robot.updateCurrentTrack(other.id)
        compose.onNode(current and hasAnyAncestor(hasTestTag("library_track:${other.id.value}"))).assertIsDisplayed()
        compose.onAllNodes(current).assertCountEquals(1)
        robot.updateCurrentTrack(null)
        compose.onAllNodes(current).assertCountEquals(0)
    }

    @Test
    fun metadataSummaryAndSubtitleFollowTheCurrentSnapshot() {
        val known = track.copy(artist = "Artist", album = "Album", sizeBytes = 12_000_000)
        robot.show(LibraryBody.Ready(listOf(known)))
        compose.onNodeWithText("Artist · Album", substring = true).assertIsDisplayed()
        val size = Formatter.formatShortFileSize(InstrumentationRegistry.getInstrumentation().targetContext, 12_000_000)
        compose.onNodeWithText(size, substring = true).assertIsDisplayed()
        robot.updateBody(LibraryBody.Ready(listOf(known.copy(sizeBytes = null, artist = null))))
        compose.onNodeWithText(robot.text(R.string.library_size_unknown), substring = true).assertIsDisplayed()
        compose.onNodeWithText("Album", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Artist · Album", substring = true).assertDoesNotExist()
    }

    @Test
    fun menuGetsTheSongCountOnlyAfterTheCatalogIsReady() {
        robot.show(LibraryBody.Loading).visible("library_loading").absent("library_empty")
        compose.onNodeWithContentDescription(robot.text(R.string.library_refresh)).assertIsNotEnabled()
        val menu = compose.onNodeWithTag("test_menu")
        menu.performClick()
        robot.updateBody(LibraryBody.Ready(listOf(track)))
        menu.performClick()
        val count = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .getQuantityString(R.plurals.library_track_count, 1, 1)
        compose.runOnIdle { assertEquals(listOf(null, count), robot.requestedMenuDetails) }
    }

    @Test
    fun permissionGateIsNotAnEmptyLibraryAndBusyActionCannotLaunchAgain() {
        var requests = 0
        robot.show(LibraryBody.AccessRequired(AccessAction.Request),
            access = LibraryAccessUi(busy = true, onAccess = { requests++ }))
            .visible("library_access").absent("library_empty")
        compose.onNodeWithTag("library_access_action").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test
    fun initialErrorOffersRetryWithoutRefresh() {
        robot.show(LibraryBody.Failed(UiText.Resource(R.string.library_load_failed))).visible("library_error")
        compose.onNodeWithText(robot.text(R.string.library_retry)).performClick()
        compose.runOnIdle { assertEquals(listOf(LibraryIntent.Retry), robot.intents) }
        compose.onNodeWithContentDescription(robot.text(R.string.library_refresh)).assertIsNotEnabled()
    }

    @Test
    fun refreshFailureRetainsPlayableTrackAndOffersRetry() {
        robot.show(LibraryBody.Ready(listOf(track), RefreshState.Failed(UiText.Resource(R.string.library_refresh_failed))))
            .visible("library_refresh_error").visible("library_track:test:1")
        compose.onNodeWithText(track.title).assertHasClickAction()
        compose.onNodeWithText(robot.text(R.string.library_unknown_artist), substring = true).assertIsDisplayed()
        compose.onNodeWithText(robot.text(R.string.library_retry)).performClick()
        compose.runOnIdle { assertEquals(LibraryIntent.Retry, robot.intents.single()) }
    }

    @Test
    fun successfulEmptySnapshotRemainsEmptyWhileRefreshing() {
        robot.show(LibraryBody.Ready(emptyList(), RefreshState.Running))
            .visible("library_empty").visible("library_refreshing").absent("library_error")
        compose.onNodeWithContentDescription(robot.text(R.string.library_refreshing)).assertHasNoClickAction()
    }

    @Test
    fun refreshDoesNotMoveTracksAtTheTopOrAtAScrolledPosition() {
        val tracks = (1..40).map { track.copy(id = TrackId("test:$it"), title = "Track $it") }
        robot.show(LibraryBody.Ready(tracks))
        for (id in listOf(1, 25)) {
            val tag = "library_track:test:$id"
            if (id != 1) compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag(tag))
            val before = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            robot.updateBody(LibraryBody.Ready(tracks, RefreshState.Running))
            robot.visible("library_refreshing")
            assertEquals("Refresh must not insert a row or change the toolbar height", before,
                compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
            robot.updateBody(LibraryBody.Ready(tracks))
            robot.absent("library_refreshing")
            assertEquals("Completion must retain the scroll position", before,
                compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun filtersContainSortAndKeepSelectionWhenReopened() {
        robot.show(LibraryBody.Ready(listOf(track)))
        compose.onNodeWithTag("library_sort").assertDoesNotExist()
        compose.onNodeWithText(robot.text(R.string.library_filters_action)).assertIsDisplayed()
        compose.onNodeWithTag("library_filters").assertHeightIsAtLeast(28.dp)
            .assertTouchHeightIsEqualTo(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                robot.text(R.string.library_sort_selection, robot.text(R.string.library_sort_title),
                    robot.text(R.string.library_ascending))))
        compose.onNodeWithTag("library_filters").performClick()
        compose.onNodeWithText(robot.text(R.string.library_filters)).assertIsDisplayed()
        compose.onNodeWithText(robot.text(R.string.library_sort)).assertIsDisplayed()
        compose.onNodeWithText(robot.text(R.string.library_sort_direction)).assertIsDisplayed()
        compose.onNodeWithText(robot.text(R.string.library_sort_artist)).performClick()
        compose.onNodeWithText(robot.text(R.string.library_sort_artist)).assertIsSelected()
        compose.onNodeWithText(robot.text(R.string.library_descending)).performClick()
        compose.runOnIdle {
            assertEquals(LibraryIntent.SortChanged(LibraryTrackSort.Artist, SortDirection.Descending), robot.intents.last())
        }
        compose.onNodeWithText(robot.text(R.string.library_done)).performClick()
        compose.onNodeWithText(robot.text(R.string.library_descending)).assertDoesNotExist()
        compose.onNodeWithTag("library_filters").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            robot.text(R.string.library_sort_selection, robot.text(R.string.library_sort_artist),
                robot.text(R.string.library_descending))))
        compose.onNodeWithText(robot.text(R.string.library_filters_action)).assertIsDisplayed()
        compose.onNodeWithTag("library_filters").performClick()
        compose.onNodeWithText(robot.text(R.string.library_sort_artist)).assertIsSelected()
        compose.onNodeWithText(robot.text(R.string.library_descending)).assertIsSelected()
        compose.onNodeWithText(robot.text(R.string.library_done)).performClick()
    }

    @Test
    fun narrowRtlLargeTextListCanReachTheLastTrack() {
        val tracks = (1..40).map { track.copy(id = TrackId("test:$it"), title = "Очень длинное название трека номер $it") }
        robot.show(LibraryBody.Ready(tracks), fontScale = 2f, direction = LayoutDirection.Rtl)
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("library_track:test:40"))
        compose.onNodeWithTag("library_track:test:40").assertIsDisplayed()
        compose.onNodeWithText(tracks.last().title).assertHasClickAction()
    }

    @Test
    fun songClickDispatchesTheDisplayedSnapshotAndSelectedTrack() {
        val tracks = listOf(
            track.copy(id = TrackId("test:z"), title = "Zebra"),
            track.copy(id = TrackId("test:a"), title = "Afterglow"),
            track.copy(id = TrackId("test:m"), title = "Midnight"),
        )
        robot.show(LibraryBody.Ready(tracks))

        compose.onNodeWithText("Afterglow").performClick()

        compose.runOnIdle {
            val intent = robot.intents.single() as LibraryIntent.PlaySong
            assertEquals(tracks[1].id, intent.trackId)
            assertSame("The click must refer to the snapshot rendered by the row", tracks, intent.displayedSongs)
            assertEquals(listOf("test:z", "test:a", "test:m"), intent.displayedSongs.map { it.id.value })
        }
    }

    @Test
    fun playbackFailureCanBeDismissedWithoutMovingTheCatalog() {
        val tracks = (1..40).map { track.copy(id = TrackId("test:$it"), title = "Track $it") }
        robot.show(LibraryBody.Ready(tracks))
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("library_track:test:25"))
        val before = compose.onNodeWithTag("library_track:test:25").fetchSemanticsNode().boundsInRoot

        robot.updatePlaybackError(UiText.Literal("Playback could not start"))
            .visible("library_playback_error")
        compose.onNodeWithText("Playback could not start").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("library_track:test:25").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText(robot.text(R.string.library_playback_dismiss)).performClick()

        robot.absent("library_playback_error")
        compose.runOnIdle { assertEquals(LibraryIntent.DismissPlaybackError, robot.intents.single()) }
        assertEquals(before, compose.onNodeWithTag("library_track:test:25").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun playbackFailureRemainsVisibleAlongsideThePermissionGate() {
        robot.show(LibraryBody.AccessRequired(AccessAction.CheckAgain))
        robot.updatePlaybackError(UiText.Literal("Audio access is unavailable"))
            .visible("library_access").visible("library_playback_error").absent("library_empty")
        compose.onNodeWithText(robot.text(R.string.library_playback_dismiss)).performClick()
        robot.visible("library_access").absent("library_playback_error")
    }
}

private class LibraryScreenRobot(private val compose: ComposeContentTestRule) {
    val intents = mutableListOf<LibraryIntent>()
    private val state = mutableStateOf(LibraryState(LibraryTrackQuery("en")))
    val requestedMenuDetails = mutableListOf<String?>()
    fun text(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    fun show(body: LibraryBody<MediaTrack>, access: LibraryAccessUi = LibraryAccessUi(),
             fontScale: Float = 1f, direction: LayoutDirection = LayoutDirection.Ltr) = apply {
        state.value = LibraryState(LibraryTrackQuery("en"), body)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalLayoutDirection provides direction) {
                NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
                    Box(Modifier.width(320.dp)) {
                        LibraryScreen(state.value, onIntent = {
                            intents += it
                            if (it is LibraryIntent.SortChanged) state.value = state.value.copy(
                                query = state.value.query.copy(sort = it.sort, direction = it.direction))
                            if (it == LibraryIntent.DismissPlaybackError) state.value = state.value.copy(playbackLaunch = state.value.playbackLaunch.dismissed())
                        }, menuButton = { detail ->
                            NullWaveIconButton(NullWaveIcons.Menu, "Menu", { requestedMenuDetails += detail },
                                modifier = Modifier.testTag("test_menu"))
                        }, accessUi = access)
                    }
                }
            }
        }
    }
    fun updateBody(body: LibraryBody<MediaTrack>) = apply {
        compose.runOnIdle { state.value = state.value.copy(body = body) }
    }
    fun updateCurrentTrack(id: TrackId?) = compose.runOnIdle {
        state.value = state.value.copy(playingTrackId = id)
    }
    fun updatePlaybackError(error: UiText) = apply {
        compose.runOnIdle { state.value = state.value.copy(playbackLaunch = PlaybackLaunchState(notice = error)) }
    }
    fun visible(tag: String) = apply { compose.onNodeWithTag(tag).assertIsDisplayed() }
    fun absent(tag: String) = apply { compose.onNodeWithTag(tag).assertDoesNotExist() }
}
