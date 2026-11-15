package com.zaus.nullwave.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** Owned by the host's composition, never by the application DI graph. */
interface NavigationScope {
    val backStack: NavBackStack<NavKey>
}

/** Ignore a second tap on the destination already on top. */
context(nav: NavigationScope)
fun navigateTo(key: NavKey) {
    if (nav.backStack.lastOrNull() != key) nav.backStack.add(key)
}

/** A callback from an outgoing screen can only pop its own destination. The Activity owns Back at the root. */
context(nav: NavigationScope)
fun navigateBack(from: NavKey) {
    if (nav.backStack.size > 1 && nav.backStack.last() == from) {
        nav.backStack.removeAt(nav.backStack.lastIndex)
    }
}
