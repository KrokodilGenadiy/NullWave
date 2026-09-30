package com.zaus.nullwave.core.designsystem.modifier

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.OctagonShape
import com.zaus.nullwave.core.designsystem.theme.chamfer
import kotlin.math.roundToInt

/**
 * A soft outer glow around [shape], drawn outside the node's bounds.
 *
 * The perk tree's unlocked-node treatment. **Design rule: the one currently active element only** -
 * the playing track, the touched EQ band, the selected tab. Never a whole container, never more than
 * one thing on screen at a time, or it stops meaning "this one".
 *
 * Conditional by convention rather than by parameter, same as [cornerTicks]:
 * ```
 * Modifier.then(if (isPlaying) Modifier.activeGlow(colors.primary, shapes.node) else Modifier)
 * ```
 *
 * ## How it works, and why not the obvious things
 *
 * There is no real blur here. `Modifier.blur` needs API 31 (minSdk is 29), `Modifier.shadow` draws a
 * shadow with a downward offset — elevation, which this system does not have — and `BlurMaskFilter`
 * means dropping to the native canvas with patchy hardware-acceleration support.
 *
 * Instead this strokes the shape's own outline several times: widest and faintest first, narrowest
 * and brightest last. The overlap approximates a falloff, and because it strokes the **actual
 * outline** the halo follows an octagon or a chamfer properly, which a radial gradient would not.
 *
 * `drawWithCache` rather than `drawBehind` because the `Path` is an allocation: built once per size
 * change instead of once per frame.
 *
 * ## The catch
 *
 * The glow is drawn *outside* `size`, so **any ancestor that clips will cut it off** - a
 * `Modifier.clip`, a `graphicsLayer { clip = true }`, or `clipToBounds()`. If a glow does not appear,
 * that is almost always why. Leave at least [radius] of padding around the glowing element too, or
 * the halo lands under its neighbour.
 *
 * ## Two roles, not one
 *
 * The design uses this at two very different intensities, and mixing them up is what makes a screen
 * look like everything is shouting:
 *
 * - **Activation** - `dimens.glowRadius` (6dp) at ~0.40 alpha. Tight and bright. The one active
 *   element: the touched EQ band, the playhead, the selected tab.
 * - **Emphasis wash** - `dimens.glowWashRadius` (10dp) at ~0.08 alpha, always alongside the 3dp
 *   accent spine. Wide and almost invisible. Selected rows.
 *
 * @param radius how far the halo extends *outward*. Note this is not the design's CSS blur number:
 *   a CSS blur transitions across its radius centred on the edge, so roughly half of it spreads
 *   outward and `0 0 8px` lands near 6dp here. Passed rather than read from the theme so this stays
 *   a plain, non-composable modifier.
 * @param alpha peak opacity at the shape's edge, falling to zero at [radius].
 */
fun Modifier.activeGlow(
    color: Color,
    shape: Shape = RectangleShape,
    radius: Dp = 6.dp,
    alpha: Float = 0.40f,
): Modifier = this.drawWithCache {
    // Built once per size/shape change, not per frame — that is what drawWithCache buys.
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val radiusPx = radius.toPx()

    // One pass roughly every 1.5px rather than a fixed count. A fixed count bands badly at large
    // radii and wastes passes at small ones; deriving it keeps every step sub-perceptual.
    //
    // The upper bound also guards a quantisation trap: per-pass alpha is `2 * alpha / steps`, and
    // colour channels are 8-bit, so a faint glow split across too many passes rounds each one
    // towards zero and the whole thing disappears. Never let a pass fall below ~1.5/255.
    val alphaBudgetSteps = (2f * alpha / MinPerStepAlpha).roundToInt()
    val steps = (radiusPx / StepSpacingPx)
        .roundToInt()
        .coerceAtMost(alphaBudgetSteps)
        .coerceIn(MinSteps, MaxSteps)

    // Each pass is a band from the outline out to its own half-width, so they nest rather than
    // tile — coverage at a given distance is everything wide enough to reach it. That means the
    // per-pass alpha has to scale DOWN as the count goes up, or more passes would just mean a
    // brighter glow. 2/steps makes the accumulation land on `alpha` at the edge whatever `steps` is.
    val perStep = 2f * alpha / steps

    onDrawBehind {
        // Widest first so the narrow bright passes paint over them.
        for (index in steps - 1 downTo 0) {
            // 1/(steps+1) .. steps/(steps+1) — never 0 (draws nothing) and never 1 (fully faded).
            val fraction = (index + 1f) / (steps + 1f)
            drawPath(
                path = path,
                // Linear per-pass falloff integrates to a quadratic one, which reads as a soft
                // shoulder rather than a hard edge.
                color = color.copy(alpha = (perStep * (1f - fraction)).coerceIn(0f, 1f)),
                // Stroke is centred on the outline, so half of each pass falls outside the shape.
                // The inner half is hidden behind the element's own fill.
                style = Stroke(
                    width = radiusPx * 2f * fraction,
                    // Round, not the default miter: a mitred join spikes outwards at the chamfer's
                    // 45-degree corners, and those spikes get longer with every wider pass.
                    join = StrokeJoin.Round,
                    cap = StrokeCap.Round,
                ),
            )
        }
    }
}

private const val StepSpacingPx = 1.5f
private const val MinSteps = 6
private const val MaxSteps = 48

/** ~1.5/255: below this a pass rounds away to nothing in an 8-bit colour channel. */
private const val MinPerStepAlpha = 0.006f

@Preview(name = "Active glow", showBackground = true)
@Composable
private fun ActiveGlowPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        val dimens = NullWaveTheme.dimens
        val rowShape = chamfer(12.dp)

        Column(
            modifier = Modifier
                .background(colors.bg)
                // Generous padding: the halo draws outside its element and would otherwise be
                // clipped by the preview surface.
                .padding(NullWaveTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xl),
        ) {
            // The canonical use: a perk-tree node. Inactive beside active for comparison.
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(dimens.sliderNode)
                        .background(colors.surface, OctagonShape)
                        .border(1.5.dp, colors.outline, OctagonShape),
                )
                Box(
                    modifier = Modifier
                        .size(dimens.sliderNode)
                        .activeGlow(colors.primary, OctagonShape, radius = dimens.glowRadius)
                        .background(colors.surface, OctagonShape)
                        .border(1.5.dp, colors.primary, OctagonShape),
                )
                Text(
                    "NODE — IDLE / ACTIVE",
                    style = NullWaveTheme.typography.micro,
                    color = colors.textTertiary,
                )
            }

            // The emphasis wash, shown the way the design actually uses it: never alone, always
            // with the 3dp accent spine. At 0.08 the wash is meant to be barely perceptible - the
            // spine is the part you read as "selected", the wash just lifts the row off the canvas.
            // Rendered on its own it looks like nothing, which is the point, not a bug.
            Box(
                modifier = Modifier
                    .size(width = 220.dp, height = dimens.rowHeight)
                    .activeGlow(
                        color = colors.primary,
                        shape = rowShape,
                        radius = dimens.glowWashRadius,
                        alpha = 0.08f,
                    )
                    .background(colors.surfaceRaised, rowShape)
                    // After background, so it paints over it: the inset 3dp spine.
                    .drawBehind {
                        drawRect(
                            color = colors.primary,
                            size = Size(dimens.accentSpine.toPx(), size.height),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("SELECTED ROW — SPINE + WASH", style = NullWaveTheme.typography.micro, color = colors.primary)
            }

            // Secondary colour, tighter radius — the tab-indicator case.
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 1.dp)
                    .activeGlow(colors.secondary, RectangleShape, radius = 8.dp, alpha = 0.5f)
                    .background(colors.secondary),
            )
        }
    }
}
