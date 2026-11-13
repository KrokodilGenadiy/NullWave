package com.zaus.nullwave.feature.equalizer.di

import androidx.compose.ui.res.stringResource
import com.zaus.nullwave.core.designsystem.components.layout.NullWavePlaceholderScreen
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.feature.equalizer.api.EqualizerKey
import com.zaus.nullwave.feature.equalizer.api.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@BindingContainer
@ContributesTo(AppScope::class)
object EqualizerEntries {
    @Provides
    @IntoSet
    fun entries(): EntryProviderInstaller = { menuButton ->
        entry<EqualizerKey> {
            NullWavePlaceholderScreen(
                title = stringResource(R.string.equalizer_title),
                icon = NullWaveIcons.Equalizer,
                menuButton = { menuButton(null) },
            )
        }
    }
}
