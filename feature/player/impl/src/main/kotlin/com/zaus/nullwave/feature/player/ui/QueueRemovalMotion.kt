package com.zaus.nullwave.feature.player.ui

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * One rectangle of the shredding row, in fractions of the row size. Each block moves on its own:
 * it starts after [delay], travels left [speed] times faster than the slowest block, drifts and
 * wobbles vertically, hops sideways on some frames, and drops out at [vanishAt].
 */
internal data class ShredBlock(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val delay: Float,
    val speed: Float,
    val drift: Float,
    val wobblePhase: Float,
    val hopAmplitude: Float,
    val vanishAt: Float,
    val salt: Int,
)

/** Where a block is drawn at one moment. Offsets are in pixels; [flash] outlines it in danger. */
internal data class ShredPose(val dx: Float, val dy: Float, val visible: Boolean, val flash: Boolean)

/**
 * Pure geometry for removing a queue row: how strongly the danger glow shows while dragging, and
 * how the "glitch shred" breaks the row apart after release. Every removal draws a fresh random
 * [shredPlan]; the same seed always gives the same plan and the same frames, so tests are stable.
 */
internal object QueueRemovalMotion {
    const val ShredMillis = 320
    const val CollapseMillis = 180
    const val RestoreMillis = 220

    /** Time buckets per shred for sideways hops and outline flashes: about one every 20 ms. */
    private const val GlitchBuckets = 16

    /** 0 at rest, 1 once the leftward drag reaches the removal threshold. */
    fun armProgress(offsetPx: Float, thresholdPx: Float): Float =
        if (thresholdPx <= 0f) 0f else (-offsetPx / thresholdPx).coerceIn(0f, 1f)

    /**
     * Cuts the row into 5–8 bands of uneven height, then each band into 1–3 blocks at random
     * points. The blocks tile the row exactly, so the first frame looks like the intact row.
     */
    fun shredPlan(random: Random): List<ShredBlock> {
        val bandCount = random.nextInt(5, 9)
        val weights = List(bandCount) { 0.6f + random.nextFloat() }
        val total = weights.sum()
        var top = 0f
        return weights.flatMapIndexed { band, weight ->
            val bottom = if (band == bandCount - 1) 1f else top + weight / total
            val cuts = List(random.nextInt(0, 3)) { 0.15f + random.nextFloat() * 0.7f }.sorted()
            val edges = listOf(0f) + cuts + 1f
            val blocks = edges.zipWithNext { left, right ->
                ShredBlock(
                    left = left, right = right, top = top, bottom = bottom,
                    delay = random.nextFloat() * 0.25f,
                    speed = 1f + random.nextFloat() * 0.6f,
                    drift = (random.nextFloat() - 0.5f) * 0.5f,
                    wobblePhase = random.nextFloat() * 2f * PI.toFloat(),
                    hopAmplitude = 0.02f + random.nextFloat() * 0.06f,
                    vanishAt = 0.55f + random.nextFloat() * 0.4f,
                    salt = random.nextInt(),
                )
            }
            top = bottom
            blocks
        }
    }

    /**
     * Pose of [block] at shred [progress]. Every block starts at the release offset [startPx] with no
     * vertical shift, and is off screen to the left or gone by the end.
     */
    fun pose(block: ShredBlock, progress: Float, startPx: Float, widthPx: Float, heightPx: Float): ShredPose {
        val p = progress.coerceIn(0f, 1f)
        if (p >= 1f || p >= block.vanishAt) return ShredPose(-widthPx, 0f, visible = false, flash = false)
        val local = ((p - block.delay) / (1f - block.delay)).coerceIn(0f, 1f)
        val travel = (local * block.speed).coerceAtMost(1f)
        var dx = startPx + (-widthPx - startPx) * travel * travel
        val wobble = sin(local * 3f * PI.toFloat() + block.wobblePhase) - sin(block.wobblePhase)
        var dy = heightPx * (block.drift * local + 0.04f * wobble)
        val bucket = (p * GlitchBuckets).toInt()
        val glitch = glitchHash(block.salt, bucket)
        if (local > 0f && glitch % 3 == 0) {
            dx += widthPx * block.hopAmplitude * (if (glitch and 8 == 0) 1 else -1)
            dy += heightPx * 0.06f * (if (glitch and 16 == 0) 1 else -1)
        }
        return ShredPose(dx, dy, visible = true, flash = local > 0f && glitch % 5 == 0)
    }

    /** Opacity of the whole shredding row; blocks also drop out on their own. */
    fun shredAlpha(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return 1f - p * p * p
    }
}
