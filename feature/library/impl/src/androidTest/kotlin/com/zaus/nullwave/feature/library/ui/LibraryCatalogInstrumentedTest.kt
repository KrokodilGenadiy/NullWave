package com.zaus.nullwave.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort
import com.zaus.nullwave.feature.library.api.model.track.TrackId
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.presentation.LibraryBody
import com.zaus.nullwave.feature.library.presentation.AccessAction
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.library.presentation.LibraryState
import com.zaus.nullwave.feature.library.presentation.LibraryTab
import com.zaus.nullwave.feature.library.presentation.RefreshState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** State renderer checks; the Root test separately exercises real VM/source switching. */
class LibraryCatalogInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(catalog())
    private val intents = mutableListOf<LibraryIntent>()
    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun tabsFollowTheDesignOrderAndDispatchTypedSelections() {
        show()
        val songs = tab(LibraryTab.Songs).getUnclippedBoundsInRoot()
        val artists = tab(LibraryTab.Artists).getUnclippedBoundsInRoot()
        val albums = tab(LibraryTab.Albums).getUnclippedBoundsInRoot()
        assertTrue(songs.left < artists.left && artists.left < albums.left)
        tab(LibraryTab.Songs).assertIsSelected()
        for (selected in listOf(LibraryTab.Artists, LibraryTab.Albums, LibraryTab.Songs)) {
            tab(selected).assertHeightIsAtLeast(48.dp).performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(LibraryIntent.TabSelected(selected), intents.last()) }
        }
    }

    @Test
    fun albumsShowUnknownAndCompilationCreditsWithoutFakeClickActions() {
        show(catalog(LibraryTab.Albums).copy(albums = LibraryBody.Ready(listOf(
            album(1).copy(title = null, artistCredit = AlbumArtistCredit.Unknown),
            album(2).copy(artistCredit = AlbumArtistCredit.Various),
        ))))
        compose.onNodeWithTag("library_album:album:1")
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assertTextContains(text(R.string.library_unknown_album))
            .assertTextContains(text(R.string.library_unknown_artist))
        compose.onNodeWithTag("library_summary").assertTextContains(resources.getQuantityString(R.plurals.library_album_count, 2, 2))
        compose.onNodeWithTag("library_track:track:1").assertDoesNotExist()
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("library_album:album:2"))
        compose.onNodeWithTag("library_album:album:2").assertTextContains(text(R.string.library_various_artists))
    }

    @Test
    fun artistRowsHaveTheirOwnCountsAndAccessibleNames() {
        show(catalog(LibraryTab.Artists).copy(artists = LibraryBody.Ready(listOf(
            LibraryArtist(ArtistId("unknown"), null, trackCount = 3, albumCount = 0),
        ))))
        compose.onNodeWithTag("library_artist:unknown").assertHasNoClickAction()
            .assertTextContains(text(R.string.library_unknown_artist))
            .assertTextContains(resources.getQuantityString(R.plurals.library_album_count, 0, 0), substring = true)
            .assertTextContains(resources.getQuantityString(R.plurals.library_track_count, 3, 3), substring = true)
        compose.onNodeWithTag("library_summary").assertTextContains(resources.getQuantityString(R.plurals.library_artist_count, 1, 1))
    }

    @Test
    fun groupFiltersChangeOnlyTheirDirectionAndKeepSongsSort() {
        show(catalog().let { it.copy(query = it.query.copy(sort = LibraryTrackSort.RecentlyAdded)) })
        for (selected in listOf(LibraryTab.Albums, LibraryTab.Artists)) {
            tab(selected).performScrollTo().performClick()
            compose.onNodeWithTag("library_filters").performClick()
            compose.onNodeWithText(text(R.string.library_sort_added)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.library_descending)).performClick()
            compose.onNodeWithText(text(R.string.library_descending)).assertIsSelected()
            compose.runOnIdle {
                assertEquals(if (selected == LibraryTab.Albums) LibraryIntent.AlbumSortChanged(SortDirection.Descending)
                    else LibraryIntent.ArtistSortChanged(SortDirection.Descending), intents.last())
                assertEquals(LibraryTrackSort.RecentlyAdded, state.value.query.sort)
                assertEquals(SortDirection.Ascending, state.value.query.direction)
            }
            compose.onNodeWithText(text(R.string.library_done)).performClick()
            compose.onNodeWithTag("library_filters").performClick()
            compose.onNodeWithText(text(R.string.library_descending)).assertIsSelected()
            compose.onNodeWithText(text(R.string.library_done)).performClick()
        }
    }

    @Test
    fun filtersStayOpenDuringLoadingAndRestorationButCloseOnAccessLoss() {
        val restore = show()
        for (selected in LibraryTab.entries) {
            val ready = catalog(selected)
            update { ready }
            compose.onNodeWithTag("library_filters").performClick()
            update { it.copy(body = LibraryBody.Loading, albums = LibraryBody.Loading, artists = LibraryBody.Loading) }
            compose.onNodeWithText(text(R.string.library_done)).assertIsDisplayed()
            restore.emulateSavedInstanceStateRestore()
            compose.onNodeWithText(text(R.string.library_done)).assertIsDisplayed()

            val denied = LibraryBody.AccessRequired(AccessAction.Request)
            update { it.copy(body = denied, albums = denied, artists = denied) }
            compose.onNodeWithText(text(R.string.library_done)).assertDoesNotExist()
            compose.onNodeWithTag("library_access_action").assertIsDisplayed()
            restore.emulateSavedInstanceStateRestore()
            compose.onNodeWithText(text(R.string.library_done)).assertDoesNotExist()
            update { ready }
            compose.onNodeWithText(text(R.string.library_done)).assertDoesNotExist()
            compose.onNodeWithTag("library_filters").performClick()
            compose.onNodeWithText(text(R.string.library_done)).performClick()
        }
    }

    @Test
    fun selectedBodyControlsLoadingErrorsAndGroupEmptyState() {
        show(catalog(LibraryTab.Albums).copy(albums = LibraryBody.Loading))
        compose.onNodeWithTag("library_loading").assertIsDisplayed()
        compose.onNodeWithTag("library_track:track:1").assertDoesNotExist()
        update { it.copy(albums = LibraryBody.Failed(UiText.Resource(R.string.library_load_failed))) }
        compose.onNodeWithText(text(R.string.library_retry)).performClick()
        compose.runOnIdle { assertEquals(LibraryIntent.Retry, intents.last()) }
        update { it.copy(albums = LibraryBody.Ready(emptyList())) }
        compose.onNodeWithText(text(R.string.library_albums_empty_title)).assertIsDisplayed()
        update { it.copy(selectedTab = LibraryTab.Artists, artists = LibraryBody.Ready(emptyList())) }
        compose.onNodeWithText(text(R.string.library_artists_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_empty_title)).assertDoesNotExist()
    }

    @Test
    fun albumRefreshKeepsTheVisibleCardInPlaceAndFailureOffersRetry() {
        val initial = catalog(LibraryTab.Albums)
        show(initial)
        val tag = "library_album:album:25"
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag(tag))
        val before = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        update { it.copy(albums = LibraryBody.Ready((initial.albums as LibraryBody.Ready).items, RefreshState.Running)) }
        compose.onNodeWithTag("library_refreshing").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
        update { initial }
        assertEquals(before, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("library_filters"))
        update { it.copy(albums = LibraryBody.Ready((initial.albums as LibraryBody.Ready).items,
            RefreshState.Failed(UiText.Resource(R.string.library_refresh_failed)))) }
        compose.onNodeWithTag("library_refresh_error").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_retry)).performClick()
        compose.runOnIdle { assertEquals(LibraryIntent.Retry, intents.last()) }
    }

    @Test
    fun eachTabRetainsScrollThroughLoadingAndCompositionRestoration() {
        val restore = show()
        val trackTag = "library_track:track:25"
        val albumTag = "library_album:album:25"
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag(trackTag))
        val trackBounds = compose.onNodeWithTag(trackTag).fetchSemanticsNode().boundsInRoot
        tab(LibraryTab.Albums).performScrollTo().performClick()
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag(albumTag))
        val albumBounds = compose.onNodeWithTag(albumTag).fetchSemanticsNode().boundsInRoot
        update { it.copy(body = LibraryBody.Loading, albums = LibraryBody.Loading, selectedTab = LibraryTab.Songs) }
        compose.onNodeWithTag("library_loading").assertIsDisplayed()
        update { catalog() }
        assertEquals(trackBounds, compose.onNodeWithTag(trackTag).fetchSemanticsNode().boundsInRoot)
        tab(LibraryTab.Albums).performScrollTo().performClick()
        assertEquals(albumBounds, compose.onNodeWithTag(albumTag).fetchSemanticsNode().boundsInRoot)
        restore.emulateSavedInstanceStateRestore()
        tab(LibraryTab.Albums).assertIsSelected()
        assertEquals(albumBounds, compose.onNodeWithTag(albumTag).fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun largeTextRtlGridUsesOneColumnAndCanReachTheLastAlbum() {
        show(catalog(LibraryTab.Albums), fontScale = 2f, direction = LayoutDirection.Rtl)
        compose.onNodeWithTag("library_album:album:1").assertWidthIsAtLeast(250.dp)
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("library_album:album:40"))
        compose.onNodeWithTag("library_album:album:40").assertIsDisplayed().assertHasNoClickAction()
    }

    private fun show(initial: LibraryState = catalog(), fontScale: Float = 1f,
                     direction: LayoutDirection = LayoutDirection.Ltr): StateRestorationTester {
        state.value = initial
        return StateRestorationTester(compose).also { restore ->
            restore.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    LocalLayoutDirection provides direction) {
                    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
                        Box(Modifier.width(320.dp)) {
                            LibraryScreen(state.value, onIntent = { intent ->
                                intents += intent
                                state.value = when (intent) {
                                    is LibraryIntent.TabSelected -> state.value.copy(selectedTab = intent.tab)
                                    is LibraryIntent.AlbumSortChanged -> state.value.copy(albumQuery = state.value.albumQuery.copy(direction = intent.direction))
                                    is LibraryIntent.ArtistSortChanged -> state.value.copy(artistQuery = state.value.artistQuery.copy(direction = intent.direction))
                                    else -> state.value
                                }
                            }, onAboutClick = {})
                        }
                    }
                }
            }
        }
    }
    private fun tab(tab: LibraryTab) = compose.onNodeWithText(text(tab.tabLabel()))
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    private fun text(id: Int) = resources.getString(id)
    private fun update(transform: (LibraryState) -> LibraryState) = compose.runOnIdle { state.value = transform(state.value) }

    private fun album(index: Int) = LibraryAlbum(AlbumId("album:$index"), "Album $index", AlbumArtistCredit.Named("Artist $index"), 3)
    private fun catalog(selected: LibraryTab = LibraryTab.Songs) = LibraryState(
        query = LibraryTrackQuery("en"), selectedTab = selected,
        body = LibraryBody.Ready((1..40).map { LibraryTrack(TrackId("track:$it"), "Track $it", null, null, null) }),
        albums = LibraryBody.Ready((1..40).map(::album)),
        artists = LibraryBody.Ready((1..40).map { LibraryArtist(ArtistId("artist:$it"), "Artist $it", 3, 1) }),
    )
}
