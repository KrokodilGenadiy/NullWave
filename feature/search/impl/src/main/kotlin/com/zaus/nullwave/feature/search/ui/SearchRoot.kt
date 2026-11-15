package com.zaus.nullwave.feature.search.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import com.zaus.nullwave.core.mvi.ui.collectMviUiWithLifecycle
import com.zaus.nullwave.core.mvi.ui.rememberMviUiApi
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessHost
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.search.presentation.SearchIntent
import com.zaus.nullwave.feature.search.presentation.SearchViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
internal fun SearchRoot(
    onBack: () -> Unit,
    accessHost: LibraryAccessHost,
    artwork: LibraryArtworkRenderer? = null,
    viewModel: SearchViewModel = metroViewModel(),
    onAlbumClick: (AlbumId) -> Unit = {},
) {
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val uiApi = rememberMviUiApi(viewModel)
    // Register the permission observer before the source binding. Resume checks fresh Android
    // access even when this entry retained its VM or received a buffered ActivityResult.
    accessHost.Content({ uiApi.onIntent(SearchIntent.AccessChecked(it == LibraryAccess.Granted)) }) { accessUi ->
        val ui = viewModel.collectMviUiWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        LaunchedEffect(viewModel, localeTag) { ui.onIntent(SearchIntent.LocaleChanged(localeTag)) }
        SearchScreen(ui.state, ui.onIntent, onBack, accessUi, artwork, onAlbumClick)
    }
}
