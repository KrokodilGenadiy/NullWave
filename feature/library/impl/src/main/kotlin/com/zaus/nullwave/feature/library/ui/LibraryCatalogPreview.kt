package com.zaus.nullwave.feature.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.presentation.LibraryBody
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.library.presentation.LibraryState
import com.zaus.nullwave.feature.library.presentation.LibraryTab
import com.zaus.nullwave.feature.library.presentation.RefreshState

private val sampleAlbums = listOf(
    LibraryAlbum(AlbumId("sample:1"), "Nullwave EP", AlbumArtistCredit.Named("Static Pilgrim"), 7),
    LibraryAlbum(AlbumId("sample:2"), "Ночной город", AlbumArtistCredit.Named("Сигнал"), 12),
    LibraryAlbum(AlbumId("sample:3"), "Off-Grid / Compilation", AlbumArtistCredit.Various, 21),
    LibraryAlbum(AlbumId("sample:4"), null, AlbumArtistCredit.Unknown, 1),
)
private val sampleArtists = listOf(
    LibraryArtist(ArtistId("sample:1"), "Static Pilgrim", 32, 4),
    LibraryArtist(ArtistId("sample:2"), "Очень длинное имя исполнителя на дальней орбите", 12, 1),
    LibraryArtist(ArtistId("sample:3"), "Mire & Halo", 21, 2),
    LibraryArtist(ArtistId("sample:4"), null, 3, 0),
)

private fun sampleCatalog(tab: LibraryTab) = LibraryState(
    query = LibraryTrackQuery("en"),
    selectedTab = tab,
    body = LibraryBody.Ready(listOf(MediaTrack(
        TrackId("sample:1"), "Deadzone Chorus", "Static Pilgrim", "Nullwave EP", 221_000,
    ))),
    albums = LibraryBody.Ready(sampleAlbums),
    artists = LibraryBody.Ready(sampleArtists),
)

internal class LibraryCatalogPreviewStates : PreviewParameterProvider<LibraryState> {
    override val values = sequenceOf(
        sampleCatalog(LibraryTab.Albums),
        sampleCatalog(LibraryTab.Artists),
        sampleCatalog(LibraryTab.Albums).copy(albums = LibraryBody.Ready(emptyList())),
        sampleCatalog(LibraryTab.Artists).copy(artists = LibraryBody.Failed(UiText.Resource(R.string.library_load_failed))),
        sampleCatalog(LibraryTab.Albums).copy(albums = LibraryBody.Ready(sampleAlbums, RefreshState.Running)),
    )
}

@Preview(name = "Catalog / EN", widthDp = 412, heightDp = 820, locale = "en")
@Preview(name = "Catalog / RU", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun LibraryCatalogPreview(@PreviewParameter(LibraryCatalogPreviewStates::class) state: LibraryState) {
    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
        LibraryScreen(state, {}, previewMenuButton)
    }
}

@Preview(name = "Albums / narrow large text", widthDp = 320, heightDp = 800, fontScale = 2f, locale = "ru")
@Preview(name = "Albums / tablet", widthDp = 840, heightDp = 900, locale = "en")
@Composable
private fun LibraryAlbumsAdaptivePreview() {
    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
        LibraryScreen(sampleCatalog(LibraryTab.Albums), {}, previewMenuButton)
    }
}

/** UI-only sample: switch tabs and inspect their filters without a ViewModel or device queries. */
@Preview(name = "Catalog / interactive tabs and filters", widthDp = 412, heightDp = 820, locale = "en")
@Composable
private fun LibraryCatalogInteractivePreview() {
    var state by remember { mutableStateOf(sampleCatalog(LibraryTab.Songs)) }
    NullWaveTheme {
        LibraryScreen(state, onIntent = { intent ->
            state = when (intent) {
                is LibraryIntent.TabSelected -> state.copy(selectedTab = intent.tab)
                is LibraryIntent.SortChanged -> state.copy(query = state.query.copy(sort = intent.sort, direction = intent.direction))
                is LibraryIntent.AlbumSortChanged -> state.copy(
                    albumQuery = state.albumQuery.copy(direction = intent.direction),
                    albums = LibraryBody.Ready(sampleAlbums.sortedBy { it.title.orEmpty() }
                        .let { if (intent.direction == SortDirection.Descending) it.reversed() else it }),
                )
                is LibraryIntent.ArtistSortChanged -> state.copy(
                    artistQuery = state.artistQuery.copy(direction = intent.direction),
                    artists = LibraryBody.Ready(sampleArtists.sortedBy { it.name.orEmpty() }
                        .let { if (intent.direction == SortDirection.Descending) it.reversed() else it }),
                )
                else -> state
            }
        }, menuButton = previewMenuButton)
    }
}
