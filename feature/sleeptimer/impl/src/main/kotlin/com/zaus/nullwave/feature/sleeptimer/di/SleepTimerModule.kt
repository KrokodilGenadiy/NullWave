package com.zaus.nullwave.feature.sleeptimer.di

import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.di.ActivityScope
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.RailSection
import com.zaus.nullwave.core.navigation.TopLevelDestination
import com.zaus.nullwave.feature.sleeptimer.SleepTimerScreen
import com.zaus.nullwave.feature.sleeptimer.api.SleepTimerKey
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@ContributesTo(ActivityScope::class)
@BindingContainer
object SleepTimerModule {

    @IntoSet
    @Provides
    fun provideEntryProviderInstaller(): EntryProviderInstaller = {
        entry<SleepTimerKey> {
            SleepTimerScreen()
        }
    }

    @IntoSet
    @Provides
    fun provideTopLevelDestination(): TopLevelDestination = TopLevelDestination(
        key = SleepTimerKey,
        label = "Sleep",
        icon = NullWaveIcons.SleepTimer,
        section = RailSection.Tools,
    )
}
