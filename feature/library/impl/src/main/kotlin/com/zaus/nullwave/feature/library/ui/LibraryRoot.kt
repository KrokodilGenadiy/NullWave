package com.zaus.nullwave.feature.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import com.zaus.nullwave.core.navigation.SectionMenuButton
import com.zaus.nullwave.core.mvi.ui.collectMviUiWithLifecycle
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessHost
import com.zaus.nullwave.core.mvi.ui.rememberMviUiApi
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
internal fun LibraryRoot(
    menuButton: SectionMenuButton,
    artworkLoader: LibraryArtworkLoader?,
    viewModel: LibraryViewModel = metroViewModel(),
    accessHost: LibraryAccessHost,
    onSearchClick: () -> Unit = {},
    onAlbumClick: (AlbumId) -> Unit = {},
) {
    val api = rememberMviUiApi(viewModel)
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    accessHost.Content({ api.onIntent(LibraryIntent.AccessChecked(it)) }) { accessUi ->
        val ui = viewModel.collectMviUiWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        LaunchedEffect(viewModel, localeTag) { ui.onIntent(LibraryIntent.LocaleChanged(localeTag)) }
        LibraryScreen(ui.state, ui.onIntent, menuButton, accessUi, artworkLoader, onSearchClick, onAlbumClick)
    }
}
