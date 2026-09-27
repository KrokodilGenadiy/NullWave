package com.zaus.nullwave.feature.settings.di

import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.di.ActivityScope
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.RailSection
import com.zaus.nullwave.core.navigation.TopLevelDestination
import com.zaus.nullwave.feature.settings.SettingsScreen
import com.zaus.nullwave.feature.settings.api.SettingsKey
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@ContributesTo(ActivityScope::class)
@BindingContainer
object SettingsModule {

    @IntoSet
    @Provides
    fun provideEntryProviderInstaller(): EntryProviderInstaller = {
        entry<SettingsKey> {
            SettingsScreen()
        }
    }

    @IntoSet
    @Provides
    fun provideTopLevelDestination(): TopLevelDestination = TopLevelDestination(
        key = SettingsKey,
        label = "Settings",
        icon = NullWaveIcons.Settings,
        section = RailSection.System,
    )
}
