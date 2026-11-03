package com.zaus.nullwave.feature.player.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.player.api.ui.PlayerHost
import com.zaus.nullwave.feature.player.ui.PlayerRoot

/** Stateless capability; neither Activity nor ViewModel is retained by the DI graph. */
internal class DefaultPlayerHost(
    private val clock: PlaybackClock,
    private val artwork: () -> LibraryArtworkRenderer,
) : PlayerHost {
    @Composable
    override fun Content(
        owner: ViewModelStoreOwner,
        lifecycleOwner: LifecycleOwner,
        onOpenLibrary: () -> Unit,
        content: @Composable () -> Unit,
    ) = PlayerRoot(owner, lifecycleOwner, onOpenLibrary, clock, artwork, content)
}
