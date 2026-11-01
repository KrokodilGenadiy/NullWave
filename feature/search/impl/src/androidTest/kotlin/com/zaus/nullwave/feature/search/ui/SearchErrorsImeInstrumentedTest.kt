package com.zaus.nullwave.feature.search.ui

import android.graphics.Rect
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.feature.search.impl.R
import com.zaus.nullwave.feature.search.presentation.SearchBody
import com.zaus.nullwave.feature.search.presentation.SearchIntent
import com.zaus.nullwave.feature.search.presentation.SearchPlaybackNotice
import com.zaus.nullwave.feature.search.presentation.SearchRefresh
import com.zaus.nullwave.feature.search.presentation.SearchState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Requires an unlocked device with a software IME enabled; no media or playback service is used. */
class SearchErrorsImeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = mutableStateOf(SearchState("en", body = SearchBody.Ready(searchResults())))
    private val intents = mutableListOf<SearchIntent>()
    private lateinit var contentView: View

    @Test
    fun refreshAndPlaybackErrorsStayAboveTheImeAndTheirTouchActionsAreIndependent() {
        compose.setContent {
            val view = LocalView.current
            SideEffect { contentView = view }
            SearchTestTheme { SearchScreen(state.value, ::onIntent, {}) }
        }
        compose.onNodeWithTag("search_input").performClick()
        awaitIme()
        compose.onNodeWithTag("search_input").performTextReplacement("Orbit")
        compose.runOnIdle {
            state.value = state.value.copy(
                body = SearchBody.Ready(searchResults(), SearchRefresh.Failed(UiText.Resource(R.string.search_refresh_failed))),
                playbackNotice = SearchPlaybackNotice(UiText.Resource(R.string.search_playback_connection_failed)),
            )
        }

        awaitIme()
        compose.onNodeWithTag("search_input").assertIsFocused().assertTextContains("Orbit")
        assertMessagesAboveIme()

        // Pointer input proves these targets remain reachable, rather than invoking semantics directly.
        messageAction("search_refresh_error", "Retry").performTouchInput { click() }
        compose.onNodeWithTag("search_refresh_error").assertDoesNotExist()
        compose.onNodeWithTag("search_playback_error").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(SearchIntent.Retry, intents.last())
            assertEquals(SearchRefresh.Running, (state.value.body as SearchBody.Ready).refresh)
            assertEquals("Orbit", state.value.rawQuery)
        }
        awaitIme()

        messageAction("search_playback_error", "Dismiss").performTouchInput { click() }
        compose.onNodeWithTag("search_playback_error").assertDoesNotExist()
        compose.onNodeWithTag("search_input").assertIsFocused().assertTextContains("Orbit")
        compose.runOnIdle {
            assertEquals(listOf(SearchIntent.QueryChanged("Orbit"), SearchIntent.Retry, SearchIntent.DismissPlaybackError), intents)
            assertEquals("Orbit", state.value.rawQuery)
            assertEquals(SearchRefresh.Running, (state.value.body as SearchBody.Ready).refresh)
        }
        awaitIme()
    }

    private fun onIntent(intent: SearchIntent) {
        intents += intent
        state.value = when (intent) {
            is SearchIntent.QueryChanged -> state.value.copy(rawQuery = intent.value)
            SearchIntent.Retry -> state.value.copy(body = (state.value.body as SearchBody.Ready).copy(refresh = SearchRefresh.Running))
            SearchIntent.DismissPlaybackError -> state.value.copy(playbackNotice = null)
            else -> state.value
        }
    }

    private fun messageAction(tag: String, label: String) = compose.onNode(
        hasClickAction() and hasText(label) and hasAnyAncestor(hasTestTag(tag)),
    ).assertIsDisplayed()

    private fun awaitIme() {
        compose.waitUntil(timeoutMillis = 5_000) {
            var visible = false
            compose.activityRule.scenario.onActivity {
                visible = it.hasWindowFocus() && ViewCompat.getRootWindowInsets(it.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            visible
        }
        compose.waitForIdle()
    }

    private fun assertMessagesAboveIme() {
        var diagnostic = "Waiting for error bounds and IME insets"
        // The IME's visible bit can precede its final animation frame; await layout, not a fixed delay.
        try {
            compose.waitUntil(timeoutMillis = 5_000) {
                val playback = compose.onNodeWithTag("search_playback_error").getUnclippedBoundsInRoot()
                val refresh = compose.onNodeWithTag("search_refresh_error").getUnclippedBoundsInRoot()
                var visibleTop = 0f
                var visibleBottom = 0f
                compose.runOnUiThread {
                    val frame = Rect()
                    val origin = IntArray(2)
                    contentView.getWindowVisibleDisplayFrame(frame)
                    contentView.getLocationOnScreen(origin)
                    val density = contentView.resources.displayMetrics.density
                    visibleTop = (frame.top - origin[1]) / density
                    visibleBottom = (frame.bottom - origin[1]) / density
                }
                diagnostic = "playback=$playback, refresh=$refresh, visible=[$visibleTop, $visibleBottom] dp"
                // Visible frame already excludes IME; subtracting it again would double-count adjustResize.
                playback.bottom.value <= refresh.top.value + 0.5f &&
                    playback.top.value >= visibleTop - 0.5f &&
                    refresh.bottom.value <= visibleBottom + 0.5f
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Both messages must fit above the keyboard without overlap: $diagnostic", timeout)
        }
        compose.onNodeWithTag("search_playback_error").assertIsDisplayed()
        compose.onNodeWithTag("search_refresh_error").assertIsDisplayed()
    }
}
