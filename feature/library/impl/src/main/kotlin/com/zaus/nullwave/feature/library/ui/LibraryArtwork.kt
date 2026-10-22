package com.zaus.nullwave.feature.library.ui

import com.zaus.nullwave.feature.library.api.ui.libraryMonogram
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.zaus.nullwave.core.designsystem.components.content.NullWaveArtworkPlaceholder
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrack
import com.zaus.nullwave.feature.library.api.model.ArtworkRef
import com.zaus.nullwave.feature.library.artwork.artworkRequest
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader

/** Only composed rows request artwork. Preview/null-loader paths never access the device catalog. */
@Composable
internal fun LibraryArtwork(track: LibraryTrack, loader: LibraryArtworkLoader?, modifier: Modifier = Modifier) {
    LibraryArtwork(track.artwork, track.album ?: track.title, loader, modifier)
}

/** Album cards provide their measured square size so grid covers are not decoded at row resolution. */
@Composable
internal fun LibraryArtwork(
    artwork: ArtworkRef?,
    title: String,
    loader: LibraryArtworkLoader?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val monogram = remember(title) { libraryMonogram(title) }
    if (artwork == null || loader == null || LocalInspectionMode.current) {
        NullWaveCoverArt(modifier = modifier, monogram = monogram, size = size)
        return
    }
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
    val request = remember(context, artwork, pixels) { artworkRequest(context, artwork, pixels) }
    if (request == null) {
        NullWaveCoverArt(modifier = modifier, monogram = monogram, size = size)
        return
    }
    // A recycled row must never retain another album's previous painter/state.
    key(request, loader) {
        val painter = rememberAsyncImagePainter(request, loader.imageLoader, contentScale = ContentScale.Crop)
        val state by painter.state.collectAsState()
        // Album thumbnails are optional. Provider/decode failures don't reliably distinguish
        // missing art from unreadable art; both use a monogram, not a track-error warning.
        val placeholder = when (state) {
            is AsyncImagePainter.State.Success,
            is AsyncImagePainter.State.Error -> NullWaveArtworkPlaceholder.Missing
            else -> NullWaveArtworkPlaceholder.Loading
        }
        NullWaveCoverArt(
            modifier = modifier,
            size = size,
            painter = if (state is AsyncImagePainter.State.Success) painter else null,
            monogram = monogram,
            placeholder = placeholder,
        )
    }
}
