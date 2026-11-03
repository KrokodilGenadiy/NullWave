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

/**
 * Shared gate for continuous or spatial motion, including marquee and progress interpolation.
 * Theme restrictions, a local [reduceMotion] request, and the system's zero animation scale can
 * each disable it. Observable system scale changes are read in composition and take effect live.
 * Callers own their static fallback and preview policy; reduced fades may use the system gate alone.
 */
@Composable
fun decorativeMotionEnabled(reduceMotion: Boolean = false): Boolean =
    !NullWaveTheme.effects.withRestrictions(reduceMotion = reduceMotion).reduceMotion && systemAnimationsEnabled()
