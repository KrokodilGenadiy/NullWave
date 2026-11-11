package com.zaus.nullwave.core.playback.media3.testing

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun createSilentWav(directory: File, durationSeconds: Int): File {
    val pcmBytes = 8_000 * durationSeconds * 2
    val data = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
    data.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcmBytes)
    data.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
    data.putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
    data.put("data".toByteArray(Charsets.US_ASCII)).putInt(pcmBytes)
    return File.createTempFile("playback-", ".wav", directory).apply { writeBytes(data.array()) }
}
