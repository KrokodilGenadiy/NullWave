package com.zaus.nullwave.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * The dim layer behind a modal surface.
 *
 * Tinted with `colors.bg` rather than black: the canvas is already near-black, and reusing it keeps
 * the dimmed content reading as the same material rather than as a grey wash.
 *
 * Uses `pointerInput` instead of `clickable` on purpose - `clickable` would draw a ripple across
 * the whole screen, and a full-bleed ripple is not something this system has. Consuming the gesture
 * also stops taps reaching the content underneath, which is the other half of what a scrim is for.
 */
@Composable
fun NullWaveScrim(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = NullWaveTheme.motion
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) ScrimAlpha else 0f,
        // Same duration as the surface it dims, so the two move as one.
        animationSpec = tween(motion.drawerMillis, easing = motion.easing),
        label = "scrimAlpha",
    )

    // Fully faded out: do not sit invisibly on top of the content swallowing taps.
    if (alpha == 0f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NullWaveTheme.colors.bg.copy(alpha = alpha))
            .pointerInput(onDismiss) {
                detectTapGestures { onDismiss() }
            }
    )
}

private const val ScrimAlpha = 0.72f
