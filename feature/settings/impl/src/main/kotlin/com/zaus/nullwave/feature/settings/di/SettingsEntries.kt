package com.zaus.nullwave.feature.settings.di

import androidx.compose.ui.res.stringResource
import com.zaus.nullwave.core.designsystem.components.layout.NullWavePlaceholderScreen
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.feature.settings.api.SettingsKey
import com.zaus.nullwave.feature.settings.api.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@BindingContainer
@ContributesTo(AppScope::class)
object SettingsEntries {
    @Provides
    @IntoSet
    fun entries(): EntryProviderInstaller = { menuButton ->
        entry<SettingsKey> {
            NullWavePlaceholderScreen(
                title = stringResource(R.string.settings_title),
                icon = NullWaveIcons.Settings,
                menuButton = { menuButton(null) },
            )
        }
    }
}
