package com.zaus.nullwave.feature.library.di

import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.navigateTo
import com.zaus.nullwave.feature.about.api.AboutKey
import com.zaus.nullwave.feature.library.LibraryScreen
import com.zaus.nullwave.feature.library.api.LibraryKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@BindingContainer
@ContributesTo(AppScope::class)
object LibraryEntries {
    @Provides
    @IntoSet
    fun entries(): EntryProviderInstaller = {
        entry<LibraryKey> {
            LibraryScreen(onAboutClick = { navigateTo(AboutKey) })
        }
    }
}
