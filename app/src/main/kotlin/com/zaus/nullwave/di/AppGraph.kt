package com.zaus.nullwave.di

import android.app.Application
import android.content.Context
import com.zaus.nullwave.core.di.ActivityScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.android.MetroAppComponentProviders

/**
 * The application-wide Metro dependency graph.
 *
 * This lives in `:app` on purpose. Metro resolves contribution hints during FIR against the
 * compile classpath, so the graph has to see every contributing module - which `:app` does and no
 * other module should. `:core:di` holds the scopes and contracts those modules share; the graph
 * that aggregates them belongs here.
 *
 * `additionalScopes = [ActivityScope::class]` pulls in the feature modules' `@ContributesTo`
 * binding containers, including their `EntryProviderInstaller` set contributions.
 *
 * Implementing [MetroAppComponentProviders] is what lets MetroX's `MetroAppComponentFactory`
 * construct `MainActivity` with constructor injection.
 */
@DependencyGraph(AppScope::class, additionalScopes = [ActivityScope::class])
interface AppGraph : MetroAppComponentProviders {

    /** Accessor for the bound-in [Application]. Add one per type a caller needs off the graph. */
    val application: Application

    /** Anything asking for a `Context` gets the application one. */
    val applicationContext: Context

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideContext(application: Application): Context = application
}

/*
 * Once a .sq file exists and NullWaveDatabase is generated, the database wiring looks like this.
 * Add `DatabaseBindings::class` to `@DependencyGraph(bindingContainers = [...])` above to install it.
 *
 * @BindingContainer
 * object DatabaseBindings {
 *
 *     @Provides
 *     @SingleIn(AppScope::class)
 *     fun provideSqlDriver(context: Context): SqlDriver =
 *         AndroidSqliteDriver(
 *             schema = NullWaveDatabase.Schema,
 *             context = context,
 *             name = "nullwave.db",
 *         )
 *
 *     @Provides
 *     @SingleIn(AppScope::class)
 *     fun provideDatabase(driver: SqlDriver): NullWaveDatabase = NullWaveDatabase(driver)
 * }
 */

/*
 * Preferences DataStore holds the Settings screen's state - theme variant, reduceMotion, "show
 * quality tier", sleep-timer default, EQ preset. One instance per file, application-scoped: a
 * second DataStore on the same file in the same process throws.
 *
 * @BindingContainer
 * object PreferencesBindings {
 *
 *     @Provides
 *     @SingleIn(AppScope::class)
 *     fun provideSettingsStore(context: Context): DataStore<Preferences> =
 *         PreferenceDataStoreFactory.create(
 *             produceFile = { context.preferencesDataStoreFile("settings") },
 *         )
 * }
 */
