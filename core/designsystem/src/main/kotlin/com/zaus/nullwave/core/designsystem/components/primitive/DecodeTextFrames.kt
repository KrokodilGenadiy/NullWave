package com.zaus.nullwave.core.designsystem.components.primitive

/**
 * One rendered decode step. Characters before [resolvedEnd] (a UTF-16 index into [text]) are the
 * final copy; the rest are cipher glyphs or preserved whitespace.
 */
internal data class DecodeFrame(val text: String, val resolvedEnd: Int)

/**
 * Segment the final text once. Each visible character resolves as a whole, including emoji
 * sequences and combining marks. The same text, elapsed time and seed always give the same frame.
 */
internal class DecodeTextFrames(private val text: String) {
    private val graphemes = Grapheme.findAll(text).map { it.value }.toList()

    /** Short labels still read as a decode; long copy never holds the reader for more than 0.9 s. */
    val durationMs = (graphemes.size.toLong() * PER_CHARACTER_MS)
        .coerceIn(MIN_DURATION_MS.toLong(), MAX_DURATION_MS.toLong()).toInt()

    fun frame(elapsedMs: Long, seed: Int): DecodeFrame {
        if (graphemes.isEmpty() || elapsedMs >= durationMs) return DecodeFrame(text, text.length)
        val elapsed = elapsedMs.coerceAtLeast(0)
        val tick = elapsed / GLYPH_HOLD_MS
        val resolvedCount = (elapsed * graphemes.size / durationMs).toInt()
        val out = StringBuilder(text.length)
        var resolvedEnd = 0
        for ((index, grapheme) in graphemes.withIndex()) {
            when {
                index < resolvedCount -> {
                    out.append(grapheme)
                    resolvedEnd = out.length
                }
                // Kotlin includes Unicode space separators (NBSP, narrow NBSP) in isWhitespace.
                grapheme.first().isWhitespace() -> out.append(grapheme)
                else -> out.append(GLYPHS[glyphIndex(seed, index, tick)])
            }
        }
        return DecodeFrame(out.toString(), resolvedEnd)
    }

    private fun glyphIndex(seed: Int, index: Int, tick: Long): Int {
        var hash = seed xor (index * -0x61c88647) xor (tick.toInt() * 0x27d4eb2d)
        hash = hash xor (hash ushr 16)
        hash *= -0x7a143595
        hash = hash xor (hash ushr 13)
        hash *= -0x3d4d51cb
        hash = hash xor (hash ushr 16)
        return Math.floorMod(hash, GLYPHS.length)
    }

    companion object {
        private val Grapheme = Regex("""\X""")
        const val GLYPHS = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789#%&*+=/<>[]{}"
        const val PER_CHARACTER_MS = 45
        const val MIN_DURATION_MS = 320
        const val MAX_DURATION_MS = 900
        const val GLYPH_HOLD_MS = 50
    }
}
