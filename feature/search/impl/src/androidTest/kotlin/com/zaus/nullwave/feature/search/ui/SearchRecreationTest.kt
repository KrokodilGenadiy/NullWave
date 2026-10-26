package com.zaus.nullwave.feature.search.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.search.presentation.SearchBody
import com.zaus.nullwave.feature.search.presentation.SearchViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/** Real Activity/entry UI saver; the permission API and repository are controlled by the fixture. */
class SearchRecreationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<SearchTestActivity>? = null
    private val fixture = SearchActivityFixture()

    @After fun close() { scenario?.close(); SearchTestActivity.fixture = null }

    @Test
    fun recreationRetainsQueryAndScrollWhileTheNextReadIsDelayed() {
        fixture.access.access = LibraryAccess.Granted
        fixture.repository.result.value = AppResult.Success(searchResults(60))
        val activity = launch()
        compose.onNodeWithTag("search_input").performTextReplacement("  Orbit  ")
        awaitResults(activity)
        // Dismiss the real IME before comparing viewport geometry across Activity recreation.
        compose.onNodeWithTag("search_input").performImeAction()
        compose.onNodeWithTag("search_results").performScrollToNode(hasTestTag("search_track:track:40"))
        val before = compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top
        lateinit var previous: SearchTestActivity
        lateinit var model: SearchViewModel
        activity.onActivity { previous = it; model = it.model }
        activity.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil { fixture.repository.active == 0 }
        fixture.repository.gate = CompletableDeferred()
        activity.recreate()
        activity.moveToState(Lifecycle.State.RESUMED)
        activity.onActivity {
            assertNotSame(previous, it)
            assertSame(model, it.model)
            assertEquals("  Orbit  ", it.model.snapshot.value.state.rawQuery)
        }
        compose.waitUntil { fixture.repository.calls.size == 2 }
        // Same-query refresh keeps the list, including while the replacement source is delayed.
        compose.onNodeWithTag("search_track:track:40").assertIsDisplayed()
        compose.runOnUiThread { fixture.repository.gate!!.complete(Unit) }
        awaitResults(activity)
        assertEquals(before, compose.onNodeWithTag("search_track:track:40").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test
    fun permissionRevokedWhileStoppedRemovesRetainedResultsAfterRecreation() {
        fixture.access.access = LibraryAccess.Granted
        val activity = launch()
        compose.onNodeWithTag("search_input").performTextReplacement("Orbit")
        awaitResults(activity)
        activity.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil { fixture.repository.active == 0 }
        fixture.access.access = LibraryAccess.Required(AccessAction.Request)
        activity.recreate()
        activity.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("search_access").assertIsDisplayed()
        compose.onNodeWithTag("search_track:track:0").assertDoesNotExist()
        activity.onActivity { assertEquals("Orbit", it.model.snapshot.value.state.rawQuery) }
        compose.runOnIdle { assertEquals(1, fixture.repository.calls.size) }
    }

    @Test
    fun recreationWithOpenImeRestoresEditingAndAcceptsFurtherInput() {
        fixture.access.access = LibraryAccess.Granted
        val activity = launch()
        enterQueryWithIme(activity, "  Orbit  ")
        compose.onNodeWithTag("search_input").assertIsFocused()
        lateinit var old: SearchTestActivity
        lateinit var model: SearchViewModel
        activity.onActivity { old = it; model = it.model }
        activity.recreate()
        activity.onActivity {
            assertNotSame(old, it)
            assertSame(model, it.model)
            assertEquals("Query must survive Activity recreation in the retained VM", "  Orbit  ", it.model.snapshot.value.state.rawQuery)
        }
        awaitIme(activity, visible = true)
        compose.onNodeWithTag("search_input").assertIsFocused().assertTextContains("  Orbit  ")
            .performTextReplacement("Orbit again")
        activity.onActivity { assertEquals("Orbit again", it.model.snapshot.value.state.rawQuery) }
    }

    @Test
    fun recreationAfterImeSearchKeepsKeyboardHiddenUntilTheInputIsTapped() {
        val activity = launch()
        enterQueryWithIme(activity, "Orbit")
        compose.onNodeWithTag("search_input").performImeAction()
        awaitIme(activity, visible = false)
        activity.recreate()
        assertBrowsingAfterRecreation(activity)
    }

    @Test
    fun recreationAfterSystemBackDismissesImeDoesNotReopenIt() {
        val activity = launch()
        enterQueryWithIme(activity, "Orbit")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitIme(activity, visible = false)
        compose.onNodeWithTag("search_screen").assertIsDisplayed()
        activity.recreate()
        assertBrowsingAfterRecreation(activity)
    }

    @Test
    fun ordinaryResumeDoesNotAutofocusAfterKeyboardDismissal() {
        val activity = launch()
        enterQueryWithIme(activity, "Orbit")
        compose.onNodeWithTag("search_input").performImeAction()
        awaitIme(activity, visible = false)
        activity.moveToState(Lifecycle.State.CREATED)
        activity.moveToState(Lifecycle.State.RESUMED)
        awaitIme(activity, visible = false)
        compose.onNodeWithTag("search_input").assertTextContains("Orbit")
        compose.runOnIdle { assertEquals(2, fixture.access.checks) }
    }

    private fun assertBrowsingAfterRecreation(activity: ActivityScenario<SearchTestActivity>) {
        awaitIme(activity, visible = false)
        compose.onNodeWithTag("search_input").assertIsNotFocused().assertTextContains("Orbit")
        compose.onNodeWithTag("search_input").performClick()
        awaitIme(activity, visible = true)
        compose.onNodeWithTag("search_input").assertIsFocused()
    }

    private fun enterQueryWithIme(activity: ActivityScenario<SearchTestActivity>, query: String) {
        compose.onNodeWithTag("search_input").performClick()
        awaitIme(activity, visible = true)
        // SetText is a semantics edit, not a keystroke delivered through the IME connection.
        // Apply it after connection setup and prove both renderer and VM contain the fixture text.
        compose.onNodeWithTag("search_input").performTextReplacement(query)
        compose.onNodeWithTag("search_input").assertTextContains(query)
        activity.onActivity { assertEquals("Query must be present BEFORE recreation", query, it.model.snapshot.value.state.rawQuery) }
    }

    /** Real window insets, not a mock keyboard controller; the device needs a software IME enabled. */
    private fun awaitIme(activity: ActivityScenario<SearchTestActivity>, visible: Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            var matches = false
            activity.onActivity {
                val insets = ViewCompat.getRootWindowInsets(it.window.decorView)
                matches = it.hasWindowFocus() && insets != null &&
                    insets.isVisible(WindowInsetsCompat.Type.ime()) == visible
            }
            matches
        }
        compose.waitForIdle()
    }

    private fun launch(): ActivityScenario<SearchTestActivity> {
        SearchTestActivity.fixture = fixture
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ActivityScenario.launch<SearchTestActivity>(Intent(context, SearchTestActivity::class.java)).also { scenario = it }
    }

    private fun awaitResults(activity: ActivityScenario<SearchTestActivity>) {
        compose.waitUntil {
            var ready = false
            activity.onActivity { ready = it.model.snapshot.value.state.body is SearchBody.Ready }
            ready
        }
        compose.onNodeWithTag("search_results").assertIsDisplayed()
    }
}

internal class SearchActivityFixture {
    val access = FakeAccessHost()
    val repository = RecordingSearchRepository()
}

class SearchTestActivity : ComponentActivity() {
    internal lateinit var model: SearchViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fixture = checkNotNull(fixture)
        model = ViewModelProvider(this, viewModelFactory {
            initializer { SearchViewModel(fixture.repository, createSavedStateHandle(), "en") }
        })[SearchViewModel::class.java]
        setContent { SearchTestTheme { SearchRoot({}, fixture.access, viewModel = model) } }
    }
    companion object { internal var fixture: SearchActivityFixture? = null }
}
