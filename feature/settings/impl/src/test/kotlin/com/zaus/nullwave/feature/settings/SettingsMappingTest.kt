package com.zaus.nullwave.feature.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.feature.settings.api.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stored-preferences to [UserSettings] mapping.
 *
 * No DataStore and no device: `mutablePreferencesOf` builds a `Preferences` directly, which is the only
 * input `toUserSettings` takes. The file-backed part is androidx's problem; the interesting behaviour is
 * what happens to keys that are missing or hold something unexpected.
 */
class SettingsMappingTest {

    @Test
    fun `an empty store yields the data class defaults`() {
        val settings = mutablePreferencesOf().toUserSettings()

        // Not restated literals - whatever UserSettings() says is the answer, so the default lives in
        // exactly one place.
        assertEquals(UserSettings(), settings)
    }

    @Test
    fun `stored values are read back`() {
        val stored = mutablePreferencesOf(
            DataStoreSettingsRepository.ThemeVariantKey to NullWaveColorVariant.Arasaka.name,
            DataStoreSettingsRepository.ReduceMotionKey to true,
            DataStoreSettingsRepository.ShowQualityTierKey to true,
        )

        val settings = stored.toUserSettings()

        assertEquals(NullWaveColorVariant.Arasaka, settings.themeVariant)
        assertEquals(true, settings.reduceMotion)
        assertEquals(true, settings.showQualityTier)
    }

    @Test
    fun `the theme variant is stored by name, not ordinal`() {
        // Ordinals silently remap every user's theme the day a variant is inserted mid-enum. Asserting
        // the stored form is a name is what keeps someone from "optimising" it to an Int.
        val stored = mutablePreferencesOf(
            DataStoreSettingsRepository.ThemeVariantKey to "Braindance",
        )

        assertEquals(NullWaveColorVariant.Braindance, stored.toUserSettings().themeVariant)
    }

    @Test
    fun `an unknown variant falls back instead of throwing`() {
        // Reachable by downgrading the app after a variant is added, or by a hand-edited store.
        // `valueOf` would throw here, and it would throw during the first collection of the settings
        // Flow - i.e. a crash on launch with no way out but clearing app data.
        val stored = mutablePreferencesOf(
            DataStoreSettingsRepository.ThemeVariantKey to "NotAVariantAnyMore",
        )

        assertEquals(UserSettings().themeVariant, stored.toUserSettings().themeVariant)
    }

    @Test
    fun `one missing key does not disturb the others`() {
        val stored = mutablePreferencesOf(DataStoreSettingsRepository.ReduceMotionKey to true)

        val settings = stored.toUserSettings()

        assertEquals(true, settings.reduceMotion)
        assertEquals(UserSettings().themeVariant, settings.themeVariant)
        assertEquals(UserSettings().showQualityTier, settings.showQualityTier)
    }
}
