package com.zaus.nullwave.di

import android.app.Application
import android.content.Context
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.media3.service.PlaybackServiceComponent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph, PlaybackServiceComponent {
    val entryProviderInstallers: Set<EntryProviderInstaller>

    val application: Application

    val playbackController: PlaybackController

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
