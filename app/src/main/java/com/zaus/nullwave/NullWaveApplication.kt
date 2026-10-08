package com.zaus.nullwave

import android.app.Application
import com.zaus.nullwave.di.AppGraph
import dev.zacsweers.metro.createGraphFactory

class NullWaveApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AppGraph.Factory>().create(this)
    }
}
