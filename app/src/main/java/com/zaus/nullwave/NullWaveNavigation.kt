package com.zaus.nullwave

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.NavigationScope
import com.zaus.nullwave.core.navigation.navigateBack
import com.zaus.nullwave.feature.library.api.LibraryKey

@Composable
internal fun NullWaveNavigation(
    entryProviderInstallers: Set<EntryProviderInstaller>,
    navigation: NavigationScope,
) {
    val entries = remember(entryProviderInstallers, navigation) {
        with(navigation) {
            entryProvider {
                entryProviderInstallers.forEach { installer -> installer {} }
            }
        }
    }

    with(navigation) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = { navigateBack() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entries,
        )
    }
}

@Composable
internal fun rememberNullWaveNavigationScope(): NavigationScope {
    val backStack = rememberNavBackStack(LibraryKey)
    return remember(backStack) {
        object : NavigationScope {
            override val backStack = backStack
        }
    }
}
