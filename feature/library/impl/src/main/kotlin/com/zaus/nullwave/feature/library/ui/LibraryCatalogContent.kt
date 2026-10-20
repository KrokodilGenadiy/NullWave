package com.zaus.nullwave.feature.library.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.content.NullWaveMediaGridCard
import com.zaus.nullwave.core.designsystem.components.content.NullWaveMediaListItem
import com.zaus.nullwave.core.designsystem.components.content.NullWaveTrackRow
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveEmptyState
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveInlineMessage
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageAction
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageVariant
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.asString
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.presentation.LibraryBody
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.library.presentation.LibraryState
import com.zaus.nullwave.feature.library.presentation.LibraryTab
import com.zaus.nullwave.feature.library.presentation.RefreshState

/** One ready catalog at a time. Its scroll state is owned above loading/error composition. */
@Composable
internal fun LibraryReadyContent(
    state: LibraryState,
    onIntent: (LibraryIntent) -> Unit,
    onFilters: () -> Unit,
    artworkLoader: LibraryArtworkLoader?,
    insets: PaddingValues,
    listState: LazyGridState,
) {
    val ready = state.selectedBody as? LibraryBody.Ready<*> ?: return
    val spacing = NullWaveTheme.spacing
    val isGrid = state.selectedTab == LibraryTab.Albums
    val gutter = NullWaveTheme.dimens.gutter
    val horizontalPadding = if (isGrid) gutter else 0.dp
    LazyVerticalGrid(
        columns = if (isGrid) GridCells.Adaptive(AlbumMinWidth * LocalDensity.current.fontScale.coerceAtLeast(1f))
            else GridCells.Fixed(1),
        state = listState,
        modifier = Modifier.fillMaxSize().consumeWindowInsets(insets).testTag("library_list"),
        contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding,
            top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(if (isGrid) spacing.md else 0.dp),
    ) {
        item("summary", span = { GridItemSpan(maxLineSpan) }, contentType = "summary") {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = if (isGrid) 0.dp else gutter, vertical = gutter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(librarySummary(state, ready.items.size),
                    Modifier.align(Alignment.CenterVertically).testTag("library_summary"),
                    style = NullWaveTheme.typography.micro, color = NullWaveTheme.colors.textSecondary)
                LibraryFiltersButton(state, onClick = onFilters)
            }
        }
        val refresh = ready.refresh
        if (refresh is RefreshState.Failed) item("refresh_error", span = { GridItemSpan(maxLineSpan) }) {
            NullWaveInlineMessage(refresh.message.asString(),
                Modifier.padding(spacing.md).testTag("library_refresh_error")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                variant = NullWaveMessageVariant.Error,
                action = NullWaveMessageAction(stringResource(R.string.library_retry), { onIntent(LibraryIntent.Retry) }))
        }
        if (ready.items.isEmpty()) item("empty", span = { GridItemSpan(maxLineSpan) }) {
            LibraryEmptyCatalog(state.selectedTab)
        }
        when (state.selectedTab) {
            LibraryTab.Songs -> {
                val songs = state.body
                if (songs is LibraryBody.Ready) items(songs.items,
                    key = { "track:${it.id.value}" }, contentType = { "track" }) { track ->
                    Column {
                        NullWaveTrackRow(
                            title = track.title,
                            subtitle = librarySubtitle(track.artist, track.album, stringResource(R.string.library_unknown_artist)),
                            durationLabel = libraryDuration(track.durationMs),
                            modifier = Modifier.testTag("library_track:${track.id.value}"),
                            artwork = { LibraryArtwork(track, artworkLoader) },
                        )
                        NullWaveDivider(Modifier.padding(horizontal = gutter))
                    }
                }
            }
            LibraryTab.Albums -> {
                val albums = state.albums
                if (albums is LibraryBody.Ready) items(albums.items,
                    key = { "album:${it.id.value}" }, contentType = { "album" }) { album ->
                    LibraryAlbumCard(album, artworkLoader)
                }
            }
            LibraryTab.Artists -> {
                val artists = state.artists
                if (artists is LibraryBody.Ready) items(artists.items,
                    key = { "artist:${it.id.value}" }, contentType = { "artist" }) { artist ->
                    LibraryArtistRow(artist)
                }
            }
        }
    }
}

@Composable
private fun librarySummary(state: LibraryState, count: Int): String = when (state.selectedTab) {
    LibraryTab.Songs -> {
        val songs = state.body as? LibraryBody.Ready
        val bytes = remember(songs?.items) { songs?.items?.let(::libraryTotalBytes) }
        val size = bytes?.let { Formatter.formatShortFileSize(LocalContext.current, it) }
            ?: stringResource(R.string.library_size_unknown)
        stringResource(R.string.library_summary, pluralStringResource(R.plurals.library_track_count, count, count), size)
    }
    LibraryTab.Albums -> pluralStringResource(R.plurals.library_album_count, count, count)
    LibraryTab.Artists -> pluralStringResource(R.plurals.library_artist_count, count, count)
}

@Composable
private fun LibraryAlbumCard(album: LibraryAlbum, loader: LibraryArtworkLoader?) {
    val title = album.title ?: stringResource(R.string.library_unknown_album)
    val credit = libraryAlbumCredit(album.artistCredit, stringResource(R.string.library_unknown_artist),
        stringResource(R.string.library_various_artists))
    NullWaveMediaGridCard(
        title = title,
        supportingText = credit,
        modifier = Modifier.testTag("library_album:${album.id.value}"),
        artwork = {
            BoxWithConstraints {
                LibraryArtwork(album.artwork, title, loader, size = maxWidth)
            }
        },
        badge = {
            Text(pluralStringResource(R.plurals.library_track_count, album.trackCount, album.trackCount),
                style = NullWaveTheme.typography.micro, color = NullWaveTheme.colors.textSecondary)
        },
    )
}

@Composable
private fun LibraryArtistRow(artist: LibraryArtist) {
    val name = artist.name ?: stringResource(R.string.library_unknown_artist)
    val monogram = remember(name) { libraryMonogram(name) }
    NullWaveMediaListItem(
        title = name,
        modifier = Modifier.padding(horizontal = NullWaveTheme.dimens.gutter, vertical = NullWaveTheme.spacing.xxs)
            .testTag("library_artist:${artist.id.value}"),
        supportingText = stringResource(R.string.library_summary,
            pluralStringResource(R.plurals.library_album_count, artist.albumCount, artist.albumCount),
            pluralStringResource(R.plurals.library_track_count, artist.trackCount, artist.trackCount)),
        artwork = { NullWaveCoverArt(monogram = monogram, size = 56.dp, shape = NullWaveTheme.shapes.node) },
    )
}

@Composable
private fun LibraryEmptyCatalog(tab: LibraryTab) {
    val title = when (tab) {
        LibraryTab.Songs -> R.string.library_empty_title
        LibraryTab.Albums -> R.string.library_albums_empty_title
        LibraryTab.Artists -> R.string.library_artists_empty_title
    }
    val description = when (tab) {
        LibraryTab.Songs -> R.string.library_empty_body
        LibraryTab.Albums -> R.string.library_albums_empty_body
        LibraryTab.Artists -> R.string.library_artists_empty_body
    }
    val icon = when (tab) {
        LibraryTab.Songs -> NullWaveIcons.Songs
        LibraryTab.Albums -> NullWaveIcons.Albums
        LibraryTab.Artists -> NullWaveIcons.Artists
    }
    NullWaveEmptyState(stringResource(title), stringResource(description),
        modifier = Modifier.testTag("library_empty"),
        illustration = { LibraryStateIcon(icon) })
}

private val AlbumMinWidth = 148.dp
