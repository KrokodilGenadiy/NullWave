package com.zaus.nullwave.feature.library.di

import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.di.ActivityScope
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.RailSection
import com.zaus.nullwave.core.navigation.TopLevelDestination
import com.zaus.nullwave.feature.library.AlbumDetailScreen
import com.zaus.nullwave.feature.library.LibraryScreen
import com.zaus.nullwave.feature.library.api.AlbumDetailKey
import com.zaus.nullwave.feature.library.api.LibraryKey
import com.zaus.nullwave.feature.player.api.PlayerController
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

/**
 * One installer can register several destinations.
 *
 * [PlayerController] comes from `:feature:player:api`. This module cannot see
 * `:feature:player:impl`, yet Metro binds the interface to the implementation there.
 */
@ContributesTo(ActivityScope::class)
@BindingContainer
object LibraryModule {

    @IntoSet
    @Provides
    fun provideEntryProviderInstaller(playerController: PlayerController): EntryProviderInstaller = {
        entry<LibraryKey> {
            LibraryScreen(onPlayAll = { playerController.play(emptyList()) })
        }
        entry<AlbumDetailKey> { key ->
            AlbumDetailScreen(albumId = key.albumId)
        }
    }

    /**
     * The design labels the library's drawer entry "PLAYER" and makes it the default destination -
     * the expanded player is reached from the mini-player, never from the drawer.
     */
    @IntoSet
    @Provides
    fun provideTopLevelDestination(): TopLevelDestination = TopLevelDestination(
        key = LibraryKey,
        label = "Player",
        icon = NullWaveIcons.NowPlaying,
        section = RailSection.Primary,
    )
}
