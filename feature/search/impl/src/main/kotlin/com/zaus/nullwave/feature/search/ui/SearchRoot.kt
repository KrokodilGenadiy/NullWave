package com.zaus.nullwave.feature.search.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import com.zaus.nullwave.core.mvi.ui.collectMviStateWithLifecycle
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
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
) {
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    // Register the permission observer before the source binding. Resume checks fresh Android
    // access even when this entry retained its VM or received a buffered ActivityResult.
    accessHost.Content({ viewModel.onIntent(SearchIntent.AccessChecked(it == LibraryAccess.Granted)) }) { accessUi ->
        val state by viewModel.collectMviStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        LaunchedEffect(viewModel, localeTag) { viewModel.onIntent(SearchIntent.LocaleChanged(localeTag)) }
        SearchScreen(state, viewModel::onIntent, onBack, accessUi, artwork)
    }
}
