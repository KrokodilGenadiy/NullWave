package com.zaus.nullwave.feature.search.di

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SearchInitialLocale

@BindingContainer
@ContributesTo(AppScope::class)
object SearchPlatformBindings {
    /** Resolve per ViewModel creation so a scoped locale cannot outlive configuration changes. */
    @Provides
    @SearchInitialLocale
    private fun initialLocale(@ForScope(AppScope::class) context: Context): String =
        context.resources.configuration.locales[0].toLanguageTag()
}
