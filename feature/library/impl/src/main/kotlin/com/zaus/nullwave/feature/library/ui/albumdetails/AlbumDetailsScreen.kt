package com.zaus.nullwave.feature.library.ui.albumdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveEmptyState
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveErrorState
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveInlineMessage
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveLoadingIndicator
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageAction
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageVariant
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumDetails
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessUi
import com.zaus.nullwave.feature.library.api.ui.libraryAlbumCredit
import com.zaus.nullwave.feature.library.api.ui.libraryDuration
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.presentation.albumdetails.AlbumDetailsBody
import com.zaus.nullwave.feature.library.presentation.albumdetails.AlbumPlaybackStart
import com.zaus.nullwave.feature.library.ui.LibraryAccessContent
import com.zaus.nullwave.feature.library.ui.LibraryArtwork
import com.zaus.nullwave.feature.library.ui.LibraryPlaybackMessage
import com.zaus.nullwave.feature.library.ui.LibraryStateIcon

@Composable
internal fun AlbumDetailsScreen(
    body: AlbumDetailsBody,
    artworkLoader: LibraryArtworkLoader?,
    accessUi: LibraryAccessUi,
    playbackError: UiText?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (LibraryAlbumDetails, AlbumPlaybackStart) -> Unit,
    onDismissPlaybackError: () -> Unit,
) {
    val listState = rememberLazyListState()
    val details = body.details
    val spacing = NullWaveTheme.spacing
    NullWaveScaffold(
        modifier = Modifier.fillMaxSize().testTag("album_details"),
        snackbarHost = {
            playbackError?.let { LibraryPlaybackMessage(it, onDismissPlaybackError) }
        },
        topBar = {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = spacing.xs),
                contentAlignment = Alignment.CenterStart,
            ) {
                NullWaveIconButton(NullWaveIcons.Back, stringResource(R.string.album_details_back), onBack,
                    modifier = Modifier.testTag("album_back"))
                if (details != null && body is AlbumDetailsBody.Loading) {
                    NullWaveLoadingIndicator(Modifier.align(Alignment.CenterEnd).padding(end = spacing.md),
                        label = stringResource(R.string.library_refreshing), showLabel = false)
                }
            }
        },
        bottomBar = {
            if (body is AlbumDetailsBody.ReadFailed && details != null) {
                NullWaveInlineMessage(
                    message = stringResource(R.string.album_details_refresh_failed),
                    modifier = Modifier.padding(spacing.md).semantics { liveRegion = LiveRegionMode.Polite },
                    variant = NullWaveMessageVariant.Error,
                    action = NullWaveMessageAction(stringResource(R.string.library_retry), onRetry),
                )
            }
        },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        if (details != null) {
            LazyColumn(modifier = content, state = listState) {
                item("header") {
                    AlbumHeader(details, artworkLoader)
                    FlowRow(
                        modifier = Modifier.padding(horizontal = NullWaveTheme.dimens.gutter, vertical = spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        NullWaveButton(stringResource(R.string.album_details_play), { onPlay(details, AlbumPlaybackStart.At(0)) })
                        NullWaveButton(
                            stringResource(R.string.album_details_shuffle), { onPlay(details, AlbumPlaybackStart.Shuffle) },
                            variant = NullWaveButtonVariant.Secondary, borderStyle = NullWaveBorderStyle.ClippedCorners,
                        )
                    }
                }
                itemsIndexed(details.tracks, key = { _, track -> track.id.value }, contentType = { _, _ -> "track" }) { index, track ->
                    AlbumTrackRow(index + 1, track) { onPlay(details, AlbumPlaybackStart.At(index)) }
                    NullWaveDivider()
                }
            }
        } else {
            Column(content.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                when (body) {
                    is AlbumDetailsBody.AccessRequired -> LibraryAccessContent(body.action, accessUi)
                    AlbumDetailsBody.NotFound -> NullWaveEmptyState(stringResource(R.string.album_details_missing),
                        stringResource(R.string.album_details_missing_body), illustration = { LibraryStateIcon(NullWaveIcons.Albums) })
                    is AlbumDetailsBody.ReadFailed -> NullWaveErrorState(stringResource(R.string.album_details_error),
                        stringResource(R.string.album_details_error_body),
                        primaryAction = { NullWaveButton(stringResource(R.string.library_retry), onRetry) })
                    AlbumDetailsBody.CheckingAccess, is AlbumDetailsBody.Loading -> NullWaveLoadingIndicator(
                        modifier = Modifier.padding(NullWaveTheme.spacing.lg), label = stringResource(R.string.library_loading))
                    is AlbumDetailsBody.Ready -> Unit
                }
            }
        }
    }
}

@Composable
private fun AlbumTrackRow(number: Int, track: MediaTrack, onClick: () -> Unit) {
    val colors = NullWaveTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NullWaveTheme.dimens.gutter, vertical = NullWaveTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number.toString(), Modifier.widthIn(min = 22.dp), style = NullWaveTheme.typography.mono, color = colors.textSecondary)
        Text(track.title, Modifier.weight(1f), style = NullWaveTheme.typography.body, color = colors.textPrimary)
        libraryDuration(track.durationMs)?.let {
            Text(it, style = NullWaveTheme.typography.mono, color = colors.textSecondary)
        }
    }
}

@Composable
private fun AlbumHeader(details: LibraryAlbumDetails, loader: LibraryArtworkLoader?) {
    val album = details.album
    val title = album.title ?: stringResource(R.string.library_unknown_album)
    val count = pluralStringResource(R.plurals.library_track_count, album.trackCount, album.trackCount)
    val duration = remember(details) {
        var total = 0L
        for (track in details.tracks) {
            val ms = track.durationMs ?: return@remember null
            if (Long.MAX_VALUE - total < ms) return@remember null
            total += ms
        }
        libraryDuration(total)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
        NullWaveTheme.colors.primary.copy(alpha = .10f), Color.Transparent))).padding(NullWaveTheme.dimens.gutter)) {
        val coverSize = minOf(140.dp, maxWidth * .38f)
        Row(horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.dimens.gutter), verticalAlignment = Alignment.Bottom) {
            LibraryArtwork(album.artwork, title, loader, size = coverSize)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
                Text(title, Modifier.semantics { heading() }, style = NullWaveTheme.typography.h1)
                Text(libraryAlbumCredit(album.artistCredit, stringResource(R.string.library_unknown_artist),
                    stringResource(R.string.library_various_artists)), style = NullWaveTheme.typography.body,
                    color = NullWaveTheme.colors.secondary)
                Text(if (duration == null) count else stringResource(R.string.library_summary, count, duration),
                    style = NullWaveTheme.typography.micro, color = NullWaveTheme.colors.textSecondary)
            }
        }
    }
}
