package com.zaus.nullwave

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithText
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.di.LibraryDetailsNavigationGraph
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import com.zaus.nullwave.feature.library.impl.R as LibraryR

class ArtistNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBackRestoresTheArtistsTabAndItsScrolledPosition() {
        show()
        compose.onNodeWithText(compose.activity.getString(LibraryR.string.library_tab_artists)).performClick()
        openAndReturn("library_list", "library_artist:fixture:artist35", 35, ::systemBack)
    }

    @Test
    fun toolbarBackRestoresSearchQueryAndScrollAndDuplicateNamesKeepTheirIds() {
        search()
        openAndReturn("search_results", "search_artist:fixture:artist35", 35) { artistBack() }
        compose.onNodeWithTag("search_input").assertTextContains("Artist")
        // Artist 35 and 36 have the same display name; each page must read its own ID.
        openAndReturn("search_results", "search_artist:fixture:artist36", 36) { artistBack() }
        compose.onNodeWithTag("search_input").assertTextContains("Artist")
    }

    @Test
    fun twoQuickArtistBackTapsReturnToSearchOnlyOnce() {
        search()
        openAndReturn("search_results", "search_artist:fixture:artist35", 35) {
            compose.mainClock.autoAdvance = false
            try { artistBack(); artistBack() } finally { compose.mainClock.autoAdvance = true }
        }
        compose.onNodeWithTag("search_input").assertTextContains("Artist")
    }

    @Test
    fun albumTapAfterArtistBackBeforeTheNextFrameKeepsSearchVisible() {
        search()
        awaitTag("search_results")
        compose.onNodeWithTag("search_results").performScrollToIndex(35)
        compose.onNodeWithTag("search_artist:fixture:artist35").performClick()
        awaitTag("artist_details_list")
        compose.onNodeWithTag("artist_details_list").performScrollToNode(hasTestTag("artist_details_albums"))
        val album = compose.onNodeWithTag("artist_details_album:fixture:1")
        album.assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            artistBack()
            // Before the next frame the outgoing card is still drawn, but its entry has been popped.
            album.assertIsDisplayed().performClick()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithTag("album_back").assertDoesNotExist()
        compose.onNodeWithTag("search_input").assertIsDisplayed().assertTextContains("Artist")
    }

    @Test
    fun albumBackRestoresTheArtistShelfBeforeReturningToSearch() {
        search()
        openAndReturn("search_results", "search_artist:fixture:artist35", 35) {
            compose.onNodeWithTag("artist_details_list").performScrollToNode(hasTestTag("artist_details_albums"))
            compose.onNodeWithTag("artist_details_albums").performScrollToIndex(8)
            val album = compose.onNodeWithTag("artist_details_album:fixture:9")
            val before = album.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            album.performClick()
            compose.onNodeWithText("Track from Record 9").assertIsDisplayed()
            compose.onNodeWithTag("album_back").performClick()
            awaitTag("artist_details_album:fixture:9")
            val after = album.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(before.left, after.left, 1f)
            assertEquals(before.top, after.top, 1f)
            systemBack()
        }
        compose.onNodeWithTag("search_input").assertTextContains("Artist")
    }

    @Test
    fun restoredArtistEntryKeepsItsTrackPositionAndBackDestination() {
        val restore = show()
        compose.onNodeWithText(compose.activity.getString(LibraryR.string.library_tab_artists)).performClick()
        openAndReturn("library_list", "library_artist:fixture:artist35", 35) {
            compose.onNodeWithTag("artist_details_list").performScrollToIndex(43)
            val trackTag = "artist_details_track:fixture:artist35:track40"
            val before = compose.onNodeWithTag(trackTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
            restore.emulateSavedInstanceStateRestore()
            awaitTag(trackTag)
            val after = compose.onNodeWithTag(trackTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
            assertEquals(before, after, 1f)
            artistBack()
        }
    }

    private fun search() {
        show()
        compose.onNodeWithTag("library_search").performClick()
        compose.onNodeWithTag("search_input").performTextReplacement("Artist")
        compose.onNodeWithTag("search_input").performImeAction()
    }

    private fun openAndReturn(list: String, cardTag: String, number: Int, back: () -> Unit) {
        awaitTag(list)
        compose.onNodeWithTag(list).performScrollToIndex(number)
        val card = compose.onNodeWithTag(cardTag)
        val before = card.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        card.performClick()
        awaitTag("artist_details_list")
        compose.onNodeWithTag("artist_details_list").performScrollToNode(
            hasTestTag("artist_details_track:fixture:artist$number:track1"))
        compose.onNodeWithTag("artist_details_track:fixture:artist$number:track1").assertIsDisplayed()
        back()
        awaitTag(cardTag)
        val after = card.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, after.left, 1f)
        assertEquals(before.top, after.top, 1f)
    }

    private fun artistBack() { compose.onNodeWithTag("artist_details_back").performClick() }
    private fun systemBack() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() } }
    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun show(): StateRestorationTester {
        val graph = createGraph<LibraryDetailsNavigationGraph>()
        return StateRestorationTester(compose).also { restore ->
            restore.setContent {
                NullWaveTheme {
                    CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
                        NullWaveNavigation(graph.entries, rememberNullWaveNavigationScope())
                    }
                }
            }
        }
    }
}
