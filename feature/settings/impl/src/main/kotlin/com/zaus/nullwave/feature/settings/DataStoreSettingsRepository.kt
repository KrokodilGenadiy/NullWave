package com.zaus.nullwave.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.feature.settings.api.SettingsRepository
import com.zaus.nullwave.feature.settings.api.UserSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] backed by DataStore, replacing the in-memory stand-in.
 *
 * Lives here rather than in `di/` - it is the implementation, not wiring. `@ContributesBinding` only
 * states which interface it satisfies, and it can be public because nothing about it needs hiding (unlike
 * `TrackRepositoryImpl`, which is `internal` to keep the generated schema types out of reach).
 *
 * Reads go through [toUserSettings], which is a pure function and therefore the part worth testing: the
 * interesting behaviour is what happens to a key that is absent or holds a value no longer in the enum.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DataStoreSettingsRepository(
    private val preferences: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = preferences.data.map { it.toUserSettings() }

    override suspend fun setThemeVariant(variant: NullWaveColorVariant) {
        // Stored by name, not ordinal. An ordinal silently remaps every user's theme the day a variant is
        // inserted in the middle of the enum; a name just fails to resolve and falls back.
        preferences.edit { it[ThemeVariantKey] = variant.name }
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        preferences.edit { it[ReduceMotionKey] = enabled }
    }

    override suspend fun setShowQualityTier(enabled: Boolean) {
        preferences.edit { it[ShowQualityTierKey] = enabled }
    }

    internal companion object {
        val ThemeVariantKey = stringPreferencesKey("theme_variant")
        val ReduceMotionKey = booleanPreferencesKey("reduce_motion")
        val ShowQualityTierKey = booleanPreferencesKey("show_quality_tier")
    }
}

/**
 * Maps stored preferences onto [UserSettings], falling back to its defaults.
 *
 * Every default comes from `UserSettings()` rather than being restated, so there is one answer to "what
 * is the default theme" and it is the data class.
 *
 * The theme variant is resolved by name and **falls back rather than throwing** if the stored string is
 * not a known variant - which happens to anyone who downgrades the app after a variant is added, or to
 * a hand-edited store. A crash on launch would be the alternative.
 */
internal fun Preferences.toUserSettings(): UserSettings {
    val defaults = UserSettings()
    return UserSettings(
        themeVariant = this[DataStoreSettingsRepository.ThemeVariantKey]
            ?.let { stored -> NullWaveColorVariant.entries.firstOrNull { it.name == stored } }
            ?: defaults.themeVariant,
        reduceMotion = this[DataStoreSettingsRepository.ReduceMotionKey] ?: defaults.reduceMotion,
        showQualityTier = this[DataStoreSettingsRepository.ShowQualityTierKey]
            ?: defaults.showQualityTier,
    )
}
