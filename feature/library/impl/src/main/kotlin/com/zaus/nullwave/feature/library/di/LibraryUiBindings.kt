package com.zaus.nullwave.feature.library.di

import com.zaus.nullwave.feature.library.api.ui.LibraryAccessHost
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.ui.AndroidLibraryAccessHost
import com.zaus.nullwave.feature.library.ui.AndroidLibraryArtworkRenderer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** Public contribution exposes capabilities while their Android implementations stay internal. */
@BindingContainer
@ContributesTo(AppScope::class)
object LibraryUiBindings {
    @Provides
    private fun access(): LibraryAccessHost = AndroidLibraryAccessHost()

    @Provides
    private fun artwork(loader: LibraryArtworkLoader): LibraryArtworkRenderer = AndroidLibraryArtworkRenderer(loader)
}
