package com.zaus.nullwave.feature.equalizer.di

import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.di.ActivityScope
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.RailSection
import com.zaus.nullwave.core.navigation.TopLevelDestination
import com.zaus.nullwave.feature.equalizer.EqualizerScreen
import com.zaus.nullwave.feature.equalizer.api.EqualizerKey
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@ContributesTo(ActivityScope::class)
@BindingContainer
object EqualizerModule {

    @IntoSet
    @Provides
    fun provideEntryProviderInstaller(): EntryProviderInstaller = {
        entry<EqualizerKey> {
            EqualizerScreen()
        }
    }

    @IntoSet
    @Provides
    fun provideTopLevelDestination(): TopLevelDestination = TopLevelDestination(
        key = EqualizerKey,
        label = "Equalizer",
        icon = NullWaveIcons.Equalizer,
        section = RailSection.Tools,
    )
}
