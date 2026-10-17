package com.zaus.nullwave.core.designsystem.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** System gate only: each component still owns its reduced-motion behaviour. */
@Composable
internal fun systemAnimationsEnabled(): Boolean {
    val scope = rememberCoroutineScope()
    // Read the scale in composition so a live system setting change updates the caller.
    return (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
}

/** For continuous/scanner/marquee or spatial motion; reduced fades may use the system gate alone. */
@Composable
internal fun decorativeMotionEnabled(reduceMotion: Boolean = false): Boolean =
    !NullWaveTheme.effects.withRestrictions(reduceMotion = reduceMotion).reduceMotion && systemAnimationsEnabled()
