package com.zaus.nullwave.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zaus.nullwave.core.designsystem.motion.decorativeMotionEnabled
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.feature.player.presentation.PlayerState
import com.zaus.nullwave.feature.player.presentation.progressFractionAt

/** Frame ticks only invalidate the DS strip's draw phase; no frame intents or metadata reads. */
@Composable
internal fun rememberPlayerProgress(
    state: PlayerState,
    clock: PlaybackClock,
    lifecycleOwner: LifecycleOwner,
): () -> Float? {
    val smooth = !LocalInspectionMode.current && decorativeMotionEnabled()
    val latestState = rememberUpdatedState(state)
    val smoothState = rememberUpdatedState(smooth)
    val now = remember(clock) { mutableLongStateOf(clock.nowMs()) }
    val snapshot = state.liveSnapshot
    // A control-only generation change must also cancel the old anchor's frame loop.
    val anchor = state.progress?.takeIf { snapshot != null && it.matches(snapshot) }
    val playing = snapshot?.transport?.isPlaying == true
    LaunchedEffect(clock, lifecycleOwner, anchor, playing, smooth) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            now.longValue = clock.nowMs()
            if (smooth && playing && anchor?.advancing == true && state.progressFractionAt(now.longValue) != null) {
                // Reuse the API's default extrapolation bound, including duration/speed clamps.
                val limit = state.positionAt(Long.MAX_VALUE)
                while (state.positionAt(now.longValue) != limit) {
                    withFrameNanos { now.longValue = clock.nowMs() }
                }
            }
        }
    }
    return remember(clock) {
        {
            val current = latestState.value
            current.progressFractionAt(if (smoothState.value) now.longValue else current.progress?.sampledAtMs ?: 0L)
        }
    }
}
