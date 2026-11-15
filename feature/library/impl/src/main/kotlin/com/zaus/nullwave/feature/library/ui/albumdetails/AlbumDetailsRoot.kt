package com.zaus.nullwave.feature.library.ui.albumdetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import com.zaus.nullwave.core.mvi.ui.collectMviUiWithLifecycle
import com.zaus.nullwave.core.mvi.ui.rememberMviUiApi
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessHost
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.presentation.albumdetails.AlbumDetailsBody
import com.zaus.nullwave.feature.library.presentation.albumdetails.AlbumDetailsIntent
import com.zaus.nullwave.feature.library.presentation.albumdetails.AlbumDetailsViewModel
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
internal fun AlbumDetailsRoot(
    albumId: AlbumId,
    accessHost: LibraryAccessHost,
    artworkLoader: LibraryArtworkLoader?,
    onBack: () -> Unit,
    viewModel: AlbumDetailsViewModel = assistedMetroViewModel<AlbumDetailsViewModel, AlbumDetailsViewModel.Factory> {
        create(albumId)
    },
) {
    val api = rememberMviUiApi(viewModel)
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    accessHost.Content({ api.onIntent(AlbumDetailsIntent.AccessChecked(it)) }) { accessUi ->
        val ui = viewModel.collectMviUiWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        LaunchedEffect(viewModel, locale) { ui.onIntent(AlbumDetailsIntent.LocaleChanged(locale)) }
        val body = when (val access = accessUi.access) {
            null -> AlbumDetailsBody.CheckingAccess
            is LibraryAccess.Required -> AlbumDetailsBody.AccessRequired(access.action)
            LibraryAccess.Granted -> ui.state.body
        }
        AlbumDetailsScreen(
            body = body,
            artworkLoader = artworkLoader,
            accessUi = accessUi,
            playbackError = ui.state.playbackError,
            onBack = onBack,
            onRetry = { ui.onIntent(AlbumDetailsIntent.Retry) },
            onPlay = { displayed, start -> ui.onIntent(AlbumDetailsIntent.Play(displayed, start)) },
            onDismissPlaybackError = { ui.onIntent(AlbumDetailsIntent.DismissPlaybackError) },
        )
    }
}
