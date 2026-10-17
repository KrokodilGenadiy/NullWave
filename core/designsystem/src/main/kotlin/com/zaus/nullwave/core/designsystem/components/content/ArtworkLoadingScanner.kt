package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import com.zaus.nullwave.core.designsystem.motion.systemAnimationsEnabled
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Decorative indeterminate artwork state. Only composed while loading; no percentage or live region. */
@Composable
internal fun ArtworkLoadingScanner(reduceMotion: Boolean) {
    val animate = !reduceMotion && systemAnimationsEnabled()
    val phase = if (animate) {
        rememberInfiniteTransition(label = "Artwork scan").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(ScanPassMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "Scan position",
        )
    } else rememberUpdatedState(0.5f)
    val colors = NullWaveTheme.colors
    val stroke = NullWaveTheme.dimens.hairline
    Spacer(Modifier.fillMaxSize().drawWithCache {
        val extent = size.minDimension
        if (extent <= 0f) return@drawWithCache onDrawBehind {}
        val edge = extent * FrameFraction
        val left = (size.width - edge) / 2f
        val top = (size.height - edge) / 2f
        val right = left + edge
        val bottom = top + edge
        val width = stroke.toPx().coerceAtLeast(1f).coerceAtMost(edge / 12f)
        val corner = edge * CornerFraction
        val lineStyle = Stroke(width)
        val corners = Path().apply {
            for (horizontal in 0..1) {
                val x = if (horizontal == 0) left else right
                val dx = if (horizontal == 0) corner else -corner
                for (vertical in 0..1) {
                    val y = if (vertical == 0) top else bottom
                    val dy = if (vertical == 0) corner else -corner
                    // Separate arms preserve the original butt-capped lines at each corner.
                    moveTo(x, y)
                    lineTo(x + dx, y)
                    moveTo(x, y)
                    lineTo(x, y + dy)
                }
            }
        }
        val inset = edge * ScanInsetFraction
        val scanLeft = left + inset
        val scanRight = right - inset
        val scanTop = top + inset
        val scanBottom = bottom - inset
        val grid = Path().apply {
            for (index in 1..GridLines) {
                val y = scanTop + (scanBottom - scanTop) * index / (GridLines + 1)
                moveTo(scanLeft, y)
                lineTo(scanRight, y)
            }
        }
        val band = edge * BandFraction
        val beam = Brush.verticalGradient(
            listOf(Color.Transparent, colors.primary.copy(alpha = BandAlpha), Color.Transparent),
            startY = -band,
            endY = band,
        )
        val beamSize = Size(scanRight - scanLeft, band * 2f)
        onDrawBehind {
            // Open corner brackets stay still; only the central scan moves.
            drawPath(corners, colors.textSecondary, style = lineStyle)
            clipRect(scanLeft, scanTop, scanRight, scanBottom) {
                drawPath(grid, colors.outline, style = lineStyle)
                // Read only during drawing; the cached brush moves with the scan line.
                val y = scanTop + width + (scanBottom - scanTop - 2f * width) * phase.value
                translate(top = y) {
                    drawRect(beam, Offset(scanLeft, -band), beamSize)
                    drawLine(colors.primary, Offset(scanLeft, 0f), Offset(scanRight, 0f), width)
                }
            }
        }
    })
}

private const val ScanPassMillis = 1400
private const val FrameFraction = 0.64f
private const val CornerFraction = 0.2f
private const val ScanInsetFraction = 0.1f
private const val BandFraction = 0.16f
private const val BandAlpha = 0.16f
private const val GridLines = 4
