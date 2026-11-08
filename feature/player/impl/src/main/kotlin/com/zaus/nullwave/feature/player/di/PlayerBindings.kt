package com.zaus.nullwave.feature.player.di

import androidx.lifecycle.ViewModel
import com.zaus.nullwave.core.media.waveform.WaveformSource
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.player.api.ui.PlayerHost
import com.zaus.nullwave.feature.player.presentation.PlayerViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelKey

/** Public graph contributions keep Player implementation types internal to this module. */
@BindingContainer
@ContributesTo(AppScope::class)
object PlayerBindings {
    @Provides
    private fun host(clock: PlaybackClock, artwork: () -> LibraryArtworkRenderer, waveformSource: WaveformSource): PlayerHost =
        DefaultPlayerHost(clock, artwork, waveformSource)

    // Unscoped provider: Metro constructs, the explicit ViewModelStore retains and clears.
    @Provides
    @IntoMap
    @ViewModelKey(PlayerViewModel::class)
    private fun viewModel(controller: PlaybackController): ViewModel = PlayerViewModel(controller)
}
