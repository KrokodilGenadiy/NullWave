package com.zaus.nullwave.feature.library.data.waveform

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A mono RIFF/WAVE fixture with signed 16-bit little-endian samples. */
internal fun pcmWave(samples: ShortArray, sampleRate: Int = 8_000): ByteArray {
    val pcmBytes = samples.size * Short.SIZE_BYTES
    return ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt(36 + pcmBytes)
        put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        putInt(16) // size of the PCM format chunk
        putShort(1) // linear PCM
        putShort(1) // one channel
        putInt(sampleRate)
        putInt(sampleRate * Short.SIZE_BYTES)
        putShort(Short.SIZE_BYTES.toShort())
        putShort(16)
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(pcmBytes)
        for (sample in samples) putShort(sample)
    }.array()
}
