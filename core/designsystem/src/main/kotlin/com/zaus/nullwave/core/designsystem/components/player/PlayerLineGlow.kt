package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.exp

/**
 * Small, cached stroke stack approximating a soft halo for draw-time timeline geometry.
 * No bitmap is rebuilt when progress moves. Create inside drawWithCache, draw before the crisp core.
 */
internal class PlayerLineGlow(color: Color, radiusPx: Float, opacity: Float) {
    private val layers = buildList {
        var previousAlpha = 0f
        for (index in HaloLayers downTo 1) {
            val fraction = index.toFloat() / HaloLayers
            val alpha = opacity * exp(-GaussianFalloff * fraction * fraction)
            // Successive strokes overlap: compensate alpha instead of adding brightness per layer.
            val layerAlpha = (alpha - previousAlpha) / (1f - previousAlpha)
            add(Layer(color.copy(alpha = color.alpha * layerAlpha), radiusPx * fraction * 2f))
            previousAlpha = alpha
        }
    }

    fun drawLine(scope: DrawScope, start: Offset, end: Offset, coreWidth: Float) = with(scope) {
        layers.forEach { layer ->
            drawLine(layer.color, start, end, coreWidth + layer.width, StrokeCap.Round)
        }
    }

    fun drawPath(scope: DrawScope, path: Path) = with(scope) {
        layers.forEach { layer ->
            drawPath(path, layer.color, style = Stroke(layer.width, join = StrokeJoin.Round))
        }
    }

    private data class Layer(val color: Color, val width: Float)
}

private const val HaloLayers = 12
private const val GaussianFalloff = 4.5f
