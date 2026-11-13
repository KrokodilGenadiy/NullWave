package com.zaus.nullwave.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/** The host renders its menu button; a screen may supply an optional localized detail. */
typealias SectionMenuButton = @Composable (detail: String?) -> Unit

typealias EntryProviderInstaller = context(NavigationScope) EntryProviderScope<NavKey>.(SectionMenuButton) -> Unit
