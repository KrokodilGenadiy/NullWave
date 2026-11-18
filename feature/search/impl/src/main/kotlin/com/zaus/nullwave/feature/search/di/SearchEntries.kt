package com.zaus.nullwave.feature.search.di

import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.navigateBack
import com.zaus.nullwave.core.navigation.navigateTo
import com.zaus.nullwave.feature.library.api.AlbumDetailsKey
import com.zaus.nullwave.feature.library.api.ArtistDetailsKey
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessHost
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.search.api.SearchKey
import com.zaus.nullwave.feature.search.ui.SearchRoot
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@BindingContainer
@ContributesTo(AppScope::class)
object SearchEntries {
    @Provides
    @IntoSet
    fun entries(accessHost: LibraryAccessHost, artwork: () -> LibraryArtworkRenderer): EntryProviderInstaller = {
        entry<SearchKey> {
            SearchRoot(onBack = { navigateBack(SearchKey) }, accessHost = accessHost, artwork = artwork(),
                onAlbumClick = { navigateTo(SearchKey, AlbumDetailsKey(it)) }, onArtistClick = { navigateTo(SearchKey, ArtistDetailsKey(it)) })
        }
    }
}
