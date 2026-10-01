package com.zaus.nullwave.feature.library.di

import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.di.ActivityScope
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.RailSection
import com.zaus.nullwave.core.navigation.TopLevelDestination
import com.zaus.nullwave.core.navigation.navigateBack
import com.zaus.nullwave.core.navigation.navigateTo
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
 * One installer can register several destinations, and this is the worked example of the pattern.
 *
 * Two kinds of dependency meet here, and they arrive by different routes:
 *
 * - [PlayerController] comes from DI, via `:feature:player:api`. This module cannot see
 *   `:feature:player:impl`, yet Metro binds the interface to the implementation there.
 * - The `NavigationScope` behind `navigateTo` comes from the host as a **context parameter** - never named
 *   here, which is why the installer's shape is identical to modules that do not navigate. It is
 *   deliberately not in the graph: the back stack belongs to composition so it survives process death and
 *   dies with the UI.
 *
 * Both are turned into plain lambdas before they reach a screen. `LibraryScreen` takes `onPlayAll` and
 * `onAlbumClick` and has no idea that one is a DI singleton and the other is a navigation mutation.
 */
@ContributesTo(ActivityScope::class)
@BindingContainer
object LibraryModule {

    @IntoSet
    @Provides
    fun provideEntryProviderInstaller(
        playerController: PlayerController,
    ): EntryProviderInstaller = {
        entry<LibraryKey> {
            LibraryScreen(
                onPlayAll = { playerController.play(emptyList()) },
                // `navigateTo` resolves here because a NavigationScope is in context. It would not
                // resolve inside LibraryScreen, which is the point: the screen has to take a lambda.
                // It also guards the double-tap double-push that raw `add` would allow.
                onAlbumClick = { albumId -> navigateTo(AlbumDetailKey(albumId)) },
            )
        }
        entry<AlbumDetailKey> { key ->
            // `navigateBack`, not `removeLastOrNull`: it refuses to empty the stack. NavDisplay throws
            // on an empty back stack, and emptying it is never what an in-app "up" affordance means.
            AlbumDetailScreen(albumId = key.albumId, onBack = { navigateBack() })
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
