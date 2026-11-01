package com.zaus.nullwave.feature.player.api.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner

/** Shared player UI above navigation. Owners are explicit; the audio service has its own lifetime. */
interface PlayerHost {
    /** Owns safe-drawing/IME insets and supplies the remaining viewport to [content]. */
    @Composable
    fun Content(
        owner: ViewModelStoreOwner,
        lifecycleOwner: LifecycleOwner,
        onOpenLibrary: () -> Unit,
        content: @Composable () -> Unit,
    )
}
