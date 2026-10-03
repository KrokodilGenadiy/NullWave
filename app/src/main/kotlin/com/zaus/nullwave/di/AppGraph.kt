package com.zaus.nullwave.di

import android.app.Application
import android.content.Context
import com.zaus.nullwave.core.di.ActivityScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

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
 *
 * Implementing [ViewModelGraph] does the same job for ViewModels: it declares the three multibinding maps
 * MetroX reads (plain, assisted, manually assisted) and exposes the `MetroViewModelFactory` built from
 * them. Features contribute with `@ContributesIntoMap(AppScope::class) @ViewModelKey(Foo::class)` and
 * nothing is named here - the same self-registration as screens and rail entries.
 */
@DependencyGraph(AppScope::class, additionalScopes = [ActivityScope::class])
interface AppGraph : MetroAppComponentProviders, ViewModelGraph {

    /** Accessor for the bound-in [Application]. Add one per type a caller needs off the graph. */
    val application: Application

    /** Anything asking for a `Context` gets the application one. */
    val applicationContext: Context

    // No accessors for the data layer on purpose. Metro only generates a provider for a binding some
    // entry point actually reaches, so until a feature injects TrackRepository there is nothing in the
    // graph - which is correct, not broken. Verified by adding an accessor temporarily: the whole chain
    // appeared (provideSqlDriverProvider, provideDatabaseProvider, provideTrackRepositoryProvider), then
    // it was removed again.

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideContext(application: Application): Context = application
}

/*
 * The database wiring that used to be sketched here now exists for real, as
 * `com.zaus.nullwave.core.data.di.DatabaseBindings` in :core:data. It is a `@ContributesTo(AppScope)`
 * binding container, so it self-registers and nothing has to be added above - which is the point. The
 * old sketch would have had :app naming the data layer, unlike every other module in the project.
 */

/*
 * The preferences wiring sketched here now exists for real, as
 * `com.zaus.nullwave.core.preferences.di.PreferencesBindings` in :core:preferences - a
 * `@ContributesTo(AppScope)` container, so it self-registers and nothing is added above. Typed accessors
 * live with their owners rather than in that module: `SettingsRepository` in :feature:settings,
 * `AudioPermissionState` in :core:data beside the scanner that needs it.
 */
