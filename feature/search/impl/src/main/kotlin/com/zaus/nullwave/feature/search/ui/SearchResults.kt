package com.zaus.nullwave.feature.search.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.content.NullWaveMediaListItem
import com.zaus.nullwave.core.designsystem.components.content.NullWaveTrackRow
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveEmptyState
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.library.api.model.ArtworkRef
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.library.api.ui.libraryAlbumCredit
import com.zaus.nullwave.feature.library.api.ui.libraryDuration
import com.zaus.nullwave.feature.library.api.ui.libraryMonogram
import com.zaus.nullwave.feature.library.api.ui.librarySubtitle
import com.zaus.nullwave.feature.search.impl.R
import com.zaus.nullwave.feature.search.presentation.SearchBody
import com.zaus.nullwave.feature.search.presentation.SearchIntent

/** All groups share a virtualized scroll surface; empty groups reserve no space or fake actions. */
@Composable
internal fun SearchResults(
    body: SearchBody.Ready,
    rawQuery: String,
    onIntent: (SearchIntent) -> Unit,
    artwork: LibraryArtworkRenderer?,
    insets: PaddingValues,
    listState: LazyListState,
) {
    val gutter = NullWaveTheme.dimens.gutter
    val spacing = NullWaveTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize().consumeWindowInsets(insets).testTag("search_results"),
        state = listState,
        contentPadding = insets,
    ) {
        if (body.isEmpty) item("no_matches", contentType = "message") {
            NullWaveEmptyState(stringResource(R.string.search_no_matches), stringResource(R.string.search_no_matches_body, rawQuery),
                modifier = Modifier.testTag("search_no_matches"), illustration = { SearchStateIcon(NullWaveIcons.Search) },
                primaryAction = { NullWaveButton(stringResource(R.string.search_clear), { onIntent(SearchIntent.ClearQuery) }) })
        }
        val results = body.results
        if (results.tracks.isNotEmpty()) {
            section("tracks", R.plurals.search_tracks, results.tracks.size)
            items(results.tracks, key = { "track:${it.track.id.value}" }, contentType = { "track" }) { hit ->
                val track = hit.track
                Column(Modifier.testTag("search_track:${track.id.value}")) {
                    NullWaveTrackRow(title = track.title,
                        subtitle = librarySubtitle(track.artist, track.album, stringResource(R.string.search_unknown_artist)),
                        durationLabel = libraryDuration(track.durationMs),
                        artwork = { SearchArtwork(track.artwork, track.album ?: track.title, artwork, 48.dp) })
                    NullWaveDivider(Modifier.padding(horizontal = gutter))
                }
            }
        }
        if (results.albums.isNotEmpty()) {
            section("albums", R.plurals.search_albums, results.albums.size)
            items(results.albums, key = { "album:${it.album.id.value}" }, contentType = { "album" }) { hit ->
                val album = hit.album
                val title = album.title ?: stringResource(R.string.search_unknown_album)
                val credit = libraryAlbumCredit(album.artistCredit, stringResource(R.string.search_unknown_artist),
                    stringResource(R.string.search_various_artists))
                NullWaveMediaListItem(title,
                    modifier = Modifier.padding(horizontal = gutter, vertical = spacing.xxs).testTag("search_album:${album.id.value}"),
                    supportingText = stringResource(R.string.search_metadata, credit,
                        pluralStringResource(R.plurals.search_tracks, album.trackCount, album.trackCount)),
                    artwork = { SearchArtwork(album.artwork, title, artwork, 56.dp) },
                    badge = if (hit.matchingArtistNames.isNotEmpty()) {{
                        SearchExplanation(stringResource(R.string.search_artist_match, hit.matchingArtistNames.joinToString(", ")))
                    }} else null,
                )
            }
        }
        if (results.artists.isNotEmpty()) {
            section("artists", R.plurals.search_artists, results.artists.size)
            items(results.artists, key = { "artist:${it.id.value}" }, contentType = { "artist" }) { artist ->
                val name = artist.name ?: stringResource(R.string.search_unknown_artist)
                NullWaveMediaListItem(name,
                    modifier = Modifier.padding(horizontal = gutter, vertical = spacing.xxs).testTag("search_artist:${artist.id.value}"),
                    supportingText = stringResource(R.string.search_metadata,
                        pluralStringResource(R.plurals.search_albums, artist.albumCount, artist.albumCount),
                        pluralStringResource(R.plurals.search_tracks, artist.trackCount, artist.trackCount)),
                    artwork = { SearchArtwork(null, name, artwork, 56.dp) },
                )
            }
        }
    }
}

private fun LazyListScope.section(id: String, label: Int, count: Int) {
    item("section:$id", contentType = "heading") {
        Text(pluralStringResource(label, count, count),
            Modifier.padding(NullWaveTheme.dimens.gutter).testTag("search_section:$id").semantics { heading() },
            style = NullWaveTheme.typography.micro, color = NullWaveTheme.colors.textSecondary)
    }
}

@Composable
private fun SearchExplanation(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(bottom = NullWaveTheme.spacing.xs), style = NullWaveTheme.typography.caption,
        color = NullWaveTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SearchArtwork(ref: ArtworkRef?, title: String, renderer: LibraryArtworkRenderer?, size: Dp) {
    if (renderer == null) {
        val monogram = remember(title) { libraryMonogram(title) }
        NullWaveCoverArt(size = size, monogram = monogram)
    } else renderer.Artwork(ref, title, size)
}
