package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.media.waveform.Waveform

internal sealed class PlayerWaveform(val peaks: List<Float>?) {
    data object Loading : PlayerWaveform(null)
    data object Unavailable : PlayerWaveform(emptyList())
    data class Ready(val waveform: Waveform) : PlayerWaveform(waveform.peaks)
}
