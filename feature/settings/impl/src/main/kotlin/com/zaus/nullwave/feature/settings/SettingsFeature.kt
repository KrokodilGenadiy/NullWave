package com.zaus.nullwave.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Scaffolding, not a settings screen. The design groups it as: Library & scanning, Playback,
 * Appearance, Audio, About.
 *
 * The repository that was here - `InMemorySettingsRepository` - is gone, replaced by
 * [DataStoreSettingsRepository]. Nothing that consumed `SettingsRepository` changed, which was the point
 * of putting the contract in the api module.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "SETTINGS",
            style = NullWaveTheme.typography.h1,
            color = NullWaveTheme.colors.textPrimary,
        )
    }
}
