package com.zaus.nullwave.feature.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import com.zaus.nullwave.core.mvi.ui.collectMviStateWithLifecycle
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.platform.AudioPermission
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
internal fun LibraryRoot(
    onAboutClick: () -> Unit,
    artworkLoader: LibraryArtworkLoader?,
    viewModel: LibraryViewModel = metroViewModel(),
    permission: AudioPermission = rememberAudioPermission(),
    onSearchClick: () -> Unit = {},
) {
    val state by viewModel.collectMviStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(viewModel, localeTag) { viewModel.onIntent(LibraryIntent.LocaleChanged(localeTag)) }
    LibraryPermissionHost(permission, { viewModel.onIntent(LibraryIntent.AccessChecked(it)) }) { accessUi ->
        LibraryScreen(state, viewModel::onIntent, onAboutClick, accessUi, artworkLoader, onSearchClick)
    }
}
