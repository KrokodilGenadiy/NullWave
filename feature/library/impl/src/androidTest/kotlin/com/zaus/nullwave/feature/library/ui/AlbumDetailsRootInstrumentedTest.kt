package com.zaus.nullwave.feature.library.ui

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueOrder
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.core.result.EmptyResult
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumDetails
import com.zaus.nullwave.feature.library.api.repository.LibraryAlbumDetailsRepository
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.presentation.albumdetails.AlbumDetailsViewModel
import com.zaus.nullwave.feature.library.testing.IdlePlaybackController
import com.zaus.nullwave.feature.library.ui.albumdetails.AlbumDetailsRoot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlbumDetailsRootInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val permission = FakeAudioPermission()
    private val registry = RecordingRegistry()
    private val store = ViewModelStore()
    private lateinit var owner: ScreenOwner
    private val id = AlbumId("fixture:1")
    private val details = LibraryAlbumDetails.create(LibraryAlbum(id, "Signals", AlbumArtistCredit.Named("Static"), 1),
        listOf(MediaTrack(TrackId("fixture:9"), "Low Orbit", "Static", "Signals", 120_000)))
    private val changes = MutableSharedFlow<AppResult<LibraryAlbumDetails, DataError>>(extraBufferCapacity = 1)
    private var reads = 0
    private val requests = mutableListOf<PlaybackQueueRequest>()
    private var reply: EmptyResult<PlaybackCommandError> = AppResult.Success(Unit)
    private val playback = object : PlaybackController by IdlePlaybackController {
        override suspend fun execute(command: PlaybackCommand, beforeDispatch: (PlaybackSnapshot?) -> Unit): EmptyResult<PlaybackCommandError> {
            beforeDispatch(null)
            requests += (command as PlaybackCommand.ReplaceQueue).request
            return reply
        }
    }

    @After fun clear() { compose.runOnUiThread { store.clear() } }

    @Test
    fun albumTintFadesFromTheBackToolbarIntoTheHeader() {
        permission.granted = true
        show()
        compose.onNodeWithText("Signals").assertIsDisplayed()
        val screen = compose.onNodeWithTag("album_details")
        val bounds = screen.fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithTag("album_back").fetchSemanticsNode().boundsInRoot
        val pixels = screen.captureToImage().toPixelMap()
        // The left gutter has no text, icon strokes or artwork at either height.
        val x = (back.left - bounds.left).toInt()
        val y = (back.center.y - bounds.top).toInt()
        val toolbar = pixels[x, y]
        val header = pixels[x, y + with(compose.density) { 56.dp.roundToPx() }]
        assertTrue("Expected a yellow fade from toolbar ($toolbar) into header ($header)",
            toolbar.red > header.red && toolbar.green > header.green && header.red > header.blue)
    }

    @Test
    fun deniedEntryWaitsForExplicitGrantAndResumeRechecksBeforeReadingAgain() {
        show()
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(registry.requests.isEmpty())
            assertEquals(0, reads)
        }
        compose.onNodeWithTag("library_access_action").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, registry.requests.size)
            permission.granted = true
            registry.reply(true)
        }
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            permission.granted = false
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.onNodeWithText("Signals").assertDoesNotExist()
        compose.onNodeWithText("Low Orbit").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, reads) }
    }

    @Test
    fun providerRevocationHidesContentAndCheckAgainRecoversWithoutPrompt() {
        permission.granted = true
        show()
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle { check(changes.tryEmit(AppResult.Failure(DataError.AccessDenied))) }
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.onNodeWithText("Signals").assertDoesNotExist()
        compose.onNodeWithText("Low Orbit").assertDoesNotExist()
        compose.onNodeWithTag("library_access_action").performScrollTo().performClick()
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, reads)
            assertTrue(registry.requests.isEmpty())
        }
    }

    @Test
    fun albumButtonsAndNumberedRowStartTheDisplayedAlbum() {
        permission.granted = true
        val track = details.tracks.single()
        val tracks = listOf(track, track.copy(id = TrackId("fixture:2"), title = "Second"))
        show(LibraryAlbumDetails.create(details.album.copy(trackCount = 2), tracks))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText("Signals").assertIsDisplayed()
        compose.onNodeWithText("Low Orbit")
            .assertIsDisplayed()
            .assertTextContains("1")
            .assertTextContains("2:00")
            .assertHasClickAction()
        compose.onNodeWithText(context.getString(R.string.album_details_play)).performClick()
        compose.onNodeWithText("Second").performClick()
        compose.onNodeWithText(context.getString(R.string.album_details_shuffle)).performClick()
        compose.runOnIdle {
            assertEquals(listOf(PlaybackQueueOrder.InOrder, PlaybackQueueOrder.InOrder, PlaybackQueueOrder.Shuffled),
                requests.map { it.order })
            assertEquals(listOf(0, 1), requests.take(2).map { it.startIndex })
            assertTrue(requests.all { it.trackIds == tracks.map { track -> track.id } && it.playRequested })
        }
    }

    @Test
    fun failedStartKeepsAlbumVisibleAndDismissDoesNotReplayIt() {
        permission.granted = true
        reply = AppResult.Failure(PlaybackCommandError.StartNotAllowed)
        show()
        compose.onNodeWithText("Low Orbit").performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.library_playback_start_not_allowed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.library_playback_dismiss)).performClick()
        compose.onNodeWithTag("library_playback_error").assertDoesNotExist()
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, requests.size) }
    }

    @Test
    fun retainedAlbumStaysHiddenUntilTheNewHostRechecksAccess() {
        val visible = mutableStateOf(true)
        permission.granted = true
        show(visible = { visible.value })
        compose.onNodeWithText("Low Orbit").assertIsDisplayed()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.onNodeWithText("Low Orbit").assertDoesNotExist()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithTag("album_details").assertDoesNotExist()
        compose.runOnIdle {
            permission.granted = false
            visible.value = true
        }
        compose.onNodeWithTag("album_back").assertIsDisplayed()
        compose.onNodeWithText("Signals").assertDoesNotExist()
        compose.onNodeWithText("Low Orbit").assertDoesNotExist()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithTag("library_access").assertIsDisplayed()
        compose.onNodeWithText("Low Orbit").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, reads)
            assertTrue(registry.requests.isEmpty())
        }
    }

    @Test
    fun retryIsReachableFromAScrolledAlbumAndKeepsTheVisibleTrackInPlace() {
        val tracks = (1..60).map { details.tracks.single().copy(id = TrackId("fixture:$it"), title = "Track $it") }
        permission.granted = true
        show(LibraryAlbumDetails.create(details.album.copy(trackCount = tracks.size), tracks))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        val anchor = compose.onNodeWithText("Track 40")
        val top = anchor.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { check(changes.tryEmit(AppResult.Failure(DataError.Io))) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val retry = compose.onNodeWithText(context.getString(R.string.library_retry))
        retry.assertIsDisplayed().performClick()
        retry.assertDoesNotExist()
        assertEquals(top, anchor.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { assertEquals(2, reads) }
    }

    private fun show(album: LibraryAlbumDetails = details, visible: () -> Boolean = { true }) {
        val host = AndroidLibraryAccessHost { permission }
        val registryOwner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val model = compose.runOnUiThread {
            owner = ScreenOwner()
            AlbumDetailsViewModel(id, object : LibraryAlbumDetailsRepository {
                override fun observeAlbumDetails(albumId: AlbumId, localeTag: String) =
                    changes.onStart {
                        assertEquals(id, albumId)
                        reads++
                        emit(AppResult.Success(album))
                    }
            }, InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0].toLanguageTag(), playback)
                .also { store.put("details", it) }
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner, LocalLifecycleOwner provides owner) {
                NullWaveTheme { if (visible()) AlbumDetailsRoot(id, host, null, onBack = {}, viewModel = model) }
            }
        }
    }
}
