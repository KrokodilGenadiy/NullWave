package com.zaus.nullwave.feature.search.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.search.LibraryAlbumSearchHit
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults
import com.zaus.nullwave.feature.library.api.model.search.LibraryTrackSearchHit
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessUi
import com.zaus.nullwave.feature.search.impl.R
import com.zaus.nullwave.feature.search.presentation.SearchBody
import com.zaus.nullwave.feature.search.presentation.SearchPlaybackNotice
import com.zaus.nullwave.feature.search.presentation.SearchRefresh
import com.zaus.nullwave.feature.search.presentation.SearchState

private val previewResults = LibrarySearchResults(
    tracks = listOf(
        LibraryTrackSearchHit(MediaTrack(TrackId("preview:1"), "Static Bloom", "Static Pilgrim", "Nullwave EP", 242_000), "static_bloom.flac"),
        LibraryTrackSearchHit(MediaTrack(TrackId("preview:2"), "Сигнал из очень далёкого города", "Статика", "Ночная орбита", 221_000), "Статика — Сигнал.flac"),
    ),
    albums = listOf(LibraryAlbumSearchHit(LibraryAlbum(AlbumId("preview:album"), "Nullwave EP", AlbumArtistCredit.Various, 12), listOf("Static Pilgrim"))),
    artists = listOf(LibraryArtist(ArtistId("preview:artist"), "Static Pilgrim", 41, 3)),
)

internal class SearchPreviewStates : PreviewParameterProvider<SearchState> {
    override val values = sequenceOf(
        SearchState("en"),
        SearchState("en", "stat", body = SearchBody.Loading),
        SearchState("en", "stat", body = SearchBody.AccessRequired),
        SearchState("en", "stat", body = SearchBody.Failed(UiText.Resource(R.string.search_load_failed))),
        SearchState("en", "stat", body = SearchBody.Ready(previewResults)),
        SearchState("en", "zzz", body = SearchBody.Ready(LibrarySearchResults(emptyList(), emptyList(), emptyList()))),
        SearchState("en", "stat", body = SearchBody.Ready(previewResults, SearchRefresh.Failed(UiText.Resource(R.string.search_refresh_failed)))),
    )
}

@Preview(name = "Search states — EN", widthDp = 360, heightDp = 800, locale = "en")
@Preview(name = "Search states — RU", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun SearchPreview(@PreviewParameter(SearchPreviewStates::class) state: SearchState) {
    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
        SearchScreen(state, {}, {}, LibraryAccessUi(access = LibraryAccess.Required(AccessAction.SettingsSuggested)))
    }
}

@Preview(name = "Search — large text", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun SearchLargeTextPreview() {
    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
        SearchScreen(SearchState("ru", "стат", body = SearchBody.Ready(previewResults)), {}, {})
    }
}

@Preview(name = "Search — playback error — EN", widthDp = 360, heightDp = 800, locale = "en")
@Preview(name = "Search — playback error — RU", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun SearchPlaybackErrorPreview() {
    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
        SearchScreen(
            SearchState("en", "stat", body = SearchBody.Ready(previewResults),
                playbackNotice = SearchPlaybackNotice(UiText.Resource(R.string.search_playback_not_found))),
            {}, {},
        )
    }
}
