package com.zaus.nullwave.feature.library.ui.artistdetails

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistDetails
import com.zaus.nullwave.feature.library.impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.zaus.nullwave.core.designsystem.R as DesignR

class ArtistDetailsContentInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val events = mutableListOf<String>()
    private val details = mutableStateOf(artistDetails())
    private val playbackEnabled = mutableStateOf(true)
    private val albumCallback = mutableStateOf<(AlbumId) -> Unit>({ events += "album:${it.value}" })

    @Test
    fun rendersArtistSectionsAndForwardsEachCallbackWithItsIdentity() {
        show()
        compose.onNode(hasText("Static Pilgrim") and isHeading()).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_artist_details_albums)).assertIsDisplayed()
        // The compilation card names the whole album and its full track count, not this artist's share.
        album(AlbumId("primary:40")).assertIsDisplayed()
            .assert(hasText("Night Drive"))
            .assert(hasText(text(R.string.library_various_artists)))
            .assert(hasText(resources.getQuantityString(R.plurals.library_track_count, 14, 14)))

        compose.onNodeWithTag(ArtistDetailsTags.Back).assertHasClickAction().performClick()
        album(AlbumId("primary:40")).performClick()
        compose.onNodeWithTag(ArtistDetailsTags.Play).assertIsEnabled().performClick()
        compose.onNodeWithTag(ArtistDetailsTags.Shuffle).assertIsEnabled().performClick()
        track(TrackId("primary:601")).performScrollTo()
        trackAction(TrackId("primary:601")).assertHasClickAction().performClick()

        assertEquals(listOf("back", "album:primary:40", "play", "shuffle", "track:primary:601"), events)
    }

    @Test
    fun sameTitledAlbumsKeepSeparateCardsAndReportTheirOwnIds() {
        val first = LibraryAlbum(AlbumId("external_primary:7"), "Signals", AlbumArtistCredit.Named("Static Pilgrim"), 1)
        val second = first.copy(id = AlbumId("card:7"))
        show(LibraryArtistDetails.create(
            LibraryArtist(ArtistId("primary:5"), "Static Pilgrim", trackCount = 2, albumCount = 2),
            listOf(first, second),
            listOf(trackOf("primary:1", "Signals", "Signals"), trackOf("card:1", "Signals", "Signals")),
        ))
        compose.onAllNodesWithText("Signals").fetchSemanticsNodes().let { assertTrue(it.size >= 2) }
        album(second.id).performClick()
        album(first.id).performClick()
        assertEquals(listOf("album:card:7", "album:external_primary:7"), events)
    }

    @Test
    fun artistWithoutAlbumsKeepsHeaderAndTracksWithoutAShelf() {
        show(LibraryArtistDetails.create(
            LibraryArtist(ArtistId("primary:6"), null, trackCount = 2, albumCount = 0),
            emptyList(),
            listOf(trackOf("primary:500", "voice_memo_01", null), trackOf("primary:501", "voice_memo_02", null)),
        ))
        compose.onNodeWithText(text(R.string.library_unknown_artist)).assertIsDisplayed()
        compose.onNodeWithTag(ArtistDetailsTags.AlbumShelf).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.library_artist_details_albums)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.library_artist_details_tracks)).assertIsDisplayed()
        track(TrackId("primary:500")).assertIsDisplayed()
        track(TrackId("primary:501")).assertIsDisplayed()
    }

    @Test
    fun suppliedAlbumAndTrackOrderIsNotResorted() {
        show()
        val albums = listOf("primary:40", "primary:10", "primary:30").map { bounds(album(AlbumId(it))).left }
        assertTrue("Albums must keep the supplied order: $albums", albums.zipWithNext().all { (a, b) -> a < b })
        val tracks = listOf("primary:601", "primary:600", "primary:602").map { bounds(track(TrackId(it))).top }
        assertTrue("Tracks must keep the supplied order: $tracks", tracks.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun scrolledRowsStayBelowTheBackToolbar() {
        val tracks = (1..60).map { trackOf("primary:$it", "Track $it", null) }
        show(LibraryArtistDetails.create(
            LibraryArtist(ArtistId("primary:5"), "Static Pilgrim", 60, 0), emptyList(), tracks))
        compose.onNodeWithTag(ArtistDetailsTags.List).performScrollToNode(hasTestTag(ArtistDetailsTags.track(TrackId("primary:40"))))
        val list = bounds(compose.onNodeWithTag(ArtistDetailsTags.List))
        val back = bounds(compose.onNodeWithTag(ArtistDetailsTags.Back))
        assertTrue("The scrolling viewport overlaps Back", list.top >= back.bottom)
    }

    @Test
    fun longTrackListReachesItsLastTrack() {
        val tracks = (1..80).map { trackOf("primary:$it", "Track $it", "Album") }
        show(LibraryArtistDetails.create(
            LibraryArtist(ArtistId("primary:5"), "Static Pilgrim", trackCount = 80, albumCount = 0), emptyList(), tracks))
        compose.onNodeWithTag(ArtistDetailsTags.List).performScrollToNode(hasTestTag(ArtistDetailsTags.track(TrackId("primary:80"))))
        trackAction(TrackId("primary:80")).assertIsDisplayed().performClick()
        assertEquals(listOf("track:primary:80"), events)
    }

    @Test
    fun compactWindowWithLargeRtlTextKeepsLongNamesAndEveryActionReachable() {
        val longArtist = "東京ナイトドライブ・シンフォニック・オーケストラと非常に長い名前の仲間たち"
        val longAlbum = "Очень длинное название сборника, который никак не помещается в карточку"
        val longTrack = "Невероятно длинное название трека на дальней орбите заброшенной станции"
        show(
            LibraryArtistDetails.create(
                LibraryArtist(ArtistId("primary:5"), longArtist, trackCount = 30, albumCount = 2),
                listOf(
                    LibraryAlbum(AlbumId("primary:1"), longAlbum, AlbumArtistCredit.Various, 30),
                    LibraryAlbum(AlbumId("primary:2"), "夜の都市", AlbumArtistCredit.Named(longArtist), 4),
                ),
                (1..30).map { trackOf("primary:$it", "$longTrack $it", longAlbum) },
            ),
            fontScale = 2f, direction = LayoutDirection.Rtl, compact = true,
        )
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag(ArtistDetailsTags.Back).assertIsDisplayed().assertHasClickAction()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        // Visual ellipsis or wrapping never removes the full names from accessibility.
        compose.onNodeWithText(longArtist).assertExists()
        val play = compose.onNodeWithTag(ArtistDetailsTags.Play).performScrollTo().assertIsDisplayed()
        val shuffle = compose.onNodeWithTag(ArtistDetailsTags.Shuffle).performScrollTo().assertIsDisplayed()
        val playBounds = bounds(play)
        val shuffleBounds = bounds(shuffle)
        assertTrue("Play and Shuffle overlap", !playBounds.overlaps(shuffleBounds))
        listOf(playBounds, shuffleBounds).forEach { assertTrue("Button leaves the window: $it", it.left >= root.left && it.right <= root.right) }
        play.performClick()

        compose.onNodeWithTag(ArtistDetailsTags.List).performScrollToNode(hasTestTag(ArtistDetailsTags.AlbumShelf))
        compose.onNodeWithTag(ArtistDetailsTags.AlbumShelf)
            .performScrollToNode(hasTestTag(ArtistDetailsTags.album(AlbumId("primary:2"))))
        val card = album(AlbumId("primary:2")).assertIsDisplayed().assertHasClickAction()
        assertTrue("Album card is wider than the window", bounds(card).width <= root.width)
        compose.onNodeWithText(longAlbum, substring = true).assertExists()
        card.performClick()

        compose.onNodeWithTag(ArtistDetailsTags.List).performScrollToNode(hasTestTag(ArtistDetailsTags.track(TrackId("primary:30"))))
        track(TrackId("primary:30")).assertIsDisplayed().performClick()
        assertEquals(listOf("play", "album:primary:2", "track:primary:30"), events)
    }

    @Test
    fun disabledPlaybackForwardsNoPlaybackCallbackAndNeverMarksTracksUnavailable() {
        playbackEnabled.value = false
        show()
        compose.onNodeWithTag(ArtistDetailsTags.Play).assertIsNotEnabled().performClick()
        compose.onNodeWithTag(ArtistDetailsTags.Shuffle).assertIsNotEnabled().performClick()
        track(TrackId("primary:600")).performScrollTo().assertIsDisplayed()
        assertNoTrackAction(TrackId("primary:600"))
        track(TrackId("primary:600")).performClick()
        compose.onAllNodesWithText(resources.getString(DesignR.string.nw_track_unavailable), substring = true, ignoreCase = true)
            .fetchSemanticsNodes().let { assertEquals(0, it.size) }
        assertEquals(emptyList<String>(), events)

        // Navigation stays independent of playback availability.
        compose.onNodeWithTag(ArtistDetailsTags.Back).performClick()
        album(AlbumId("primary:10")).performScrollTo().performClick()
        assertEquals(listOf("back", "album:primary:10"), events)
    }

    @Test
    fun callbacksAndCardsFollowReplacedDetailsAndLambdas() {
        show()
        album(AlbumId("primary:40")).performClick()
        compose.runOnIdle {
            details.value = LibraryArtistDetails.create(
                LibraryArtist(ArtistId("primary:7"), "Guest", trackCount = 1, albumCount = 1),
                listOf(LibraryAlbum(AlbumId("card:99"), "Night Drive", AlbumArtistCredit.Various, 12)),
                listOf(trackOf("card:900", "Lights", "Night Drive")),
            )
            albumCallback.value = { events += "replaced:${it.value}" }
            playbackEnabled.value = false
        }
        album(AlbumId("primary:40")).assertDoesNotExist()
        album(AlbumId("card:99")).performClick()
        assertNoTrackAction(TrackId("card:900"))
        compose.runOnIdle { playbackEnabled.value = true }
        trackAction(TrackId("card:900")).assertHasClickAction().performClick()
        assertEquals(listOf("album:primary:40", "replaced:card:99", "track:card:900"), events)
    }

    @Test
    fun listAndShelfScrollSurviveRendererRecreation() {
        val albums = (1..12).map { LibraryAlbum(AlbumId("primary:a$it"), "Album $it", AlbumArtistCredit.Named("Static Pilgrim"), 1) }
        val tracks = (1..40).map { trackOf("primary:$it", "Track $it", "Album") }
        val restore = show(LibraryArtistDetails.create(
            LibraryArtist(ArtistId("primary:5"), "Static Pilgrim", trackCount = 40, albumCount = 12), albums, tracks))
        val shelf = compose.onNodeWithTag(ArtistDetailsTags.AlbumShelf)
        val lastAlbum = ArtistDetailsTags.album(AlbumId("primary:a12"))
        shelf.performScrollToNode(hasTestTag(lastAlbum))
        val albumBounds = bounds(compose.onNodeWithTag(lastAlbum))
        val trackTag = ArtistDetailsTags.track(TrackId("primary:30"))
        compose.onNodeWithTag(ArtistDetailsTags.List).performScrollToNode(hasTestTag(trackTag))
        val trackBounds = bounds(compose.onNodeWithTag(trackTag))

        restore.emulateSavedInstanceStateRestore()

        assertEquals(trackBounds, bounds(compose.onNodeWithTag(trackTag)))
        compose.onNodeWithTag(ArtistDetailsTags.List).performScrollToNode(hasTestTag(ArtistDetailsTags.AlbumShelf))
        compose.onNodeWithTag(lastAlbum).assertIsDisplayed()
        assertEquals(albumBounds.left, bounds(compose.onNodeWithTag(lastAlbum)).left)
    }

    private fun show(
        initial: LibraryArtistDetails = artistDetails(),
        fontScale: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr,
        compact: Boolean = false,
    ): StateRestorationTester {
        details.value = initial
        return StateRestorationTester(compose).also { restore ->
            restore.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    LocalLayoutDirection provides direction,
                ) {
                    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
                        Box(if (compact) Modifier.size(320.dp, 480.dp) else Modifier) {
                            ArtistDetailsContent(
                                details = details.value,
                                artworkLoader = null,
                                onBack = { events += "back" },
                                onAlbumClick = albumCallback.value,
                                onPlay = { events += "play" },
                                onShuffle = { events += "shuffle" },
                                onTrackClick = { events += "track:${it.value}" },
                                playbackEnabled = playbackEnabled.value,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun album(id: AlbumId) = compose.onNodeWithTag(ArtistDetailsTags.album(id))
    private fun track(id: TrackId) = compose.onNodeWithTag(ArtistDetailsTags.track(id))

    /** The DS row puts its click action on an inner node; the tag marks the outer container. */
    private fun trackActionMatcher(id: TrackId) = hasClickAction() and
        (hasTestTag(ArtistDetailsTags.track(id)) or hasAnyAncestor(hasTestTag(ArtistDetailsTags.track(id))))
    private fun trackAction(id: TrackId) = compose.onNode(trackActionMatcher(id))
    private fun assertNoTrackAction(id: TrackId) {
        track(id).assertExists()
        compose.onAllNodes(trackActionMatcher(id)).assertCountEquals(0)
    }
    private fun bounds(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot
    private fun text(id: Int) = resources.getString(id)

    private fun trackOf(id: String, title: String, album: String?) =
        MediaTrack(TrackId(id), title, "Static Pilgrim", album, 200_000)

    /** Supplied order is deliberately not alphabetical; the compilation counts the whole album. */
    private fun artistDetails() = LibraryArtistDetails.create(
        LibraryArtist(ArtistId("primary:5"), "Static Pilgrim", trackCount = 3, albumCount = 3),
        listOf(
            LibraryAlbum(AlbumId("primary:40"), "Night Drive", AlbumArtistCredit.Various, trackCount = 14),
            LibraryAlbum(AlbumId("primary:10"), "Afterglow", AlbumArtistCredit.Named("Static Pilgrim"), trackCount = 1),
            LibraryAlbum(AlbumId("primary:30"), "Carbon Sleep", AlbumArtistCredit.Named("Static Pilgrim"), trackCount = 1),
        ),
        listOf(
            trackOf("primary:601", "Tunnel", "Night Drive"),
            trackOf("primary:600", "Lights", "Afterglow"),
            trackOf("primary:602", "Zero", "Carbon Sleep"),
        ),
    )
}
