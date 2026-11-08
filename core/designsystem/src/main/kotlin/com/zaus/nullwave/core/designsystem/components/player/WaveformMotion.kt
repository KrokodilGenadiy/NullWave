package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.zaus.nullwave.core.designsystem.motion.decorativeMotionEnabled
import kotlinx.coroutines.delay

internal class WaveformMotion(val scanPhase: State<Float>?, val barHeightScale: State<Float>?)

@Composable
internal fun rememberWaveformMotion(peaks: List<Float>?): WaveformMotion {
    val showScan = if (peaks == null) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(200)
            visible = true
        }
        visible
    } else false
    val animate = decorativeMotionEnabled()
    val scanPhase = when {
        !showScan -> null
        !animate -> rememberUpdatedState(0.5f)
        else -> rememberInfiniteTransition(label = "Waveform scan").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
            label = "Scan position",
        )
    }
    // Start short only when a scan was visible. Cached peaks enter at full height.
    val barHeightScale = if (animate && (showScan || !peaks.isNullOrEmpty())) {
        animateFloatAsState(
            targetValue = if (showScan) 0.12f else 1f,
            animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing),
            label = "Waveform bar height",
        )
    } else null
    return remember(scanPhase, barHeightScale) { WaveformMotion(scanPhase, barHeightScale) }
}
