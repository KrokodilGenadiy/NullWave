package com.zaus.nullwave.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.settings.api.SettingsRepository
import com.zaus.nullwave.feature.settings.api.UserSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Scaffolding, not a settings screen. The design groups it as: Library & scanning, Playback,
 * Appearance, Audio, About.
 *
 * [InMemorySettingsRepository] stays here rather than in `di/` - it is the implementation itself,
 * not DI wiring. `@ContributesBinding` only says which interface it satisfies. Swap it for a
 * DataStore-backed version (already on this module's classpath) without touching a caller.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class InMemorySettingsRepository : SettingsRepository {

    private val state = MutableStateFlow(UserSettings())
    override val settings: Flow<UserSettings> = state

    override suspend fun setThemeVariant(variant: NullWaveColorVariant) {
        state.update { it.copy(themeVariant = variant) }
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        state.update { it.copy(reduceMotion = enabled) }
    }

    override suspend fun setShowQualityTier(enabled: Boolean) {
        state.update { it.copy(showQualityTier = enabled) }
    }
}

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
