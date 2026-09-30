package com.zaus.nullwave.core.designsystem.modifier

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

/** Which corner a tick is drawn in. Start/End resolve against layout direction. */
enum class TickCorner {
    TopStart,
    TopEnd,
    BottomStart,
    BottomEnd,
}

/**
 * The two corners the house chamfer does NOT cut.
 *
 * `chamfer()` cuts top-end and bottom-start, so ticking the other two puts a hard mark on each square
 * corner and leaves the cut ones clean. Pass an explicit set for anything else.
 */
val SquareCorners: Set<TickCorner> = setOf(TickCorner.TopStart, TickCorner.BottomEnd)

/**
 * Short bright L-shaped marks in the corners of a container.
 *
 * A plain extension function, not a composable one - so it allocates nothing per recomposition and can
 * be hoisted into a `val` if a caller wants to. The colour is a parameter rather than being read from
 * the theme for the same reason: reading `NullWaveTheme` would force this to be `@Composable`.
 *
 * Draw-phase only. It changes no layout, so adding or removing ticks never moves the content.
 *
 * **Design rule:** emphasised containers only. If everything has ticks they stop meaning anything.
 *
 * ## The inset is large on purpose
 *
 * The artboards place ticks 10-16px inside the edge, with 14-16px arms. That distance is what makes
 * them read as a deliberate inner mark. A tick sitting a pixel or two off the border instead looks
 * like it was *trying* to trace the border and missed - and on a chamfered container it collides
 * with the 45-degree cut, so the L hangs in space with nothing to connect to. If ticks look wrong,
 * the inset is almost always too small.
 *
 * @param inset distance from the node's edge. See above; `dimens.cornerTickInset` is the token.
 */
fun Modifier.cornerTicks(
    color: Color,
    corners: Set<TickCorner> = SquareCorners,
    length: Dp = 14.dp,
    strokeWidth: Dp = 2.dp,
    inset: Dp = 12.dp,
): Modifier = this.drawBehind {
    // DrawScope works in pixels; toPx() is available here because DrawScope is a Density.
    val stroke = strokeWidth.toPx()
    val len = length.toPx()
    // Half a stroke keeps the line's center inside the inset, so nothing is clipped at the edge.
    val pad = inset.toPx() + stroke / 2f

    val left = pad
    val right = size.width - pad
    val top = pad
    val bottom = size.height - pad

    val isRtl = layoutDirection == LayoutDirection.Rtl

    corners.forEach { corner ->
        val atStart = corner == TickCorner.TopStart || corner == TickCorner.BottomStart
        val atTop = corner == TickCorner.TopStart || corner == TickCorner.TopEnd

        // "start" is the left edge in LTR and the right edge in RTL.
        val atLeft = atStart != isRtl

        val x = if (atLeft) left else right
        val y = if (atTop) top else bottom
        // Both arms run inwards, away from their corner.
        val dx = if (atLeft) len else -len
        val dy = if (atTop) len else -len

        // Two arms from the same origin. They overlap in a stroke-by-stroke square at the corner,
        // which is what fills the joint - no cap trickery needed.
        drawLine(color, Offset(x, y), Offset(x + dx, y), strokeWidth = stroke)
        drawLine(color, Offset(x, y), Offset(x, y + dy), strokeWidth = stroke)
    }
}

// No widthDp/heightDp: a fixed size pins the render surface, and Studio's floating preview toolbar
// then sits on top of it. Without them the preview wraps its content and the toolbar clears it.
@Preview(name = "Corner ticks", showBackground = true)
@Composable
private fun CornerTicksPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        val shape = chamfer(12.dp)

        Column(
            modifier = Modifier
                .background(colors.bg)
                .padding(NullWaveTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
        ) {
            // Default: ticks on the two corners the chamfer leaves square.
            Box(
                modifier = Modifier
                    .size(width = 220.dp, height = 88.dp)
                    .background(colors.surface, shape)
                    .border(1.dp, colors.outline, shape)
                    .cornerTicks(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("SQUARE CORNERS", style = NullWaveTheme.typography.micro, color = colors.textSecondary)
            }

            // All four, to see the shape clearly while developing.
            Box(
                modifier = Modifier
                    .size(width = 220.dp, height = 88.dp)
                    .background(colors.surface, shape)
                    .border(1.dp, colors.outline, shape)
                    .cornerTicks(colors.secondary, corners = TickCorner.entries.toSet()),
                contentAlignment = Alignment.Center,
            ) {
                Text("ALL FOUR", style = NullWaveTheme.typography.micro, color = colors.textSecondary)
            }

            // What "too small an inset" looks like, kept as a counter-example: the tick lands just
            // off the border and reads as a misalignment rather than a mark.
            Box(
                modifier = Modifier
                    .size(width = 220.dp, height = 88.dp)
                    .background(colors.surfaceRaised, shape)
                    .border(1.dp, colors.outline, shape)
                    .cornerTicks(colors.danger, inset = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("INSET 1dp — WRONG", style = NullWaveTheme.typography.micro, color = colors.textSecondary)
            }
        }
    }
}
