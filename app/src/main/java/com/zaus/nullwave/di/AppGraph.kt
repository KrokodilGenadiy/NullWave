package com.zaus.nullwave.di

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The application-wide Metro dependency graph.
 *
 * Metro is a compiler plugin: there is no annotation processor and no generated `Dagger*` class to
 * reference. The graph implementation is created by [dev.zacsweers.metro.createGraphFactory],
 * which is what [com.zaus.nullwave.NullWaveApplication] calls.
 *
 * Declaring `AppScope` here makes the graph an implicit `@SingleIn(AppScope::class)`, so anything
 * contributed to that scope (`@ContributesBinding(AppScope::class)`, `@ContributesTo`) is
 * aggregated automatically - no module list to maintain. A screen- or session-scoped graph hangs
 * off this one later via `@GraphExtension`.
 *
 * Adding bindings:
 * - Constructor-inject a class with `@Inject`, add `@SingleIn(AppScope::class)` if it should be a
 *   singleton, and Metro finds it - no module needed.
 * - For third-party types you do not own, write a `@Provides` function in a `@BindingContainer`
 *   and list it in `bindingContainers`.
 */
@DependencyGraph(scope = AppScope::class)
interface AppGraph {

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
