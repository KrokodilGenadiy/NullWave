package com.zaus.nullwave.core.designsystem.modifier

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp

enum class NullWaveBorderStyle {
    /** Trace the entire shape, including its diagonal edges. */
    Closed,
    /** Clip a rectangular border to the shape, leaving cut-corner diagonals unstroked. */
    ClippedCorners,
}

/**
 * Draw a border over content without changing layout, hit areas or semantics.
 * ClippedCorners reproduces the design's CSS rectangle border + clip-path. Use the same [shape]
 * as the component's background/clip; this modifier clips only the border, not its content.
 * Geometry comes from Shape.createOutline, including RTL and normalized corner sizes.
 */
fun Modifier.nullWaveBorder(
    border: BorderStroke,
    shape: Shape,
    style: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
): Modifier {
    require(border.width.value.isFinite() && border.width.value >= 0f) {
        "Border width must be finite and non-negative."
    }
    return when (style) {
        NullWaveBorderStyle.Closed -> border(border, shape)
        NullWaveBorderStyle.ClippedCorners -> drawWithCache {
            val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val width = if (border.width == Dp.Hairline) 1f else border.width.toPx()
            // The rectangle's stroke is centred on its bounds. Clip away the outer half so the
            // visible thickness is exactly width, like Foundation's inward border.
            val stroke = Stroke(width = width * 2f)
            onDrawWithContent {
                drawContent()
                if (size.width > 0f && size.height > 0f) {
                    clipPath(outline) { drawRect(brush = border.brush, style = stroke) }
                }
            }
        }
    }
}
