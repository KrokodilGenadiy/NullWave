package com.zaus.nullwave.feature.library.di

import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.navigateTo
import com.zaus.nullwave.feature.about.api.AboutKey
import com.zaus.nullwave.feature.search.api.SearchKey
import com.zaus.nullwave.feature.library.ui.LibraryRoot
import com.zaus.nullwave.feature.library.api.LibraryKey
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@BindingContainer
@ContributesTo(AppScope::class)
object LibraryEntries {
    @Provides
    @IntoSet
    fun entries(artworkLoader: () -> LibraryArtworkLoader): EntryProviderInstaller = {
        entry<LibraryKey> {
            LibraryRoot(onAboutClick = { navigateTo(AboutKey) }, artworkLoader = artworkLoader(),
                onSearchClick = { navigateTo(SearchKey) })
        }
    }
}
