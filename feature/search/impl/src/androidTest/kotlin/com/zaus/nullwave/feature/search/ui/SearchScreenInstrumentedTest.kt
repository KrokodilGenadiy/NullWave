package com.zaus.nullwave.feature.search.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessUi
import com.zaus.nullwave.feature.search.impl.R
import com.zaus.nullwave.feature.search.presentation.SearchBody
import com.zaus.nullwave.feature.search.presentation.SearchIntent
import com.zaus.nullwave.feature.player.api.launch.PlaybackLaunchState
import com.zaus.nullwave.feature.search.presentation.SearchRefresh
import com.zaus.nullwave.feature.search.presentation.SearchState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(SearchState("en"))
    private val intents = mutableListOf<SearchIntent>()
    private var back = 0
    private val openedArtists = mutableListOf<ArtistId>()

    // TrackRow's caller modifier tags its bounds container; the inner Row owns the action.
    private fun trackAction(id: String) = compose.onNode(
        hasClickAction() and hasAnyAncestor(hasTestTag("search_track:$id")),
    )

    @Test
    fun inputPreservesRawTextAndImeDoesNotResubmitOrChangeTheQuery() {
        show()
        compose.onNodeWithTag("search_idle").assertIsDisplayed()
        compose.onNodeWithTag("search_input").performTextReplacement("  ЁЛКА  ")
        compose.onNodeWithTag("search_input").performImeAction()
        compose.runOnIdle {
            assertEquals(listOf(SearchIntent.QueryChanged("  ЁЛКА  ")), intents)
            assertEquals("  ЁЛКА  ", state.value.rawQuery)
        }
        compose.onNodeWithTag("search_back").performClick()
        compose.runOnIdle { assertEquals(1, back) }
    }

    @Test
    fun tracksCanPlayAndHideFilenamesAlbumsAndArtistsOpenByIdentity() {
        state.value = SearchState("en", "orbit", body = SearchBody.Ready(searchResults()))
        show()
        trackAction("track:0").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("orbit_0.flac", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("search_results").performScrollToNode(hasTestTag("search_album:album:1"))
        compose.onNodeWithText("Matching artist: Guest Orbit").assertIsDisplayed()
        compose.onNodeWithTag("search_album:album:1").assertHasClickAction()
        compose.onNodeWithTag("search_results").performScrollToNode(hasTestTag("search_artist:artist:1"))
        compose.onNodeWithTag("search_artist:artist:1").assertIsDisplayed().assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(listOf(ArtistId("artist:1")), openedArtists) }
    }

    @Test
    fun trackClickCarriesTheSelectedTrackAndDisplayedRankingIncludingDuringRefresh() {
        val results = searchResults(3).let { it.copy(tracks = listOf(it.tracks[2], it.tracks[0], it.tracks[1])) }
        state.value = SearchState("en", "orbit", body = SearchBody.Ready(results))
        show()
        compose.onNodeWithTag("search_input").performImeAction()
        trackAction("track:0").performClick()
        compose.runOnIdle {
            assertEquals(SearchIntent.PlayTrack(results.tracks[1].track.id, results.tracks.map { it.track }), intents.single())
            state.value = state.value.copy(body = SearchBody.Ready(results.copy(tracks = results.tracks.reversed()), SearchRefresh.Running))
        }
        trackAction("track:2").performClick()
        compose.runOnIdle {
            assertEquals(SearchIntent.PlayTrack(results.tracks[0].track.id, results.tracks.reversed().map { it.track }), intents.last())
            assertEquals("orbit", state.value.rawQuery)
        }
    }

    @Test
    fun playbackFailureCanBeDismissedWithoutMovingResultsOrChangingTheQuery() {
        state.value = SearchState("en", "orbit", body = SearchBody.Ready(searchResults(60)))
        show()
        compose.onNodeWithTag("search_input").performImeAction()
        compose.onNodeWithTag("search_results").performScrollToIndex(41)
        val anchor = compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle {
            state.value = state.value.copy(playbackLaunch = PlaybackLaunchState(notice =
                UiText.Resource(R.string.search_playback_not_found),
            ))
        }
        compose.onNodeWithTag("search_playback_error").assertIsDisplayed()
        assertEquals(anchor, compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithTag("search_playback_error").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(SearchIntent.DismissPlaybackError, intents.single())
            assertEquals("orbit", state.value.rawQuery)
        }
        assertEquals(anchor, compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test
    fun noMatchesCanClearAndEmptyGroupsHaveNoHeadings() {
        state.value = SearchState("en", "zzz", body = SearchBody.Ready(emptyResults()))
        show()
        compose.onNodeWithTag("search_no_matches").assertIsDisplayed()
        compose.onNodeWithTag("search_section:tracks").assertDoesNotExist()
        compose.onNodeWithText("Clear query").performClick()
        compose.runOnIdle { assertEquals(SearchIntent.ClearQuery, intents.single()) }
        compose.onNodeWithTag("search_idle").assertIsDisplayed()
    }

    @Test
    fun errorAndRefreshErrorRetryWithoutHidingPreviousResults() {
        state.value = SearchState("en", "orbit", body = SearchBody.Failed(UiText.Resource(R.string.search_load_failed)))
        show()
        compose.onNodeWithTag("search_error").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle {
            assertEquals(SearchIntent.Retry, intents.single())
            state.value = state.value.copy(body = SearchBody.Ready(searchResults(), SearchRefresh.Failed(UiText.Resource(R.string.search_refresh_failed))))
        }
        compose.onNodeWithTag("search_refresh_error").assertIsDisplayed()
        compose.onNodeWithTag("search_track:track:0").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(2, intents.size) }
    }

    @Test
    fun refreshFailureStaysReachableDeepInResultsAndDisappearsWithItsState() {
        val ready = SearchBody.Ready(searchResults(60))
        val failed = ready.copy(refresh = SearchRefresh.Failed(UiText.Resource(R.string.search_refresh_failed)))
        state.value = SearchState("en", "orbit", body = ready)
        show()
        compose.onNodeWithTag("search_input").performImeAction()
        compose.onNodeWithTag("search_results").performScrollToIndex(41)
        // Anchor the top item, safely away from both ends and the bottom feedback surface.
        val anchor = compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state.value = state.value.copy(body = failed) }
        compose.onNodeWithTag("search_refresh_error").assertIsDisplayed()
        compose.onAllNodesWithTag("search_refresh_error").assertCountEquals(1)
        assertEquals(anchor, compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle {
            assertEquals(SearchIntent.Retry, intents.single())
            state.value = state.value.copy(body = ready.copy(refresh = SearchRefresh.Running))
        }
        compose.onNodeWithTag("search_refresh_error").assertDoesNotExist()
        compose.onNodeWithTag("search_refreshing").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(body = ready) }
        assertEquals(anchor, compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { state.value = state.value.copy(body = failed) }
        compose.onNodeWithTag("search_refresh_error").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(rawQuery = "other", body = SearchBody.Loading) }
        compose.onNodeWithTag("search_refresh_error").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(body = failed) }
        compose.onNodeWithTag("search_refresh_error").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(body = SearchBody.AccessRequired) }
        compose.onNodeWithTag("search_refresh_error").assertDoesNotExist()
    }

    @Test
    fun busyPermissionRecoveryDisablesBothActionsButNotQueryEditing() {
        state.value = SearchState("en", "orbit", body = SearchBody.AccessRequired)
        show(LibraryAccessUi(busy = true, access = LibraryAccess.Required(AccessAction.SettingsSuggested)))
        compose.onNodeWithTag("search_access_action").assertIsNotEnabled()
        compose.onNodeWithText("Ask again").assertIsNotEnabled()
        compose.onNodeWithTag("search_input").performTextReplacement("")
        compose.onNodeWithTag("search_idle").assertIsDisplayed()
    }

    @Test
    fun delayedResultsRestoreScrollAndDifferentQueryResetsIt() {
        val ready = SearchBody.Ready(searchResults(trackCount = 60))
        state.value = SearchState("en", "orbit", body = ready)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { SearchTestTheme {
            SearchScreen(state.value, {}, {})
        } }
        compose.onNodeWithTag("search_input").performImeAction()
        compose.onNodeWithTag("search_results").performScrollToNode(hasTestTag("search_track:track:40"))
        val before = compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state.value = state.value.copy(body = SearchBody.Loading) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("search_loading").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(body = ready) }
        compose.onNodeWithTag("search_track:track:40").assertIsDisplayed()
        val after = compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 1f)
        compose.runOnIdle { state.value = state.value.copy(rawQuery = "other", body = SearchBody.Loading) }
        compose.waitForIdle()
        compose.runOnIdle { state.value = state.value.copy(body = ready) }
        compose.onNodeWithTag("search_track:track:0").assertIsDisplayed()
        compose.onNodeWithTag("search_track:track:40").assertDoesNotExist()
        compose.onNodeWithTag("search_results").performScrollToNode(hasTestTag("search_track:track:40"))
        compose.runOnIdle { state.value = state.value.copy(localeTag = "ru", body = SearchBody.Loading) }
        compose.waitForIdle()
        compose.runOnIdle { state.value = state.value.copy(body = ready) }
        compose.onNodeWithTag("search_track:track:0").assertIsDisplayed()
        compose.onNodeWithTag("search_track:track:40").assertDoesNotExist()
    }

    private fun show(access: LibraryAccessUi = LibraryAccessUi()) {
        compose.setContent { SearchTestTheme {
            SearchScreen(state.value, { intent ->
                intents += intent
                when (intent) {
                    is SearchIntent.QueryChanged -> state.value = state.value.copy(rawQuery = intent.value,
                        body = if (intent.value.isBlank()) SearchBody.Idle else state.value.body)
                    SearchIntent.ClearQuery -> state.value = SearchState("en")
                    SearchIntent.DismissPlaybackError -> state.value = state.value.copy(playbackLaunch = state.value.playbackLaunch.dismissed())
                    else -> Unit
                }
            }, { back++ }, access, onArtistClick = { openedArtists += it })
        } }
    }
}
