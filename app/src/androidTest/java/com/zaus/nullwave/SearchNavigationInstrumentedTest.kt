package com.zaus.nullwave

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test

/** Uses the production Metro graph, permission UI service and Navigation 3 entry owner. */
class SearchNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun querySurvivesActivityRecreationAndPoppingCreatesANewSearchEntry() {
        compose.onNodeWithTag("library_search").performClick()
        compose.onNodeWithTag("search_idle").assertIsDisplayed()
        compose.onNodeWithTag("search_input").performTextReplacement("  Orbit  ")
        compose.onNodeWithTag("search_input").performImeAction()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("search_screen").assertIsDisplayed()
        compose.onNodeWithTag("search_input").assertTextContains("  Orbit  ")
        compose.onNodeWithTag("search_back").performClick()
        compose.onNodeWithTag("library_screen").assertIsDisplayed()
        compose.onNodeWithTag("library_search").performClick()
        compose.onNodeWithTag("search_idle").assertIsDisplayed()
        compose.onNodeWithTag("search_input").assertTextEquals("")
    }

    @Test
    fun systemBackAfterImeDismissalReturnsToLibrary() {
        compose.onNodeWithTag("library_search").performClick()
        compose.onNodeWithTag("search_input").performImeAction()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("library_screen").assertIsDisplayed()
    }
}
