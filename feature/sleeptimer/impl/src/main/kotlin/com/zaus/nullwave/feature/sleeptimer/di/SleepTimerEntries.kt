package com.zaus.nullwave.feature.sleeptimer.di

import androidx.compose.ui.res.stringResource
import com.zaus.nullwave.core.designsystem.components.layout.NullWavePlaceholderScreen
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.feature.sleeptimer.api.SleepTimerKey
import com.zaus.nullwave.feature.sleeptimer.api.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@BindingContainer
@ContributesTo(AppScope::class)
object SleepTimerEntries {
    @Provides
    @IntoSet
    fun entries(): EntryProviderInstaller = { menuButton ->
        entry<SleepTimerKey> {
            NullWavePlaceholderScreen(
                title = stringResource(R.string.sleeptimer_title),
                icon = NullWaveIcons.SleepTimer,
                menuButton = { menuButton(null) },
            )
        }
    }
}
