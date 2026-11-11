package com.zaus.nullwave.core.designsystem.components.primitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeTextFramesTest {
    @Test
    fun durationScalesWithLengthWithinBounds() {
        assertEquals(DecodeTextFrames.MIN_DURATION_MS, DecodeTextFrames("OK").durationMs)
        assertEquals(10 * DecodeTextFrames.PER_CHARACTER_MS, DecodeTextFrames("LIBRARY 01").durationMs)
        assertEquals(10 * DecodeTextFrames.PER_CHARACTER_MS, DecodeTextFrames("👩🏽‍🎤".repeat(10)).durationMs)
        assertEquals(DecodeTextFrames.MAX_DURATION_MS, DecodeTextFrames("x".repeat(200)).durationMs)
    }

    @Test
    fun startShowsOnlyGlyphsAndEndShowsTheFinalCopy() {
        val text = "LIBRARY"
        val frames = DecodeTextFrames(text)
        val first = frames.frame(0, seed = 7)
        assertEquals(0, first.resolvedEnd)
        assertEquals(text.length, first.text.length)
        assertTrue(first.text.all { it in DecodeTextFrames.GLYPHS })

        assertEquals(DecodeFrame(text, text.length), frames.frame(frames.durationMs.toLong(), seed = 7))
        assertEquals(DecodeFrame(text, text.length), frames.frame(Long.MAX_VALUE, seed = 7))
    }

    @Test
    fun resolvedCharactersAlwaysFormAGrowingPrefixOfTheFinalCopy() {
        val text = "БИБЛИОТЕКА · Library"
        val frames = DecodeTextFrames(text)
        var previous = 0
        for (elapsed in 0L..frames.durationMs step 5) {
            val frame = frames.frame(elapsed, seed = 42)
            assertTrue("Resolved part shrank at $elapsed", frame.resolvedEnd >= previous)
            assertEquals(text.substring(0, frame.resolvedEnd), frame.text.substring(0, frame.resolvedEnd))
            previous = frame.resolvedEnd
        }
        assertEquals(text.length, previous)
    }

    @Test
    fun whitespaceKeepsItsPlaceWhileWordsAreScrambled() {
        val frame = DecodeTextFrames("NO SIGNAL\tFOUND").frame(0, seed = 3)
        assertEquals(' ', frame.text[2])
        assertEquals('\t', frame.text[9])
    }

    @Test
    fun nonBreakingSpacesStayIntactAtEveryStep() {
        val text = "12\u00a0MB\u202f!\u2007X"
        val frames = DecodeTextFrames(text)
        for (elapsed in 0L..frames.durationMs) {
            val frame = frames.frame(elapsed, seed = 7)
            for (index in listOf(2, 5, 7)) assertEquals(text[index], frame.text[index])
        }
    }

    @Test
    fun glyphsChangeBetweenHoldsButNotWithinOne() {
        // Six characters use the 320 ms minimum, so nothing resolves inside the first hold.
        val text = "SIGNAL"
        val hold = DecodeTextFrames.GLYPH_HOLD_MS.toLong()
        assertEquals(DecodeTextFrames(text).frame(0, 11), DecodeTextFrames(text).frame(hold - 1, 11))
        assertNotEquals(DecodeTextFrames(text).frame(0, 11).text, DecodeTextFrames(text).frame(hold, 11).text)
    }

    @Test
    fun sameInputsGiveTheSameFrame() {
        assertEquals(DecodeTextFrames("NULLWAVE").frame(130, 5), DecodeTextFrames("NULLWAVE").frame(130, 5))
    }

    @Test
    fun flagsCombiningMarksAndEmojiSequencesResolveOneWholeGraphemeAtATime() {
        val clusters = listOf("🇵🇱", "e\u0301", "👩🏽‍🎤", "1\uFE0F\u20E3", "🎵")
        val text = clusters.joinToString("")
        val frames = DecodeTextFrames(text)
        val boundaries = clusters.runningFold(0) { offset, cluster -> offset + cluster.length }
        val observed = mutableSetOf<Int>()
        for (elapsed in 0L..frames.durationMs) {
            val frame = frames.frame(elapsed, seed = 9)
            val resolved = boundaries.indexOf(frame.resolvedEnd)
            assertTrue("Split grapheme at UTF-16 offset ${frame.resolvedEnd}", resolved >= 0)
            assertEquals(text.take(frame.resolvedEnd), frame.text.take(frame.resolvedEnd))
            val suffix = frame.text.drop(frame.resolvedEnd)
            assertEquals(clusters.size - resolved, suffix.length)
            assertTrue(suffix.all { it in DecodeTextFrames.GLYPHS })
            observed += frame.resolvedEnd
        }
        assertEquals(boundaries.toSet(), observed)
    }

    @Test
    fun emptyTextIsAlreadyDecoded() {
        assertEquals(DecodeFrame("", 0), DecodeTextFrames("").frame(0, seed = 1))
    }
}
