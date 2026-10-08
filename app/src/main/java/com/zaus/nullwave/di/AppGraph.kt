package com.zaus.nullwave.di

import android.app.Application
import android.content.Context
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides

@DependencyGraph(AppScope::class)
interface AppGraph {
    val entryProviderInstallers: Set<EntryProviderInstaller>

    val application: Application

    @ForScope(AppScope::class)
    val applicationContext: Context

    @Binds
    @ForScope(AppScope::class)
    val Application.bindApplicationContext: Context

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
