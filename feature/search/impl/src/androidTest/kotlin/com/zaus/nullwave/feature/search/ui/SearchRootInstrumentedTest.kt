package com.zaus.nullwave.feature.search.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.search.presentation.SearchAccess
import com.zaus.nullwave.feature.search.presentation.SearchBody
import com.zaus.nullwave.feature.search.presentation.SearchViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchRootInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val access = FakeAccessHost()
    private val repository = RecordingSearchRepository()
    private val store = ViewModelStore()
    private lateinit var vm: SearchViewModel
    private lateinit var owner: RootOwner
    private val visible = mutableStateOf(true)

    @After fun close() { compose.runOnUiThread { store.clear() } }

    @Test
    fun emptyQueryNeverPromptsAndExplicitGrantSearchesOnce() {
        show()
        compose.onNodeWithTag("search_idle").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, access.requests); assertTrue(repository.calls.isEmpty()) }
        input("Orbit")
        compose.onNodeWithTag("search_access_action").performClick().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, access.requests); access.reply(LibraryAccess.Granted) }
        awaitTrack()
        compose.runOnIdle { assertEquals(1, repository.calls.size) }
        compose.onNodeWithTag("search_input").performImeAction()
        compose.runOnIdle { assertEquals(1, repository.calls.size) }
    }

    @Test
    fun lateGrantAfterClearingQueryDoesNotReadTheCatalog() {
        show()
        input("Orbit")
        compose.onNodeWithTag("search_access_action").performClick()
        input("")
        compose.runOnIdle { access.reply(LibraryAccess.Granted) }
        compose.onNodeWithTag("search_idle").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(SearchAccess.Granted, vm.snapshot.value.state.access)
            assertNull(vm.snapshot.value.state.request)
            assertTrue(repository.calls.isEmpty())
        }
    }

    @Test
    fun resumeRechecksRevocationBeforeAProtectedSearchCanRestart() {
        access.access = LibraryAccess.Granted
        show("Orbit")
        awaitTrack()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitUntil { repository.active == 0 }
        val previous = compose.runOnIdle {
            access.access = LibraryAccess.Required(AccessAction.Request)
            repository.calls.size
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithTag("search_access").assertIsDisplayed()
        compose.onNodeWithTag("search_track:track:0").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(previous, repository.calls.size)
            assertEquals(0, repository.active)
            assertNull(vm.snapshot.value.state.request)
        }
    }

    @Test
    fun stoppedSettingsResultWaitsForResumeAndKeepsTheQuery() {
        show("  Orbit  ")
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            access.reply(LibraryAccess.Granted)
        }
        compose.runOnIdle { assertTrue(repository.calls.isEmpty()) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        awaitTrack()
        compose.runOnIdle {
            assertEquals("  Orbit  ", vm.snapshot.value.state.rawQuery)
            assertEquals(1, repository.calls.size)
            assertEquals("orbit", repository.calls.single().text)
        }
    }

    @Test
    fun removalCancelsObservationAndReentryChecksAccessAgain() {
        access.access = LibraryAccess.Granted
        show("Orbit")
        awaitTrack()
        compose.runOnIdle { visible.value = false }
        compose.waitUntil { repository.active == 0 }
        compose.runOnIdle {
            access.access = LibraryAccess.Required(AccessAction.Request)
            visible.value = true
        }
        compose.onNodeWithTag("search_access").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, repository.calls.size); assertEquals(2, access.checks) }
    }

    @Test
    fun providerDenialClearsResultsAndFreshAccessCheckCanRecover() {
        access.access = LibraryAccess.Granted
        show("Orbit")
        awaitTrack()
        compose.runOnIdle { repository.result.value = AppResult.Failure(DataError.AccessDenied) }
        compose.onNodeWithTag("search_access").assertIsDisplayed()
        compose.onNodeWithTag("search_track:track:0").assertDoesNotExist()
        compose.waitUntil { repository.active == 0 }
        compose.runOnIdle { repository.result.value = AppResult.Success(searchResults()) }
        // A later provider value cannot revive the terminal observation without a fresh check.
        compose.onNodeWithTag("search_access").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, repository.calls.size) }
        compose.onNodeWithText("Check access").performClick()
        compose.runOnIdle { access.reply(LibraryAccess.Granted) }
        compose.waitUntil { repository.calls.size == 2 }
        awaitTrack()
        compose.runOnIdle { assertEquals(2, repository.calls.size) }
    }

    @Test
    fun refreshFailureKeepsRowsAndRetryRecollects() {
        access.access = LibraryAccess.Granted
        show("Orbit")
        awaitTrack()
        compose.runOnIdle { repository.result.value = AppResult.Failure(DataError.Io) }
        compose.onNodeWithTag("search_refresh_error").assertIsDisplayed()
        compose.onNodeWithTag("search_track:track:0").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil { repository.calls.size == 2 }
        compose.runOnIdle { repository.result.value = AppResult.Success(searchResults()) }
        compose.onNodeWithTag("search_refresh_error").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, repository.active) }
    }

    private fun show(query: String = "") {
        compose.runOnUiThread {
            owner = RootOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
            vm = SearchViewModel(repository, SavedStateHandle(mapOf("search.query" to query)), "en")
            store.put("search", vm)
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                SearchTestTheme { if (visible.value) SearchRoot({}, access, viewModel = vm) }
            }
        }
    }

    private fun input(text: String) { compose.onNodeWithTag("search_input").performTextReplacement(text) }
    private fun awaitTrack() {
        compose.waitUntil { vm.snapshot.value.state.body is SearchBody.Ready }
        compose.onNodeWithTag("search_track:track:0").assertIsDisplayed()
    }
}
