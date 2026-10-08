package com.zaus.nullwave.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/** Features register entries; the host supplies navigation and owns its state. */
typealias EntryProviderInstaller = context(NavigationScope) EntryProviderScope<NavKey>.() -> Unit
