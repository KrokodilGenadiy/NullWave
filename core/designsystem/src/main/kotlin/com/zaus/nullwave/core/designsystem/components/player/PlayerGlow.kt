package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Component-local halo tokens. Static decoration; no elevation, layout or semantics changes. */
internal enum class PlayerGlow(val radius: Dp, val alpha: Float) {
    Hero(28.dp, 0.45f),
    Compact(12.dp, 0.35f),
}

/** Apply before the component's clip/background. A clipping ancestor can still trim the halo. */
internal fun Modifier.playerGlow(
    style: PlayerGlow,
    shape: Shape,
    color: Color,
    enabled: Boolean,
): Modifier = if (enabled) {
    dropShadow(shape, Shadow(radius = style.radius, color = color, alpha = style.alpha))
} else this
