package com.zaus.nullwave.core.designsystem.modifier

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

/**
 * A chamfered border with the diagonals left **unstroked** - four straight edges that stop short at
 * each cut corner, leaving an open bracket.
 *
 * ## Why this exists
 *
 * The design mock produces this look by accident: CSS `clip-path` slices the corners off an
 * already-bordered rectangle, so the cut edge has no border and the outline ends up broken. It
 * happens to 203 elements across the artboards, and nothing in the design ever draws a diagonal
 * deliberately - so it is an artifact, not a specification.
 *
 * It is also unmistakably the right look for this system, so it is reproduced here **on purpose**
 * rather than inherited. That distinction matters: `Modifier.border(width, color, shape)` strokes
 * the complete outline including the diagonals, and is still the correct choice wherever a closed
 * frame is wanted. Use this where the open bracket is the intent.
 *
 * Reads the cut sizes off [shape] rather than taking them as numbers, so the border can never
 * disagree with the background it sits on - pass the same shape to both.
 */
fun Modifier.chamferBorder(
    color: Color,
    shape: CutCornerShape,
    width: Dp = 1.dp,
): Modifier = this.drawWithCache {
    val stroke = width.toPx()
    // Half a stroke in, so the line sits inside the bounds rather than straddling the edge.
    val inset = stroke / 2f

    // CutCornerShape speaks start/end; drawing speaks left/right.
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val startCut = shape.topStart.toPx(size, this)
    val endCut = shape.topEnd.toPx(size, this)
    val bottomEndCut = shape.bottomEnd.toPx(size, this)
    val bottomStartCut = shape.bottomStart.toPx(size, this)

    val topLeft = if (isRtl) endCut else startCut
    val topRight = if (isRtl) startCut else endCut
    val bottomRight = if (isRtl) bottomStartCut else bottomEndCut
    val bottomLeft = if (isRtl) bottomEndCut else bottomStartCut

    onDrawBehind {
        val right = size.width - inset
        val bottom = size.height - inset

        // Each edge stops short by the cut at either end. Where a corner is not cut the value is 0,
        // so the two edges overlap in a stroke-by-stroke square and the joint fills itself.
        // Top
        drawLine(color, Offset(topLeft, inset), Offset(size.width - topRight, inset), stroke)
        // Right
        drawLine(color, Offset(right, topRight), Offset(right, size.height - bottomRight), stroke)
        // Bottom
        drawLine(color, Offset(size.width - bottomRight, bottom), Offset(bottomLeft, bottom), stroke)
        // Left
        drawLine(color, Offset(inset, size.height - bottomLeft), Offset(inset, topLeft), stroke)
    }
}

@Preview(name = "Chamfer border — open vs closed", showBackground = true)
@Composable
private fun ChamferBorderPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        val shape = chamfer(10.dp)

        Column(
            modifier = Modifier
                .background(colors.bg)
                .padding(NullWaveTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 140.dp, height = 48.dp)
                        .chamferBorder(colors.secondary, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("OPEN", style = NullWaveTheme.typography.button, color = colors.secondary)
                }
                Box(
                    modifier = Modifier
                        .size(width = 140.dp, height = 48.dp)
                        .border(1.dp, colors.secondary, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("CLOSED", style = NullWaveTheme.typography.button, color = colors.secondary)
                }
            }

            // All four corners cut, to check every edge shortens correctly.
            Box(
                modifier = Modifier
                    .size(width = 296.dp, height = 64.dp)
                    .chamferBorder(
                        color = colors.primary,
                        shape = chamfer(14.dp, topStart = true, bottomEnd = true),
                        width = 2.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("ALL FOUR CUT · 2dp", style = NullWaveTheme.typography.button, color = colors.primary)
            }
        }
    }
}
