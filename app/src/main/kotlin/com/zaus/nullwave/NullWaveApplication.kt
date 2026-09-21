package com.zaus.nullwave

import android.app.Application
import com.zaus.nullwave.di.AppGraph
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication

/**
 * Owns the application-wide [AppGraph].
 *
 * `by lazy` rather than building it in `onCreate()`: Android instantiates `ContentProvider`s
 * before `Application.onCreate()` runs, and MetroX's `MetroAppComponentFactory` may read
 * [appComponentProviders] at that point. A lazy is ready whenever it is first touched.
 */
class NullWaveApplication : Application(), MetroApplication {

    val appGraph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(this)
    }

    override val appComponentProviders: MetroAppComponentProviders
        get() = appGraph
}
