package com.zaus.nullwave.core.media.waveform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WaveformTest {
    @Test
    fun `silent audio and full scale peaks are valid`() {
        assertEquals(listOf(0f, 0f), Waveform.of(listOf(0f, 0f)).peaks)
        assertEquals(listOf(1f), Waveform.of(listOf(1f)).peaks)
    }

    @Test
    fun `empty and invalid peaks are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Waveform.of(emptyList()) }
        for (invalid in listOf(-0.1f, 1.1f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)) {
            assertThrows("Invalid peak $invalid", IllegalArgumentException::class.java) {
                Waveform.of(listOf(0.5f, invalid))
            }
        }
    }

    @Test
    fun `waveform keeps its ordered peaks and value equality after mutation attempts`() {
        val input = mutableListOf(0.25f, 0.75f)
        val waveform = Waveform.of(input)
        val equalWaveform = Waveform.of(listOf(0.25f, 0.75f))
        val originalHash = waveform.hashCode()

        input.clear()
        val mutableView = waveform.peaks as? MutableList<Float>
        if (mutableView != null) {
            assertThrows(UnsupportedOperationException::class.java) { mutableView[0] = 1f }
        }

        assertEquals(listOf(0.25f, 0.75f), waveform.peaks)
        assertEquals(equalWaveform, waveform)
        assertEquals(equalWaveform.hashCode(), waveform.hashCode())
        assertEquals(originalHash, waveform.hashCode())
        assertNotEquals(waveform, Waveform.of(listOf(0.75f, 0.25f)))
    }
}
