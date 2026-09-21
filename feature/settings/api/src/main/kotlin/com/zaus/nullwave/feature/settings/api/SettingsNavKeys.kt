package com.zaus.nullwave.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Settings, grouped as: Library & scanning, Playback, Appearance, Audio, About. */
@Serializable
data object SettingsKey : NavKey
