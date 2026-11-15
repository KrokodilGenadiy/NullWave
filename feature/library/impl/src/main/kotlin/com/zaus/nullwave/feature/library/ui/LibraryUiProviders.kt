package com.zaus.nullwave.feature.library.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.core.media.model.ArtworkRef
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessHost
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessUi
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.platform.AndroidAudioPermission
import com.zaus.nullwave.feature.library.platform.AudioPermission

internal class AndroidLibraryAccessHost(
    private val permission: @Composable () -> AudioPermission = { rememberAudioPermission() },
) : LibraryAccessHost {
    @Composable
    override fun Content(onAccessChecked: (LibraryAccess) -> Unit, content: @Composable (LibraryAccessUi) -> Unit) {
        LibraryPermissionHost(permission(), onAccessChecked, content)
    }
}

internal class AndroidLibraryArtworkRenderer(private val loader: LibraryArtworkLoader) : LibraryArtworkRenderer {
    @Composable
    override fun Artwork(artwork: ArtworkRef?, title: String, size: Dp, modifier: Modifier) {
        LibraryArtwork(artwork, title, loader, modifier, size)
    }
}

@Composable
internal fun rememberAudioPermission(): AudioPermission {
    val activity = checkNotNull(LocalActivity.current) { "Audio permission requires an Activity host" }
    return remember(activity) { AndroidAudioPermission(activity) }
}
