package com.zaus.nullwave.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Scaffolding, not screens. The Songs / Artists / Albums / Playlists / Folders tabs go here, plus
 * the detail screens.
 */
@Composable
fun LibraryScreen(
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "LIBRARY",
            style = NullWaveTheme.typography.h1,
            color = NullWaveTheme.colors.textPrimary,
        )
    }
}

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "ALBUM $albumId",
            style = NullWaveTheme.typography.h1,
            color = NullWaveTheme.colors.textPrimary,
        )
    }
}
