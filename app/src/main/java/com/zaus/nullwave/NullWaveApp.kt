package com.zaus.nullwave

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.feature.player.api.ui.PlayerHost
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/** Common ancestor of navigation and Player: the Activity owns Player; entries own their screen VMs. */
@Composable
internal fun NullWaveApp(
    entryProviderInstallers: Set<EntryProviderInstaller>,
    viewModelFactory: MetroViewModelFactory,
    playerHost: PlayerHost,
    owner: ViewModelStoreOwner,
    lifecycleOwner: LifecycleOwner,
) {
    val navigation = rememberNullWaveNavigationScope()
    CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
        Surface(Modifier.fillMaxSize(), color = NullWaveTheme.colors.bg) {
            playerHost.Content(
                owner = owner,
                lifecycleOwner = lifecycleOwner,
                onOpenLibrary = {
                    // Library is the root. Returning to it preserves its entry and permission host.
                    while (navigation.backStack.size > 1) {
                        navigation.backStack.removeAt(navigation.backStack.lastIndex)
                    }
                },
            ) {
                NullWaveNavigation(entryProviderInstallers, navigation)
            }
        }
    }
}
