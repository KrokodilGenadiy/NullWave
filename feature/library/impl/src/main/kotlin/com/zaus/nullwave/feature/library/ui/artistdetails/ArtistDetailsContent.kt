package com.zaus.nullwave.feature.library.ui.artistdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.content.NullWaveMediaGridCard
import com.zaus.nullwave.core.designsystem.components.content.NullWaveTrackRow
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveInlineMessage
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveLoadingIndicator
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageAction
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageVariant
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistDetails
import com.zaus.nullwave.feature.library.api.ui.libraryAlbumCredit
import com.zaus.nullwave.feature.library.api.ui.libraryDuration
import com.zaus.nullwave.feature.library.api.ui.libraryMonogram
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.ui.LibraryArtwork

/**
 * Stateless artist page (artboard 29) for one ready [details] snapshot, rendered in its given order.
 *
 * Album cards describe the whole album, including compilations; the track list holds only this artist's
 * tracks. There is no portrait, year or popularity data, so the header uses the artist monogram plate and
 * the list shows every track. Play, Shuffle and track taps are forwarded only while [playbackEnabled];
 * otherwise rows stay informational with normal colours. Back and album cards never depend on it.
 *
 * [windowInsets] goes to the scaffold (pass zero insets when the host already owns them).
 * [contentPadding] is extra space the host reserves inside the list, for example under a MiniPlayer.
 * Scroll positions use saveable Compose state and survive renderer recreation inside a saveable host.
 */
@Composable
internal fun ArtistDetailsContent(
    details: LibraryArtistDetails,
    artworkLoader: LibraryArtworkLoader?,
    onBack: () -> Unit,
    onAlbumClick: (AlbumId) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onTrackClick: (TrackId) -> Unit,
    modifier: Modifier = Modifier,
    playbackEnabled: Boolean = false,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    albumShelfState: LazyListState = rememberLazyListState(),
    refreshing: Boolean = false,
    refreshFailed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val spacing = NullWaveTheme.spacing
    val gutter = NullWaveTheme.dimens.gutter
    val colors = NullWaveTheme.colors
    NullWaveScaffold(
        modifier = modifier.fillMaxSize().testTag(ArtistDetailsTags.Screen),
        windowInsets = windowInsets,
        topBar = { ArtistDetailsToolbar(onBack, refreshing) },
        bottomBar = {
            if (refreshFailed) {
                NullWaveInlineMessage(
                    message = stringResource(R.string.library_artist_details_refresh_failed),
                    modifier = Modifier.padding(spacing.md).testTag("artist_details_refresh_error")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    variant = NullWaveMessageVariant.Error,
                    action = NullWaveMessageAction(stringResource(R.string.library_retry), onRetry),
                )
            }
        },
    ) { padding ->
        val plateHeight = with(LocalDensity.current) { StripedPlateHeight.toPx() }
        // Static backdrop behind the toolbar and header, like the album tint: imagery is never invented.
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .stripedPlate(colors.surfaceRaised, colors.surface, colors.bg, plateHeight)
                .padding(padding)
                .consumeWindowInsets(padding)
                .testTag(ArtistDetailsTags.List),
            state = listState,
            contentPadding = contentPadding,
        ) {
            item(key = "header", contentType = "header") {
                ArtistHeader(details)
                FlowRow(
                    modifier = Modifier.padding(horizontal = gutter, vertical = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    NullWaveButton(
                        stringResource(R.string.library_artist_details_play),
                        { if (playbackEnabled) onPlay() },
                        modifier = Modifier.testTag(ArtistDetailsTags.Play),
                        enabled = playbackEnabled,
                    )
                    NullWaveButton(
                        stringResource(R.string.library_artist_details_shuffle),
                        { if (playbackEnabled) onShuffle() },
                        modifier = Modifier.testTag(ArtistDetailsTags.Shuffle),
                        variant = NullWaveButtonVariant.Secondary,
                        enabled = playbackEnabled,
                        borderStyle = NullWaveBorderStyle.ClippedCorners,
                    )
                }
            }
            if (details.albums.isNotEmpty()) {
                item(key = "albums_heading", contentType = "heading") {
                    SectionLabel(stringResource(R.string.library_artist_details_albums))
                }
                item(key = "albums", contentType = "albums") {
                    AlbumShelf(details.albums, artworkLoader, albumShelfState, onAlbumClick)
                }
            }
            item(key = "tracks_heading", contentType = "heading") {
                SectionLabel(stringResource(R.string.library_artist_details_tracks))
            }
            items(details.tracks, key = { "track:${it.id.value}" }, contentType = { "track" }) { track ->
                Column {
                    val onClick: (() -> Unit)? = if (playbackEnabled) ({ onTrackClick(track.id) }) else null
                    ArtistTrackRow(track, artworkLoader, onClick)
                    NullWaveDivider(Modifier.padding(horizontal = gutter))
                }
            }
        }
    }
}

@Composable
internal fun ArtistDetailsToolbar(onBack: () -> Unit, refreshing: Boolean = false) {
    val spacing = NullWaveTheme.spacing
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = spacing.xs),
        contentAlignment = Alignment.CenterStart,
    ) {
        NullWaveIconButton(NullWaveIcons.Back, stringResource(R.string.library_artist_details_back), onBack,
            modifier = Modifier.testTag(ArtistDetailsTags.Back))
        if (refreshing) {
            NullWaveLoadingIndicator(Modifier.align(Alignment.CenterEnd).padding(end = spacing.md),
                label = stringResource(R.string.library_refreshing), showLabel = false)
        }
    }
}

/** Test tags shared with instrumented tests; IDs, never names, distinguish albums and tracks. */
internal object ArtistDetailsTags {
    const val Screen = "artist_details"
    const val List = "artist_details_list"
    const val Back = "artist_details_back"
    const val Play = "artist_details_play"
    const val Shuffle = "artist_details_shuffle"
    const val AlbumShelf = "artist_details_albums"
    fun album(id: AlbumId) = "artist_details_album:${id.value}"
    fun track(id: TrackId) = "artist_details_track:${id.value}"
}

@Composable
private fun ArtistHeader(details: LibraryArtistDetails) {
    val artist = details.artist
    val name = artist.name ?: stringResource(R.string.library_unknown_artist)
    val monogram = remember(name) { libraryMonogram(name) }
    val summary = artistSummary(artist, details.tracks)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = NullWaveTheme.dimens.gutter,
        end = NullWaveTheme.dimens.gutter, top = NullWaveTheme.spacing.xl)) {
        val plateSize = minOf(MonogramPlateSize, maxWidth * .3f)
        Row(horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.dimens.gutter), verticalAlignment = Alignment.Bottom) {
            NullWaveCoverArt(monogram = monogram, size = plateSize, shape = NullWaveTheme.shapes.node)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
                // The full name wraps; no ellipsis, so long Japanese/Russian names remain complete.
                Text(name, Modifier.semantics { heading() }, style = NullWaveTheme.typography.display,
                    color = NullWaveTheme.colors.textPrimary)
                Text(summary, style = NullWaveTheme.typography.mono, color = NullWaveTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun artistSummary(artist: LibraryArtist, tracks: List<MediaTrack>): String {
    val tracksLabel = pluralStringResource(R.plurals.library_track_count, artist.trackCount, artist.trackCount)
    val counts = if (artist.albumCount == 0) tracksLabel else stringResource(R.string.library_summary,
        pluralStringResource(R.plurals.library_album_count, artist.albumCount, artist.albumCount), tracksLabel)
    // Unknown or overflowing durations omit the total instead of showing a partial sum.
    val duration = remember(tracks) {
        var total = 0L
        for (track in tracks) {
            val ms = track.durationMs ?: return@remember null
            if (Long.MAX_VALUE - total < ms) return@remember null
            total += ms
        }
        libraryDuration(total)
    }
    return if (duration == null) counts else stringResource(R.string.library_summary, counts, duration)
}

@Composable
private fun SectionLabel(text: String) {
    NullWaveMicroLabel(
        text,
        Modifier.fillMaxWidth()
            .padding(start = NullWaveTheme.dimens.gutter, end = NullWaveTheme.dimens.gutter,
                top = NullWaveTheme.spacing.md, bottom = NullWaveTheme.spacing.xs)
            .semantics { heading() },
    )
}

@Composable
private fun AlbumShelf(
    albums: List<LibraryAlbum>,
    loader: LibraryArtworkLoader?,
    state: LazyListState,
    onAlbumClick: (AlbumId) -> Unit,
) {
    val gutter = NullWaveTheme.dimens.gutter
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Grow with text like the Albums grid, but always leave a whole card inside the window.
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val cardWidth = minOf(AlbumCardWidth * fontScale, (maxWidth - gutter * 2).coerceAtLeast(AlbumCardMinWidth))
        LazyRow(
            modifier = Modifier.fillMaxWidth().testTag(ArtistDetailsTags.AlbumShelf),
            state = state,
            contentPadding = PaddingValues(horizontal = gutter, vertical = NullWaveTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
        ) {
            items(albums, key = { "album:${it.id.value}" }, contentType = { "album" }) { album ->
                ArtistAlbumCard(album, loader, cardWidth) { onAlbumClick(album.id) }
            }
        }
    }
}

@Composable
private fun ArtistAlbumCard(album: LibraryAlbum, loader: LibraryArtworkLoader?, width: Dp, onClick: () -> Unit) {
    val title = album.title ?: stringResource(R.string.library_unknown_album)
    val credit = libraryAlbumCredit(album.artistCredit, stringResource(R.string.library_unknown_artist),
        stringResource(R.string.library_various_artists))
    NullWaveMediaGridCard(
        title = title,
        onClick = onClick,
        supportingText = credit,
        modifier = Modifier.width(width).testTag(ArtistDetailsTags.album(album.id)),
        artwork = {
            BoxWithConstraints { LibraryArtwork(album.artwork, title, loader, size = maxWidth) }
        },
        badge = {
            // The whole album's count: a compilation can hold more tracks than this artist has.
            Text(pluralStringResource(R.plurals.library_track_count, album.trackCount, album.trackCount),
                style = NullWaveTheme.typography.micro, color = NullWaveTheme.colors.textSecondary)
        },
    )
}

@Composable
private fun ArtistTrackRow(track: MediaTrack, loader: LibraryArtworkLoader?, onClick: (() -> Unit)?) {
    NullWaveTrackRow(
        title = track.title,
        onClick = onClick,
        subtitle = track.album,
        durationLabel = libraryDuration(track.durationMs),
        modifier = Modifier.testTag(ArtistDetailsTags.track(track.id)),
        artwork = { LibraryArtwork(track, loader) },
    )
}

/** Diagonal stripes from artboard 29, fading into the page background below [height]. */
private fun Modifier.stripedPlate(
    stripe: Color,
    base: Color,
    page: Color,
    height: Float,
): Modifier = background(page).drawBehind {
    val bottom = minOf(height, size.height)
    clipRect(bottom = bottom) {
        drawRect(base)
        val step = StripePeriod.toPx()
        val stroke = step / 2f
        var x = -bottom
        while (x < size.width + bottom) {
            drawLine(stripe, Offset(x, bottom), Offset(x + bottom, 0f), strokeWidth = stroke)
            x += step
        }
        drawRect(Brush.verticalGradient(0f to page.copy(alpha = .2f), .92f to page, startY = 0f, endY = bottom))
    }
}

private val MonogramPlateSize = 96.dp
private val AlbumCardWidth = 132.dp
private val AlbumCardMinWidth = 96.dp
private val StripedPlateHeight = 260.dp
private val StripePeriod = 24.dp
