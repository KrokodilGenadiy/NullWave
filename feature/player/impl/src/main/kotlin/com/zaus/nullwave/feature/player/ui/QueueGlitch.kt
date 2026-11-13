package com.zaus.nullwave.feature.player.ui

/**
 * A stable pseudo-random, non-negative number for one piece of a glitch effect at one time bucket.
 * The queue's shred and teleport use it for hops and flashes, so the same seed replays identically.
 */
internal fun glitchHash(salt: Int, bucket: Int): Int {
    var h = salt xor (bucket * -0x61c88647)
    h = h xor (h ushr 16)
    h *= -0x7a143595
    h = h xor (h ushr 13)
    return h and Int.MAX_VALUE
}
