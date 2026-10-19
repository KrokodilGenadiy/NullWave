package com.zaus.nullwave

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.NavigationScope
import com.zaus.nullwave.core.navigation.navigateBack
import com.zaus.nullwave.feature.library.api.LibraryKey
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

@Composable
internal fun NullWaveNavigation(
    entryProviderInstallers: Set<EntryProviderInstaller>,
    viewModelFactory: MetroViewModelFactory,
) {
    val backStack = rememberNavBackStack(LibraryKey)
    val navigation = remember(backStack) {
        object : NavigationScope {
            override val backStack = backStack
        }
    }
    val entries = remember(entryProviderInstallers, navigation) {
        with(navigation) {
            entryProvider {
                entryProviderInstallers.forEach { installer -> installer() }
            }
        }
    }

    CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
        Surface(modifier = Modifier.fillMaxSize(), color = NullWaveTheme.colors.bg) {
            with(navigation) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    onBack = { navigateBack() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entries,
                )
            }
        }
    }
}
