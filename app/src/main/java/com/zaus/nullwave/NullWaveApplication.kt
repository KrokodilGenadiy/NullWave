package com.zaus.nullwave

import android.app.Application
import com.zaus.nullwave.di.AppGraph
import dev.zacsweers.metro.createGraphFactory

/**
 * Owns the application-wide [AppGraph].
 *
 * The graph is built eagerly in [onCreate] so a missing binding surfaces at startup rather than on
 * whichever screen happens to request it first.
 */
class NullWaveApplication : Application() {

    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AppGraph.Factory>().create(this)
    }
}
