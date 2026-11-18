package com.zaus.nullwave

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.di.LibraryDetailsNavigationGraph
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import com.zaus.nullwave.feature.library.impl.R as LibraryR

class AlbumNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBackReturnsToTheScrolledAlbumsGrid() {
        show()
        compose.onNodeWithText(compose.activity.getString(LibraryR.string.library_tab_albums)).performClick()
        openAndReturn("library_list", "library_album:fixture:35") {
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        }
    }

    @Test
    fun toolbarBackReturnsToTheSameSearchQueryAndResultPosition() {
        searchForRecords()
        openAndReturn("search_results", "search_album:fixture:35") { compose.onNodeWithTag("album_back").performClick() }
        compose.onNodeWithTag("search_input").assertTextContains("Record")
    }

    @Test
    fun twoAlbumBackTapsBeforeTheNextFrameReturnToSearchOnlyOnce() {
        searchForRecords()
        openAndReturn("search_results", "search_album:fixture:35") {
            compose.mainClock.autoAdvance = false
            try {
                val back = compose.onNodeWithTag("album_back")
                back.performClick()
                back.performClick()
            } finally {
                compose.mainClock.autoAdvance = true
            }
        }
        compose.onNodeWithTag("search_input").assertTextContains("Record")
    }

    private fun searchForRecords() {
        show()
        compose.onNodeWithTag("library_search").performClick()
        compose.onNodeWithTag("search_input").performTextReplacement("Record")
        compose.onNodeWithTag("search_input").performImeAction()
    }

    private fun openAndReturn(listTag: String, albumTag: String, goBack: () -> Unit) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(listTag)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(listTag).performScrollToIndex(35)
        val card = compose.onNodeWithTag(albumTag)
        val before = card.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        card.performClick()
        compose.onNodeWithText("Track from Record 35").assertIsDisplayed()
        goBack()
        val after = card.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, after.left, 1f)
        assertEquals(before.top, after.top, 1f)
    }

    private fun show() {
        val graph = createGraph<LibraryDetailsNavigationGraph>()
        compose.setContent {
            NullWaveTheme {
                CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
                    NullWaveNavigation(graph.entries, rememberNullWaveNavigationScope())
                }
            }
        }
    }
}
