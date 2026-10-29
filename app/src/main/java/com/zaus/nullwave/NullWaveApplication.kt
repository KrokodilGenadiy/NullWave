package com.zaus.nullwave

import android.app.Application
import com.zaus.nullwave.di.AppGraph
import com.zaus.nullwave.core.playback.media3.PlaybackServiceComponent
import com.zaus.nullwave.core.playback.media3.PlaybackServiceComponentOwner
import dev.zacsweers.metro.createGraphFactory

class NullWaveApplication : Application(), PlaybackServiceComponentOwner {
    lateinit var appGraph: AppGraph
        private set

    override val playbackServiceComponent: PlaybackServiceComponent get() = appGraph

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AppGraph.Factory>().create(this)
    }
}
