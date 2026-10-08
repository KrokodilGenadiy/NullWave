package com.zaus.nullwave.feature.about.di

import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.navigateBack
import com.zaus.nullwave.feature.about.AboutScreen
import com.zaus.nullwave.feature.about.api.AboutKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@BindingContainer
@ContributesTo(AppScope::class)
object AboutEntries {
    @Provides
    @IntoSet
    fun entries(): EntryProviderInstaller = {
        entry<AboutKey> {
            AboutScreen(onBackClick = { navigateBack() })
        }
    }
}
