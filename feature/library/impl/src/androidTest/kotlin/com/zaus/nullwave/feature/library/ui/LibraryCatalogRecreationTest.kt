package com.zaus.nullwave.feature.library.ui

import com.zaus.nullwave.feature.library.testing.IdlePlaybackController

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
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
import com.zaus.nullwave.feature.library.presentation.LibraryBody
import com.zaus.nullwave.feature.library.presentation.LibraryTab
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Real Activity + retained VM + Compose saver. No device permission or MediaStore dependency. */
@RunWith(Parameterized::class)
internal class LibraryCatalogRecreationTest(private val selected: LibraryTab) {
    @get:Rule val compose = createEmptyComposeRule()
    private val fixture = CatalogActivityFixture()
    private var scenario: ActivityScenario<LibraryCatalogTestActivity>? = null
    private lateinit var model: LibraryViewModel
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun close() {
        scenario?.close()
        LibraryCatalogTestActivity.fixture = null
    }

    @Test
    fun delayedCatalogRestoresTabSortAndBothScrollPositionsAfterActivityRecreation() {
        LibraryCatalogTestActivity.fixture = fixture
        val activity = ActivityScenario.launch<LibraryCatalogTestActivity>(
            Intent(context, LibraryCatalogTestActivity::class.java),
        ).also { scenario = it }
        activity.onActivity { model = it.model }
        awaitItem("library_track:track:1")
        select(selected)
        awaitReady(selected)
        compose.onNodeWithTag("library_filters").performClick()
        compose.onNodeWithText(context.getString(R.string.library_descending)).performClick()
        compose.onNodeWithText(context.getString(R.string.library_done)).performClick()
        awaitReady(selected)
        scrollTo(groupTag(40)) // Do not assume any particular scroll policy after changing sort.
        if (selected == LibraryTab.Albums) assertTwoColumns()

        val groupAnchor = groupTag(25)
        val groupBounds = scrollTo(groupAnchor)
        select(LibraryTab.Songs)
        awaitReady(LibraryTab.Songs)
        val songAnchor = "library_track:track:35"
        val songBounds = scrollTo(songAnchor)

        // Returning to the group discards its snapshot but must keep its scroll saver untouched.
        val response = compose.runOnIdle { fixture.hold(selected) }
        select(selected)
        compose.onNodeWithTag("library_loading").assertIsDisplayed()
        compose.onNodeWithTag(groupAnchor).assertDoesNotExist()
        lateinit var previousActivity: LibraryCatalogTestActivity
        lateinit var previousModel: LibraryViewModel
        activity.onActivity {
            previousActivity = it
            previousModel = it.model
        }
        activity.recreate()
        compose.onNodeWithTag("library_loading").assertIsDisplayed()
        tab(selected).assertIsSelected()
        activity.onActivity {
            assertNotSame(previousActivity, it)
            assertSame("Configuration recreation retains the ViewModel", previousModel, it.model)
            assertEquals(selected, it.model.snapshot.value.state.selectedTab)
            assertEquals(LibraryBody.Loading, it.model.snapshot.value.state.selectedBody)
            assertEquals(SortDirection.Descending, it.model.snapshot.value.state.sortDirection)
            assertEquals(1, fixture.activeSources)
        }

        // The gate is shared by recollections, so RESUME cannot accidentally return data early.
        compose.runOnIdle { response.complete(Unit) }
        awaitItem(groupAnchor)
        assertBoundsRestored(groupBounds, groupAnchor)
        activity.onActivity {
            val query = fixture.lastQuery
            assertTrue(if (selected == LibraryTab.Albums) query is LibraryAlbumQuery else query is LibraryArtistQuery)
            assertEquals(SortDirection.Descending, when (query) {
                is LibraryAlbumQuery -> query.direction
                is LibraryArtistQuery -> query.direction
                else -> error("Expected the selected group query, got $query")
            })
        }

        // Also restore the inactive Songs state, then return to the group's independent position.
        select(LibraryTab.Songs)
        awaitItem(songAnchor)
        assertBoundsRestored(songBounds, songAnchor)
        select(selected)
        awaitItem(groupAnchor)
        assertBoundsRestored(groupBounds, groupAnchor)
    }

    private fun select(tab: LibraryTab) { tab(tab).performScrollTo().performClick() }
    private fun tab(tab: LibraryTab) = compose.onNodeWithText(context.getString(tab.tabLabel()))
    private fun groupTag(index: Int) = when (selected) {
        LibraryTab.Albums -> "library_album:album:$index"
        LibraryTab.Artists -> "library_artist:artist:$index"
        else -> error("Group tab required")
    }

    private fun awaitItem(tag: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun awaitReady(tab: LibraryTab) {
        compose.waitUntil(timeoutMillis = 5_000) {
            val state = model.snapshot.value.state
            state.selectedTab == tab && state.selectedBody is LibraryBody.Ready
        }
        compose.waitForIdle()
    }

    private fun scrollTo(tag: String): Rect {
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    }

    private fun assertBoundsRestored(expected: Rect, tag: String) {
        val actual = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertEquals("$tag left", expected.left, actual.left, 1f)
        assertEquals("$tag top", expected.top, actual.top, 1f)
        assertEquals("$tag right", expected.right, actual.right, 1f)
        assertEquals("$tag bottom", expected.bottom, actual.bottom, 1f)
    }

    private fun assertTwoColumns() {
        val first = compose.onNodeWithTag(groupTag(40)).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag(groupTag(39)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("Two albums must share a row", first.top, second.top, 1f)
        assertTrue("Albums must occupy separate columns", first.right < second.left)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "tab={0}")
        fun cases() = listOf(arrayOf(LibraryTab.Artists), arrayOf(LibraryTab.Albums))
    }
}

/** Test APK only: real owner/registry, controlled 412 dp viewport for repeatable grid geometry. */
class LibraryCatalogTestActivity : ComponentActivity() {
    internal lateinit var model: LibraryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val testFixture = checkNotNull(fixture)
        model = ViewModelProvider(this, viewModelFactory {
            initializer { LibraryViewModel(testFixture.repository, createSavedStateHandle(), "en", IdlePlaybackController) }
        })[LibraryViewModel::class.java]
        setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Fit 412 logical dp into the window; system font/density cannot reduce this to one column.
                CompositionLocalProvider(
                    LocalDensity provides Density(constraints.maxWidth / 412f, fontScale = 1f),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
                        LibraryRoot({}, artworkLoader = null, viewModel = model, accessHost = AndroidLibraryAccessHost { testFixture })
                    }
                }
            }
        }
    }

    companion object { internal var fixture: CatalogActivityFixture? = null }
}

/** Access is granted; only the first snapshot can be held, including across lifecycle recollection. */
internal class CatalogActivityFixture : AudioPermission {
    override val name = "android.permission.READ_MEDIA_AUDIO"
    override fun isGranted() = true
    override fun shouldExplain() = false
    private val gates = mutableMapOf<LibraryTab, CompletableDeferred<Unit>>()
    var activeSources = 0
        private set
    var lastQuery: Any? = null
        private set

    fun hold(tab: LibraryTab) = CompletableDeferred<Unit>().also { gates[tab] = it }

    val repository = object : LibraryCatalogRepository {
        override fun observeTracks(query: LibraryTrackQuery) = observe(LibraryTab.Songs, query, query.direction,
            (1..40).map { MediaTrack(TrackId("track:$it"), "Track $it", null, null, null) })
        override fun observeAlbums(query: LibraryAlbumQuery) = observe(LibraryTab.Albums, query, query.direction,
            (1..40).map { LibraryAlbum(AlbumId("album:$it"), "Album $it", AlbumArtistCredit.Named("Artist $it"), 3) })
        override fun observeArtists(query: LibraryArtistQuery) = observe(LibraryTab.Artists, query, query.direction,
            (1..40).map { LibraryArtist(ArtistId("artist:$it"), "Artist $it", 3, 1) })
    }

    private fun <T> observe(tab: LibraryTab, query: Any, direction: SortDirection, items: List<T>) =
        flow<AppResult<List<T>, DataError>> {
            activeSources++
            lastQuery = query
            try {
                gates[tab]?.await()
                emit(AppResult.Success(if (direction == SortDirection.Descending) items.asReversed() else items))
                awaitCancellation()
            } finally {
                activeSources--
            }
        }
}
