package com.zaus.nullwave.feature.library.ui.artistdetails

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveEmptyState
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveErrorState
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveLoadingIndicator
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessUi
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.presentation.artistdetails.ArtistDetailsBody
import com.zaus.nullwave.feature.library.ui.LibraryAccessContent
import com.zaus.nullwave.feature.library.ui.LibraryStateIcon

@Composable
internal fun ArtistDetailsScreen(
    body: ArtistDetailsBody,
    artworkLoader: LibraryArtworkLoader?,
    accessUi: LibraryAccessUi,
    onBack: () -> Unit,
    onAlbumClick: (AlbumId) -> Unit,
    onRetry: () -> Unit,
) {
    // Keep positions above the access/loading branches; a temporary empty list must not clamp them.
    val listState = rememberLazyListState()
    val shelfState = rememberLazyListState()
    val details = body.details
    if (details != null) {
        ArtistDetailsContent(
            details, artworkLoader, onBack, onAlbumClick,
            onPlay = {}, onShuffle = {}, onTrackClick = {},
            listState = listState, albumShelfState = shelfState,
            refreshing = body is ArtistDetailsBody.Loading,
            refreshFailed = body is ArtistDetailsBody.ReadFailed,
            onRetry = onRetry,
        )
    } else {
        NullWaveScaffold(
            modifier = Modifier.fillMaxSize().testTag(ArtistDetailsTags.Screen),
            topBar = { ArtistDetailsToolbar(onBack) },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (body) {
                    is ArtistDetailsBody.AccessRequired -> LibraryAccessContent(body.action, accessUi)
                    ArtistDetailsBody.NotFound -> NullWaveEmptyState(
                        stringResource(R.string.library_artist_details_missing),
                        stringResource(R.string.library_artist_details_missing_body),
                        illustration = { LibraryStateIcon(NullWaveIcons.Artists) },
                    )
                    is ArtistDetailsBody.ReadFailed -> NullWaveErrorState(
                        stringResource(R.string.library_artist_details_error),
                        stringResource(R.string.library_artist_details_error_body),
                        primaryAction = { NullWaveButton(stringResource(R.string.library_retry), onRetry) },
                    )
                    ArtistDetailsBody.CheckingAccess, is ArtistDetailsBody.Loading -> NullWaveLoadingIndicator(
                        Modifier.padding(NullWaveTheme.spacing.lg).testTag("artist_details_loading"),
                        label = stringResource(R.string.library_loading),
                    )
                    is ArtistDetailsBody.Ready -> Unit
                }
            }
        }
    }
}
