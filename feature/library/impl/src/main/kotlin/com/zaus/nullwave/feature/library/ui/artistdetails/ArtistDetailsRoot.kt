package com.zaus.nullwave.feature.library.ui.artistdetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import com.zaus.nullwave.core.mvi.ui.collectMviUiWithLifecycle
import com.zaus.nullwave.core.mvi.ui.rememberMviUiApi
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessHost
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.presentation.artistdetails.ArtistDetailsBody
import com.zaus.nullwave.feature.library.presentation.artistdetails.ArtistDetailsIntent
import com.zaus.nullwave.feature.library.presentation.artistdetails.ArtistDetailsViewModel
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
internal fun ArtistDetailsRoot(
    artistId: ArtistId,
    accessHost: LibraryAccessHost,
    artworkLoader: LibraryArtworkLoader?,
    onBack: () -> Unit,
    onAlbumClick: (AlbumId) -> Unit,
    viewModel: ArtistDetailsViewModel = assistedMetroViewModel<ArtistDetailsViewModel, ArtistDetailsViewModel.Factory> {
        create(artistId)
    },
) {
    val api = rememberMviUiApi(viewModel)
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    // Register the fresh permission check before binding sources on resume.
    accessHost.Content({ api.onIntent(ArtistDetailsIntent.AccessChecked(it)) }) { accessUi ->
        val ui = viewModel.collectMviUiWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        LaunchedEffect(viewModel, locale) { ui.onIntent(ArtistDetailsIntent.LocaleChanged(locale)) }
        val body = when (val access = accessUi.access) {
            null -> ArtistDetailsBody.CheckingAccess
            is LibraryAccess.Required -> ArtistDetailsBody.AccessRequired(access.action)
            LibraryAccess.Granted -> ui.state.body
        }
        ArtistDetailsScreen(body, artworkLoader, accessUi, onBack, onAlbumClick,
            onRetry = { ui.onIntent(ArtistDetailsIntent.Retry) })
    }
}
