package com.zaus.nullwave.feature.settings.api

import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import kotlinx.coroutines.flow.Flow

/**
 * The settings every other part of the app reads.
 *
 * Only the values that cross a module boundary belong here. Settings that the Settings screen owns
 * and nobody else reads - scan folders, "rescan on start", the about blurb - stay in
 * `:feature:settings:impl`.
 */
data class UserSettings(
    val themeVariant: NullWaveColorVariant = NullWaveColorVariant.Cyberpunk,
    /** Disables the glitch and scanline sweep. See `NullWaveMotion.reduceMotion`. */
    val reduceMotion: Boolean = false,
    /** Draws the 3dp quality-tier stripe on track rows. */
    val showQualityTier: Boolean = false,
)

/**
 * Read/write access to [UserSettings].
 *
 * `:app` collects [settings] to pick the theme variant it hands `NullWaveTheme`, which is why this
 * contract lives in an api module rather than inside the Settings feature.
 */
interface SettingsRepository {

    val settings: Flow<UserSettings>

    suspend fun setThemeVariant(variant: NullWaveColorVariant)

    suspend fun setReduceMotion(enabled: Boolean)

    suspend fun setShowQualityTier(enabled: Boolean)
}
