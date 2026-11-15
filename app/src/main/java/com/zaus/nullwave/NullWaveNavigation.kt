package com.zaus.nullwave

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.NavigationScope
import com.zaus.nullwave.core.navigation.SectionMenuButton
import com.zaus.nullwave.core.navigation.navigateBack
import com.zaus.nullwave.feature.library.api.LibraryKey

@Composable
internal fun NullWaveNavigation(
    entryProviderInstallers: Set<EntryProviderInstaller>,
    navigation: NavigationScope,
) {
    val stack = navigation.backStack
    // Library stays decorated while hidden so its entry keeps its VM, tabs and scroll state.
    val sectionIndex = stack.indexOfLast { key -> menuDestinations.any { it.key == key } }.coerceAtLeast(0)
    val section = stack[sectionIndex]
    val provider = remember(entryProviderInstallers, navigation) {
        val menu: SectionMenuButton = { detail ->
            SectionMenu(current = stack.last(), detail = detail, onMenuDestination = navigation::selectSection)
        }
        with(navigation) {
            entryProvider {
                entryProviderInstallers.forEach { installer -> installer(menu) }
            }
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = stack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = provider,
    )
    key(section) {
        val displayedKey = stack.last()
        NavDisplay(
            entries = entries.drop(sectionIndex),
            modifier = Modifier.fillMaxSize(),
            onBack = { with(navigation) { navigateBack(displayedKey) } },
        )
    }
}

internal fun NavigationScope.selectSection(key: NavKey) {
    if (backStack.last() == key) return
    backStack.subList(1, backStack.size).clear()
    if (key != LibraryKey) backStack.add(key)
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
