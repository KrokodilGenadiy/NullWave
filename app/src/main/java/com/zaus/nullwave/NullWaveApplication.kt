package com.zaus.nullwave

import android.app.Application
import com.zaus.nullwave.core.playback.media3.service.PlaybackServiceComponent
import com.zaus.nullwave.core.playback.media3.service.PlaybackServiceComponentOwner
import com.zaus.nullwave.di.AppGraph
import com.zaus.nullwave.feature.library.data.waveform.work.WaveformWorkerComponent
import com.zaus.nullwave.feature.library.data.waveform.work.WaveformWorkerComponentOwner
import dev.zacsweers.metro.createGraphFactory

class NullWaveApplication : Application(), PlaybackServiceComponentOwner, WaveformWorkerComponentOwner {
    val appGraph: AppGraph by lazy { createGraphFactory<AppGraph.Factory>().create(this) }

    override val playbackServiceComponent: PlaybackServiceComponent get() = appGraph
    override val waveformWorkerComponent: WaveformWorkerComponent get() = appGraph.waveformWorkerComponent
}
