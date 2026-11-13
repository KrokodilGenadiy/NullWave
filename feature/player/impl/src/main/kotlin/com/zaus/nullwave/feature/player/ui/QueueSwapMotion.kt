package com.zaus.nullwave.feature.player.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import kotlin.random.Random

/** One horizontal strip of a row that is materializing after a move, in fractions of the row height. */
internal data class SwapSlice(val top: Float, val bottom: Float, val offset: Float, val salt: Int)

/** Where a strip is drawn at one moment: [dx] in pixels, [flash] outlines it in the accent colour. */
internal data class SwapSlicePose(val dx: Float, val flash: Boolean)

/**
 * Pure timeline for the "glitch teleport" that lands a moved queue row. One progress value runs
 * 0 → 1 for the whole move, so the passed rows and the moved row share a clock:
 *
 * - the passed rows slide one slot over [SlideMillis] ([slideRemaining]);
 * - until [RevealAt] the slot only shows a blinking accent frame while the neighbours slide past;
 * - then the row appears as offset strips that jitter and flash, and a scan line sweeps down;
 *   each strip snaps into place once the line has passed it;
 * - at [ScanEnd] the whole row is assembled, the frame flashes at full strength and fades out.
 */
internal object QueueSwapMotion {
    const val ArriveMillis = 500
    /** The neighbours finish sliding before the moved row appears. */
    const val SlideMillis = 150

    const val RevealAt = 0.3f
    const val ScanEnd = 0.8f

    private const val GlitchBuckets = 24
    private const val BlinkBuckets = 6

    /** 6–9 strips of uneven height, each displaced 4–18 % of the width to the left or right. */
    fun plan(random: Random): List<SwapSlice> {
        val count = random.nextInt(6, 10)
        val weights = List(count) { 0.5f + random.nextFloat() }
        val total = weights.sum()
        var top = 0f
        return weights.mapIndexed { index, weight ->
            val bottom = if (index == count - 1) 1f else top + weight / total
            val direction = if (random.nextBoolean()) 1f else -1f
            SwapSlice(top, bottom, direction * (0.04f + random.nextFloat() * 0.14f), random.nextInt())
                .also { top = bottom }
        }
    }

    fun revealed(progress: Float): Boolean = progress >= RevealAt

    /** How far down the scan line is, as a fraction of the row height. */
    fun scanFraction(progress: Float): Float =
        ((progress - RevealAt) / (ScanEnd - RevealAt)).coerceIn(0f, 1f)

    fun pose(slice: SwapSlice, progress: Float, widthPx: Float): SwapSlicePose {
        val p = progress.coerceIn(0f, 1f)
        val scan = scanFraction(p)
        if (p >= 1f || scan >= slice.bottom) return SwapSlicePose(0f, flash = false)
        val glitch = glitchHash(slice.salt, (p * GlitchBuckets).toInt())
        // Now and then a strip jumps the other way; all strips calm down as the scan nears them.
        val jump = if (glitch % 3 == 0) -0.5f else 1f
        val dx = widthPx * slice.offset * jump * (1f - 0.5f * scan)
        return SwapSlicePose(dx, flash = glitch % 4 == 0)
    }

    /** Strength of the accent frame: blinking while the slot opens, steady, a lock-in flash, then gone. */
    fun frameAlpha(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return when {
            p >= 1f -> 0f
            p < RevealAt -> if ((p / RevealAt * BlinkBuckets).toInt() % 2 == 0) 0.9f else 0.3f
            p < ScanEnd -> 0.5f
            else -> 1f - (p - ScanEnd) / (1f - ScanEnd)
        }
    }

    /**
     * How far a passed row still is from its new slot, 1 → 0, on the same clock as the arrival:
     * it eases in over the first [SlideMillis] and then stays put.
     */
    fun slideRemaining(progress: Float): Float {
        val t = (progress * ArriveMillis / SlideMillis).coerceIn(0f, 1f)
        return 1f - FastOutSlowInEasing.transform(t)
    }
}

/** A confirmed move: the [entry] that teleports and how many slots each passed entry shifted. */
internal class QueueSwapLanding(val entry: QueueEntryId, private val shifts: Map<QueueEntryId, Int>) {
    /** +1 for a passed entry that moved down a slot, -1 for one that moved up, 0 for every other. */
    fun shift(id: QueueEntryId): Int = shifts[id] ?: 0
}

/**
 * Remembers the last move this queue asked for, so the next service order can tell whether that
 * move is what changed. Only the exact order the move produces counts: a rejected move, another
 * edit arriving in the same snapshot or a new session plays no animation.
 */
internal class QueueSwapTracker {
    private class Request(val entry: QueueEntryId, val sentFrom: List<QueueEntryId>, val expected: List<QueueEntryId>)

    // Written by gesture and accessibility callbacks, read when a new order is composed.
    private var request: Request? = null

    fun requested(entry: QueueEntryId, before: QueueEntryId?, displayedOrder: List<QueueEntryId>) {
        val rest = displayedOrder - entry
        val at = if (before == null) rest.size else rest.indexOf(before)
        request = if (at < 0) null
        else Request(entry, displayedOrder, rest.subList(0, at) + entry + rest.subList(at, rest.size))
    }

    /** The landing if [order] is exactly the displayed order with the requested move applied. */
    fun landed(order: List<QueueEntryId>): QueueSwapLanding? {
        val request = request?.takeIf { it.expected == order && it.sentFrom != order } ?: return null
        val from = request.sentFrom.indexOf(request.entry)
        val to = order.indexOf(request.entry)
        // The entries between the old and the new slot each moved one slot towards the old one.
        val passed = if (to > from) order.subList(from, to) else order.subList(to + 1, from + 1)
        return QueueSwapLanding(request.entry, passed.associateWith { if (to > from) -1 else 1 })
    }

    /** Any order other than the one the request was sent from answers it, whichever way. */
    fun settle(order: List<QueueEntryId>) {
        if (request?.sentFrom != order) request = null
    }
}
