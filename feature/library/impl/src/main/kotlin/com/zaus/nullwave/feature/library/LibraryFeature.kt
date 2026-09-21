package com.zaus.nullwave.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.di.ActivityScope
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.feature.library.api.AlbumDetailKey
import com.zaus.nullwave.feature.library.api.LibraryKey
import com.zaus.nullwave.feature.player.api.PlayerController
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

/**
 * Scaffolding, not a screen. The Songs / Artists / Albums / Playlists / Folders tabs go here, plus
 * the detail screens.
 *
 * Two patterns worth copying:
 * - One installer can register several destinations, as below.
 * - [PlayerController] comes from `:feature:player:api`. This module cannot see
 *   `:feature:player:impl`, yet Metro binds the interface to the implementation there.
 */
@ContributesTo(ActivityScope::class)
@BindingContainer
object LibraryFeatureBindings {

    @Provides
    @IntoSet
    fun libraryEntries(playerController: PlayerController): EntryProviderInstaller = {
        entry<LibraryKey> {
            LibraryScreen(onPlayAll = { playerController.play(emptyList()) })
        }
        entry<AlbumDetailKey> { key -> AlbumDetailScreen(albumId = key.albumId) }
    }
}

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
