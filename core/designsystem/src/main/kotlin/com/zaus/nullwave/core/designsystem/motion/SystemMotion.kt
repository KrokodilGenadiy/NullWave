package com.zaus.nullwave.core.designsystem.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale

/** System gate only: each component still owns its reduced-motion behaviour. */
@Composable
internal fun systemAnimationsEnabled(): Boolean {
    val scope = rememberCoroutineScope()
    // Read the scale in composition so a live system setting change updates the caller.
    return (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
}
