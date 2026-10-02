package com.zaus.nullwave.core.preferences.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The app's one preferences store, contributed to the application graph.
 *
 * `@SingleIn(AppScope::class)` is not a habit here, it is a requirement: **a second `DataStore` over the
 * same file in the same process throws.** Everything that keeps a preference shares this instance, which
 * is also why there is one file rather than one per feature.
 *
 * Self-registering via `@ContributesTo`, like `DatabaseBindings` - nothing outside this module names it,
 * and the sketch that used to sit commented in `:app`'s `AppGraph` is gone.
 *
 * ## What is deliberately not here
 *
 * No typed accessors. This module provides the mechanism and stops. `SettingsRepository` lives in
 * `:feature:settings`, the audio-permission flag in `:core:data` next to the scanner that cares about it.
 * Keeping them with their owners is what stops this module turning into a bag of unrelated keys that
 * every feature depends on for one value.
 *
 * If a second store is ever needed - a cache with different durability, say - `DataStore<Preferences>`
 * becomes ambiguous in the graph and both will need a qualifier. One is enough for now.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object PreferencesBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserPreferences(context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(FileName) },
        )

    /**
     * Not "settings": the store holds app state too, like whether the audio permission has ever been
     * requested, which is not something a user sets.
     */
    private const val FileName = "user_preferences"
}
