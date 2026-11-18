package com.zaus.nullwave.feature.library.ui.artistdetails

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistDetails
import com.zaus.nullwave.feature.library.api.repository.LibraryArtistDetailsRepository
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.presentation.artistdetails.ArtistDetailsViewModel
import com.zaus.nullwave.feature.library.ui.AndroidLibraryAccessHost
import com.zaus.nullwave.feature.library.ui.FakeAudioPermission
import com.zaus.nullwave.feature.library.ui.RecordingRegistry
import com.zaus.nullwave.feature.library.ui.ScreenOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArtistDetailsRootInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val permission = FakeAudioPermission()
    private val registry = RecordingRegistry()
    private val store = ViewModelStore()
    private lateinit var owner: ScreenOwner
    private val id = ArtistId("fixture:artist")
    private val details = LibraryArtistDetails.create(
        LibraryArtist(id, "Static Pilgrim", 60, 12),
        (1..12).map { LibraryAlbum(AlbumId("fixture:album$it"), "Record $it", AlbumArtistCredit.Various, 9) },
        (1..60).map { MediaTrack(TrackId("fixture:$it"), "Track $it", "Static Pilgrim", "Record 1", 120_000) },
    )
    private val changes = MutableSharedFlow<AppResult<LibraryArtistDetails, DataError>>(extraBufferCapacity = 1)
    private var initial: AppResult<LibraryArtistDetails, DataError> = AppResult.Success(details)
    private var reads = 0
    private var observers = 0
    private var backs = 0
    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @After fun clear() { compose.runOnUiThread { store.clear() } }

    @Test
    fun deniedEntryWaitsForExplicitGrantAndResumeRechecksBeforeReadingAgain() {
        show()
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.onNodeWithTag(ArtistDetailsTags.Back).performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals(0, reads)
            assertTrue(registry.requests.isEmpty())
        }
        compose.onNodeWithTag("library_access_action").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, registry.requests.size)
            permission.granted = true
            registry.reply(true)
        }
        compose.onNodeWithText("Static Pilgrim").assertIsDisplayed()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.onNodeWithText("Static Pilgrim").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, observers)
            permission.granted = false
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.onNodeWithText("Static Pilgrim").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, reads) }
    }

    @Test
    fun retainedArtistStaysHiddenUntilTheNewHostRechecksAccess() {
        val visible = mutableStateOf(true)
        permission.granted = true
        show { visible.value }
        compose.onNodeWithText("Static Pilgrim").assertIsDisplayed()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithTag(ArtistDetailsTags.Screen).assertDoesNotExist()
        compose.runOnIdle {
            permission.granted = false
            visible.value = true
        }
        compose.onNodeWithTag(ArtistDetailsTags.Back).assertIsDisplayed()
        compose.onNodeWithTag("artist_details_loading").assertIsDisplayed()
        compose.onNodeWithText("Static Pilgrim").assertDoesNotExist()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, reads)
            assertTrue(registry.requests.isEmpty())
        }
    }

    @Test
    fun providerDenialHidesBothListsAndCheckAgainRecoversWithoutPermissionPrompt() {
        permission.granted = true
        show()
        compose.onNodeWithText("Static Pilgrim").assertIsDisplayed()
        emit(AppResult.Failure(DataError.AccessDenied))
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.onNodeWithTag(ArtistDetailsTags.List).assertDoesNotExist()
        compose.onNodeWithTag(ArtistDetailsTags.AlbumShelf).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, observers) }
        compose.onNodeWithTag("library_access_action").performScrollTo().performClick()
        compose.onNodeWithText("Static Pilgrim").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, reads)
            assertEquals(1, observers)
            assertTrue(registry.requests.isEmpty())
        }
    }

    @Test
    fun retryRecoversAnInitialReadFailure() {
        permission.granted = true
        initial = AppResult.Failure(DataError.Io)
        show()
        compose.onNodeWithText(resources.getString(R.string.library_artist_details_error)).assertIsDisplayed()
        compose.runOnIdle { initial = AppResult.Success(details) }
        compose.onNodeWithText(resources.getString(R.string.library_retry)).performScrollTo().performClick()
        compose.onNodeWithText("Static Pilgrim").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, reads) }
    }

    @Test
    fun retryStaysVisibleBelowAScrolledListAndPreservesItsPosition() {
        permission.granted = true
        show()
        compose.onNodeWithTag(ArtistDetailsTags.List).performScrollToIndex(43)
        val track = compose.onNodeWithTag(ArtistDetailsTags.track(TrackId("fixture:40")))
        val top = track.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        emit(AppResult.Failure(DataError.Io))
        compose.onNodeWithText(resources.getString(R.string.library_retry)).assertIsDisplayed().performClick()
        compose.onNodeWithTag("artist_details_refresh_error").assertDoesNotExist()
        assertEquals(top, track.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { assertEquals(2, reads) }
    }

    @Test
    fun pauseAndFreshAccessCheckPreserveTheTrackListAndAlbumShelfPositions() {
        permission.granted = true
        show()
        compose.onNodeWithTag(ArtistDetailsTags.AlbumShelf).performScrollToIndex(9)
        val album = compose.onNodeWithTag(ArtistDetailsTags.album(AlbumId("fixture:album10")))
        val left = album.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag(ArtistDetailsTags.List).performScrollToIndex(43)
        val track = compose.onNodeWithTag(ArtistDetailsTags.track(TrackId("fixture:40")))
        val top = track.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.onNodeWithTag(ArtistDetailsTags.List).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, observers); owner.registry.currentState = Lifecycle.State.RESUMED }
        assertEquals(top, track.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag(ArtistDetailsTags.List).performScrollToNode(hasTestTag(ArtistDetailsTags.AlbumShelf))
        assertEquals(left, album.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.runOnIdle { assertEquals(2, reads) }
    }

    @Test
    fun disappearanceDropsTheSnapshotAndTheSameObservationCanRestoreIt() {
        permission.granted = true
        show()
        compose.onNodeWithText("Static Pilgrim").assertIsDisplayed()
        emit(AppResult.Failure(DataError.NotFound))
        compose.onNodeWithText(resources.getString(R.string.library_artist_details_missing)).assertIsDisplayed()
        compose.onNodeWithTag(ArtistDetailsTags.List).assertDoesNotExist()
        compose.onNodeWithTag(ArtistDetailsTags.Back).assertIsDisplayed()
        emit(AppResult.Success(details))
        compose.onNodeWithText("Static Pilgrim").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, reads) }
    }

    private fun emit(result: AppResult<LibraryArtistDetails, DataError>) {
        compose.runOnIdle { check(changes.tryEmit(result)) }
    }

    private fun show(visible: () -> Boolean = { true }) {
        val host = AndroidLibraryAccessHost { permission }
        val registryOwner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val vm = compose.runOnUiThread {
            owner = ScreenOwner()
            ArtistDetailsViewModel(id, object : LibraryArtistDetailsRepository {
                override fun observeArtistDetails(artistId: ArtistId, localeTag: String) = flow {
                    assertEquals(id, artistId)
                    reads++
                    observers++
                    try { emit(initial); emitAll(changes) } finally { observers-- }
                }
            }, resources.configuration.locales[0].toLanguageTag()).also { store.put("artist", it) }
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner, LocalLifecycleOwner provides owner) {
                NullWaveTheme {
                    if (visible()) ArtistDetailsRoot(id, host, null, onBack = { backs++ }, onAlbumClick = {}, viewModel = vm)
                }
            }
        }
    }
}
