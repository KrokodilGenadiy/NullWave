package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Decorative indeterminate artwork state. Only composed while loading; no percentage or live region. */
@Composable
internal fun ArtworkLoadingScanner(reduceMotion: Boolean) {
    val scope = rememberCoroutineScope()
    val animate = !reduceMotion && (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    val phase = if (animate) {
        rememberInfiniteTransition(label = "Artwork scan").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(ScanPassMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "Scan position",
        )
    } else rememberUpdatedState(0.5f)
    val colors = NullWaveTheme.colors
    val stroke = NullWaveTheme.dimens.hairline
    Canvas(Modifier.fillMaxSize()) {
        val extent = size.minDimension
        if (extent <= 0f) return@Canvas
        val edge = extent * FrameFraction
        val left = (size.width - edge) / 2f
        val top = (size.height - edge) / 2f
        val right = left + edge
        val bottom = top + edge
        val width = stroke.toPx().coerceAtLeast(1f).coerceAtMost(edge / 12f)
        val corner = edge * CornerFraction
        // Open corner brackets stay still; only the central scan moves.
        for (x in listOf(left, right)) {
            for (y in listOf(top, bottom)) {
                val dx = if (x == left) corner else -corner
                val dy = if (y == top) corner else -corner
                drawLine(colors.textSecondary, Offset(x, y), Offset(x + dx, y), width)
                drawLine(colors.textSecondary, Offset(x, y), Offset(x, y + dy), width)
            }
        }
        val inset = edge * ScanInsetFraction
        val scanLeft = left + inset
        val scanRight = right - inset
        val scanTop = top + inset
        val scanBottom = bottom - inset
        clipRect(scanLeft, scanTop, scanRight, scanBottom) {
            for (index in 1..GridLines) {
                val y = scanTop + (scanBottom - scanTop) * index / (GridLines + 1)
                drawLine(colors.outline, Offset(scanLeft, y), Offset(scanRight, y), width)
            }
            // Read animation state in draw, keeping the surrounding card out of per-frame recomposition.
            val y = scanTop + width + (scanBottom - scanTop - 2f * width) * phase.value
            val band = edge * BandFraction
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, colors.primary.copy(alpha = BandAlpha), Color.Transparent), y - band, y + band),
                Offset(scanLeft, y - band), Size(scanRight - scanLeft, band * 2f),
            )
            drawLine(colors.primary, Offset(scanLeft, y), Offset(scanRight, y), width)
        }
    }
}

private const val ScanPassMillis = 1400
private const val FrameFraction = 0.64f
private const val CornerFraction = 0.2f
private const val ScanInsetFraction = 0.1f
private const val BandFraction = 0.16f
private const val BandAlpha = 0.16f
private const val GridLines = 4
