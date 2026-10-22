package com.zaus.nullwave.feature.search.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import com.zaus.nullwave.feature.search.presentation.SearchBody
import com.zaus.nullwave.feature.search.presentation.SearchState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** New composition starts with the new identity; changing it AFTER restore misses the regression. */
@RunWith(Parameterized::class)
class SearchScrollRestorationTest(
    private val restoredQuery: String,
    private val restoredLocale: String,
    private val shouldRestore: Boolean,
) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun savedPositionBelongsToQueryAndLocaleEvenWithDelayedResults() {
        val ready = SearchBody.Ready(searchResults(60))
        var initialState = SearchState("en", "orbit", body = ready)
        lateinit var screenState: MutableState<SearchState>
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            screenState = remember { mutableStateOf(initialState) }
            SearchTestTheme { SearchScreen(screenState.value, {}, {}) }
        }
        compose.onNodeWithTag("search_input").performImeAction()
        compose.onNodeWithTag("search_results").performScrollToNode(hasTestTag("search_track:track:40"))
        val before = compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top
        // Plain variable: the saved screen still has OLD inputs. Only the new composition sees this.
        compose.runOnIdle { initialState = SearchState(restoredLocale, restoredQuery, body = SearchBody.Loading) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("search_loading").assertIsDisplayed()
        compose.onNodeWithTag("search_results").assertDoesNotExist()
        compose.runOnIdle { screenState.value = screenState.value.copy(body = ready) }
        if (shouldRestore) {
            compose.onNodeWithTag("search_track:track:40").assertIsDisplayed()
            assertEquals(before, compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top, 1f)
        } else {
            compose.onNodeWithTag("search_track:track:0").assertIsDisplayed()
            compose.onNodeWithTag("search_track:track:40").assertDoesNotExist()
        }

        // A rejected old payload must not poison subsequent saves under the new identity.
        compose.onNodeWithTag("search_results").performScrollToNode(hasTestTag("search_track:track:30"))
        val newPosition = compose.onNodeWithTag("search_track:track:30").fetchSemanticsNode().boundsInRoot.top
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("search_loading").assertIsDisplayed()
        compose.runOnIdle { screenState.value = screenState.value.copy(body = ready) }
        compose.onNodeWithTag("search_track:track:30").assertIsDisplayed()
        assertEquals(newPosition, compose.onNodeWithTag("search_track:track:30").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "query={0}, locale={1}, restore={2}")
        fun cases() = listOf(
            arrayOf<Any>("orbit", "en", true),
            arrayOf<Any>("  ORBIT  ", "en", true),
            arrayOf<Any>("other", "en", false),
            arrayOf<Any>("orbit", "ru", false),
        )
    }
}
