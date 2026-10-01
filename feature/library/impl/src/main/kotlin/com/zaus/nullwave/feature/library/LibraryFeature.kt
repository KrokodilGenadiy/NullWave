package com.zaus.nullwave.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Scaffolding, not screens. The Songs / Artists / Albums / Playlists / Folders tabs go here, plus
 * the detail screens.
 *
 * Note what these signatures do *not* contain: no `NavKey`, no back stack, no navigator. A screen takes
 * lambdas and knows nothing about where they go, which is what lets it be previewed and tested with no
 * navigation wired up. The keys live in `LibraryModule`, at the navigation boundary.
 */
@Composable
fun LibraryScreen(
    onPlayAll: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Placeholder affordance: taps through to a detail screen so the navigation wiring is
        // verifiable before the real tabs exist.
        Text(
            text = "LIBRARY",
            style = NullWaveTheme.typography.h1,
            color = NullWaveTheme.colors.textPrimary,
            modifier = Modifier.clickable { onAlbumClick(PlaceholderAlbumId) },
        )
    }
}

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "ALBUM $albumId",
            style = NullWaveTheme.typography.h1,
            color = NullWaveTheme.colors.textPrimary,
            modifier = Modifier.clickable(onClick = onBack),
        )
    }
}

/** Stand-in until the library lists real albums. */
private const val PlaceholderAlbumId = 42L
