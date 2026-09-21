package com.zaus.nullwave.core.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * How a feature module registers its screens with the host `NavDisplay` without the host ever
 * importing the feature's composables.
 *
 * A feature contributes one of these into a set:
 * ```
 * @ContributesIntoSet(ActivityScope::class)
 * @Provides
 * fun libraryEntries(): EntryProviderInstaller = {
 *     entry<LibraryKey> { LibraryScreen() }
 * }
 * ```
 *
 * `MainActivity` injects `Set<EntryProviderInstaller>` and invokes each one inside
 * `entryProvider { ... }`. Adding a feature then means adding a module dependency and nothing
 * else - no `when` block in the host to edit.
 */
typealias EntryProviderInstaller = EntryProviderScope<NavKey>.() -> Unit

/**
 * Typed to [NavKey], not to a shared sealed key type: each feature declares its own keys in its
 * api module now, so there is no single type they all share. `NavDisplay` takes a plain
 * `List<NavKey>`, so [backStack] can be passed to it directly.
 */
class AppNavigator(startDestination: NavKey) {
    // В Nav3 бэкстек — это контролируемый вами стейт-лист
    val backStack = mutableStateListOf(startDestination)

    fun navigateTo(key: NavKey) {
        if (backStack.lastOrNull() != key) {
            backStack.add(key)
        }
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun switchToTopLevel(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }
}
