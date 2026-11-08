package com.zaus.nullwave.feature.library.data.waveform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmPeaksTest {
    @Test
    fun `interleaved stereo uses the loudest channel in each time interval`() {
        val peaks = PcmPeaks(durationUs = 1_000_000, bins = 4)
        val stereo = PcmFormat(PcmEncoding.Signed16, channels = 2, sampleRate = 8)
        // The second packet starts between bucket boundaries and covers frames from three buckets.
        assertTrue(peaks.add(pcm16(0, 0, 0, 8192, -16384, 0), 0, stereo))
        assertTrue(peaks.add(pcm16(0, 8192, 0, 0, 0, 0, 0, -32768, 0, 0), 375_000, stereo))
        assertEquals(listOf(0.25f, 0.5f, 0f, 1f), peaks.waveform()?.peaks)
    }

    @Test
    fun `time gaps stay silent and only valid PCM frames count`() {
        val peaks = PcmPeaks(durationUs = 1_000_000, bins = 4)
        val mono = PcmFormat(PcmEncoding.Signed16, channels = 1, sampleRate = 4)
        assertNull(peaks.waveform())
        assertFalse(peaks.add(ByteBuffer.allocate(1), 0, mono))
        assertTrue(peaks.add(pcm16(8192), 750_000, mono))
        assertEquals(listOf(0f, 0f, 0f, 0.25f), peaks.waveform()?.peaks)
    }

    @Test
    fun `packets in one bin of a ninety minute recording retain the loudest sample`() {
        val peaks = PcmPeaks(durationUs = 90 * 60 * 1_000_000L)
        val mono = PcmFormat(PcmEncoding.Signed16, channels = 1, sampleRate = 44_100)
        assertTrue(peaks.add(pcm16(4096), 0, mono))
        assertTrue(peaks.add(pcm16(16384), 23, mono))
        val result = requireNotNull(peaks.waveform()).peaks
        assertEquals(128, result.size)
        assertEquals(0.5f, result.first(), 0f)
        assertTrue(result.drop(1).all { it == 0f })
    }

    @Test
    fun `overlapping packets with earlier timestamps preserve later peaks`() {
        val peaks = PcmPeaks(durationUs = 1_000_000, bins = 4)
        val mono = PcmFormat(PcmEncoding.Signed16, channels = 1, sampleRate = 8)
        assertTrue(peaks.add(pcm16(8192, 4096, 8192), 250_000, mono))
        assertTrue(peaks.add(pcm16(16384, 24576), 125_000, mono))
        assertEquals(listOf(0.5f, 0.75f, 0.25f, 0f), peaks.waveform()?.peaks)
    }

    @Test
    fun `PCM sample widths preserve amplitude and float data stays normalized`() {
        val cases = listOf(
            PcmEncoding.Unsigned8 to ByteBuffer.wrap(byteArrayOf(128.toByte(), 192.toByte())),
            PcmEncoding.Packed24 to ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0, 0, 0xc0.toByte())),
            PcmEncoding.Signed32 to buffer(8) { putInt(0).putInt(-1073741824) },
            PcmEncoding.Float32 to buffer(8) { putFloat(Float.NaN).putFloat(-0.5f) },
        )
        for ((encoding, data) in cases) {
            val peaks = PcmPeaks(durationUs = 1_000_000, bins = 2)
            assertTrue(peaks.add(data, 0, PcmFormat(encoding, channels = 1, sampleRate = 2)))
            assertEquals(encoding.name, listOf(0f, 0.5f), peaks.waveform()?.peaks)
        }
    }

    private fun pcm16(vararg samples: Int): ByteBuffer = buffer(samples.size * 2) {
        for (sample in samples) putShort(sample.toShort())
    }

    private fun buffer(size: Int, write: ByteBuffer.() -> Unit): ByteBuffer =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(write).apply { flip() }
}
