package com.zaus.nullwave.feature.library.ui

import com.zaus.nullwave.feature.library.testing.IdlePlaybackController

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.api.repository.LibraryCatalogRepository
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.platform.AudioPermission
import com.zaus.nullwave.feature.library.presentation.LibraryTab
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real Root/VM/Base plus a fake registry and permission check. Never opens an OS dialog. */
class LibraryRootInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val permission = FakeAudioPermission()
    private val registry = RecordingRegistry()
    private val store = ViewModelStore()
    private lateinit var owner: ScreenOwner
    private lateinit var viewModel: LibraryViewModel
    private var subscriptions = 0
    private val catalogQueries = mutableListOf<Any>()
    private var activeSources = 0
    private var protectedReads = 0
    private var observerRegistrations = 0
    private val configuration = mutableStateOf(Configuration().apply { setLocale(Locale.ENGLISH) })

    @After
    fun clear() { compose.runOnUiThread { store.clear() } }

    @Test
    fun entryNeverPromptsAutomaticallyAndGrantReadsTracksAfterOneLaunch() {
        show()
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.runOnIdle { assertTrue(registry.requests.isEmpty()) }
        clickAccess()
        clickAccess()
        compose.runOnIdle {
            assertEquals(1, registry.requests.size)
            assertEquals(permission.name, registry.requests.single().input)
            permission.granted = true
            registry.reply(true)
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
    }

    @Test
    fun freshGrantBeforeClickAvoidsAnUnnecessaryPrompt() {
        show()
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.runOnIdle { permission.granted = true }
        clickAccess()
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle { assertTrue(registry.requests.isEmpty()) }
    }

    @Test
    fun callbackFlagCannotOverrideTheFreshAndroidCheck() {
        show()
        clickAccess()
        compose.runOnIdle { registry.reply(true) } // Platform seam still reports denied.
        compose.onNodeWithText(text(R.string.library_open_settings)).assertIsDisplayed()
        compose.onNodeWithText("Low Orbit").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.library_ask_again)).performClick()
        compose.runOnIdle { assertEquals(permission.name, registry.requests.last().input) }
    }

    @Test
    fun rationaleCanBeCancelledAndOnlyConfirmationLaunches() {
        permission.rationale = true
        show()
        clickAccess()
        compose.onNodeWithText(text(R.string.library_cancel)).performClick()
        compose.runOnIdle { assertTrue(registry.requests.isEmpty()) }
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        clickAccess()
        compose.onNodeWithText(text(R.string.library_continue)).performClick()
        compose.runOnIdle { assertEquals(1, registry.requests.size) }
    }

    @Test
    fun deniedResultSuggestsSettingsAndCancelledSettingsResultStillRechecksGrant() {
        show()
        clickAccess()
        compose.runOnIdle { registry.reply(false) }
        compose.onNodeWithText(text(R.string.library_open_settings)).performClick()
        compose.runOnIdle {
            val intent = registry.requests.last().input as Intent
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
            assertEquals("package:${InstrumentationRegistry.getInstrumentation().targetContext.packageName}", intent.dataString)
            owner.registry.currentState = Lifecycle.State.CREATED
            permission.granted = true
            registry.reply(ActivityResult(0, null))
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
    }

    @Test
    fun resultBeforeResumeWaitsForResumeAndRefreshesOnlyOnce() {
        show()
        clickAccess()
        val before = compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            subscriptions
        }
        compose.runOnIdle {
            permission.granted = true
            registry.reply(true)
        }
        compose.runOnIdle {
            assertEquals("A stopped result must not restart the source during its grace period", before, subscriptions)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle { assertEquals(before + 1, subscriptions) }
    }

    @Test
    fun resumeBeforeSettingsResultRefreshesOnlyOnceButLaterResumeStillChecks() {
        show()
        clickAccess()
        compose.runOnIdle { registry.reply(false) }
        compose.onNodeWithText(text(R.string.library_open_settings)).performClick()
        val before = compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            subscriptions
        }
        compose.runOnIdle {
            permission.granted = true
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle { registry.reply(ActivityResult(0, null)) }
        compose.runOnIdle {
            assertEquals(before + 1, subscriptions)
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals("A separate return must still invalidate the snapshot", before + 2, subscriptions) }
    }

    @Test
    fun lateCallbackWithChangedGrantIsNotSuppressedByResume() {
        show()
        clickAccess()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        val before = compose.runOnIdle { subscriptions }
        compose.runOnIdle {
            permission.granted = true
            registry.reply(true)
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle { assertEquals(before + 1, subscriptions) }
    }

    @Test
    fun returningWithRevokedPermissionRemovesThePreviouslyVisibleTracks() {
        permission.granted = true
        show()
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            permission.granted = false
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.onNodeWithText("Low Orbit").assertDoesNotExist()
        compose.runOnIdle { assertTrue(registry.requests.isEmpty()) }
    }

    @Test
    fun pendingPermissionSurvivesCompositionRestorationWithoutRelaunching() {
        val restoration = show()
        clickAccess()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("library_access_action").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, registry.requests.size)
            permission.granted = true
            registry.reply(true)
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
    }

    @Test
    fun missingSystemActivityShowsRecoverableMessageAndReleasesLaunchGuard() {
        registry.failLaunch = true
        show()
        clickAccess()
        compose.onNodeWithText(text(R.string.library_access_launch_failed)).assertIsDisplayed()
        compose.runOnIdle {
            registry.failLaunch = false
            owner.registry.currentState = Lifecycle.State.STARTED
            permission.granted = true
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            permission.granted = false
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText(text(R.string.library_access_launch_failed)).assertDoesNotExist()
        clickAccess()
        compose.runOnIdle { assertEquals(2, registry.requests.size) }
    }

    @Test
    fun configurationLocaleReachesTheQueryWithoutPersistingAStaleLocale() {
        permission.granted = true
        show()
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply { setLocale(Locale.forLanguageTag("ru-RU")) }
        }
        compose.runOnIdle { assertEquals("ru-RU", viewModel.snapshot.value.state.query.localeTag) }
    }

    @Test
    fun restoredTabAndUiActionsSelectOneCatalogSourceAndRespectRevocation() {
        permission.granted = true
        show(SavedStateHandle(mapOf("library.tab" to "Albums", "library.albums.direction" to "Descending")))
        compose.onNodeWithText("Test album").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_tab_albums)).assertIsSelected()
        compose.runOnIdle {
            assertEquals(1, activeSources)
            assertTrue(catalogQueries.all { it is LibraryAlbumQuery })
            assertEquals(SortDirection.Descending, (catalogQueries.last() as LibraryAlbumQuery).direction)
        }
        compose.onNodeWithText(text(R.string.library_tab_artists)).performScrollTo().performClick()
        compose.onNodeWithText("Test artist").assertIsDisplayed()
        compose.onNodeWithTag("library_filters").performClick()
        compose.onNodeWithText(text(R.string.library_descending)).performClick()
        compose.onNodeWithText(text(R.string.library_done)).assertIsDisplayed()
        val protectedWork = compose.runOnIdle {
            assertEquals(1, activeSources)
            assertEquals(LibraryTab.Artists, viewModel.snapshot.value.state.selectedTab)
            assertEquals(SortDirection.Descending, (catalogQueries.last() as LibraryArtistQuery).direction)
            owner.registry.currentState = Lifecycle.State.STARTED
            permission.granted = false
            owner.registry.currentState = Lifecycle.State.RESUMED
            protectedReads to observerRegistrations
        }
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_done)).assertDoesNotExist()
        compose.onNodeWithText("Test artist").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.library_tab_songs)).performScrollTo().performClick()
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.runOnIdle {
            // A different tab can recheck permission through the repository. It must not do I/O.
            assertEquals(protectedWork, protectedReads to observerRegistrations)
            assertEquals(LibraryTab.Songs, viewModel.snapshot.value.state.selectedTab)
            assertEquals(0, activeSources)
            owner.registry.currentState = Lifecycle.State.STARTED
            permission.granted = true
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_done)).assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(catalogQueries.last() is LibraryTrackQuery)
            assertEquals(1, activeSources)
        }
    }

    private fun clickAccess() { compose.onNodeWithTag("library_access_action").performClick() }
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(savedState: SavedStateHandle = SavedStateHandle()): StateRestorationTester {
        compose.runOnUiThread {
            owner = ScreenOwner()
            val repository = object : LibraryCatalogRepository {
                override fun observeAlbums(query: LibraryAlbumQuery) = observe(query, listOf(
                    LibraryAlbum(AlbumId("test:album"), "Test album", AlbumArtistCredit.Unknown, 1)))
                override fun observeArtists(query: LibraryArtistQuery) = observe(query, listOf(
                    LibraryArtist(ArtistId("test:artist"), "Test artist", 1, 1)))
                override fun observeTracks(query: LibraryTrackQuery) = observe(query, listOf(
                    MediaTrack(TrackId("test:1"), "Low Orbit", null, null, 203_000)))
            }
            viewModel = LibraryViewModel(repository, savedState, "en", IdlePlaybackController)
            store.put("library", viewModel)
            configuration.value = Configuration(InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration)
        }
        val registryOwner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        return StateRestorationTester(compose).also { restoration ->
            restoration.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner,
                    LocalLifecycleOwner provides owner, LocalConfiguration provides configuration.value) {
                    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
                        LibraryRoot({}, artworkLoader = null, viewModel = viewModel, permission = permission)
                    }
                }
            }
        }
    }

    private fun <T> observe(query: Any, items: List<T>) = flow<AppResult<List<T>, DataError>> {
        subscriptions++
        catalogQueries += query
        activeSources++
        try {
            if (permission.granted) {
                protectedReads++
                observerRegistrations++
                emit(AppResult.Success(items))
                awaitCancellation()
            } else emit(AppResult.Failure(DataError.AccessDenied))
        } finally { activeSources-- }
    }
}

private class ScreenOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}

private class FakeAudioPermission : AudioPermission {
    override val name = "android.permission.READ_MEDIA_AUDIO"
    var granted = false
    var rationale = false
    override fun isGranted() = granted
    override fun shouldExplain() = rationale
}

private class RecordingRegistry : ActivityResultRegistry() {
    data class Request(val code: Int, val input: Any?)
    val requests = mutableListOf<Request>()
    var failLaunch = false
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        requests += Request(requestCode, input)
        if (failLaunch) throw ActivityNotFoundException("Test system activity unavailable")
    }
    fun reply(value: Any?) { dispatchResult(requests.last().code, value) }
}
