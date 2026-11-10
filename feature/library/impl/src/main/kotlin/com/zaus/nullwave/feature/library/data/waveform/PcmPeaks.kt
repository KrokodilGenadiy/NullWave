package com.zaus.nullwave.feature.library.data.waveform

import android.media.AudioFormat
import com.zaus.nullwave.core.media.waveform.Waveform
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil

internal enum class PcmEncoding(val bytesPerSample: Int) {
    Unsigned8(1), Signed16(2), Packed24(3), Signed32(4), Float32(4);

    companion object {
        fun fromAndroid(encoding: Int): PcmEncoding? = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> Unsigned8
            AudioFormat.ENCODING_PCM_16BIT -> Signed16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> Packed24
            AudioFormat.ENCODING_PCM_32BIT -> Signed32
            AudioFormat.ENCODING_PCM_FLOAT -> Float32
            else -> null
        }
    }
}

internal data class PcmFormat(val encoding: PcmEncoding, val channels: Int, val sampleRate: Int) {
    fun framesIn(buffer: ByteBuffer): Int? {
        val frameBytes = channels.toLong() * encoding.bytesPerSample
        if (frameBytes <= 0 || sampleRate <= 0 || buffer.remaining() % frameBytes != 0L) return null
        return (buffer.remaining() / frameBytes).toInt()
    }
}

/** Reduces each PCM buffer immediately. The retained data has the same size for a song and a podcast. */
internal class PcmPeaks(private val durationUs: Long, bins: Int = WaveformBinCount, initial: Waveform? = null) {
    private val peaks = initial?.peaks?.toFloatArray() ?: FloatArray(bins)
    private val pcm16 = ShortArray(4096)
    private var hasFrames = initial != null

    fun add(data: ByteBuffer, startUs: Long, format: PcmFormat): Boolean {
        // Android PCM uses native byte order (little endian on Android ABIs).
        data.order(ByteOrder.LITTLE_ENDIAN)
        val frames = format.framesIn(data) ?: return false
        val framesPerBin = durationUs.toDouble() * format.sampleRate / (1_000_000.0 * peaks.size)
        val startFrame = startUs.toDouble() * format.sampleRate / 1_000_000.0
        var frame = 0
        while (frame < frames) {
            val bin = ((startFrame + frame) / framesPerBin).toInt().coerceIn(peaks.indices)
            val end = if (bin == peaks.lastIndex) frames else {
                ceil((bin + 1) * framesPerBin - startFrame).toInt().coerceIn(frame + 1, frames)
            }
            peaks[bin] = maxOf(peaks[bin], format.encoding.peak(data, (end - frame) * format.channels))
            frame = end
        }
        if (frames > 0) hasFrames = true
        return true
    }

    fun waveform(): Waveform? = if (hasFrames) Waveform.of(peaks.asList()) else null

    private fun PcmEncoding.peak(data: ByteBuffer, samples: Int): Float {
        var peak = 0f
        when (this) {
            PcmEncoding.Signed16 -> {
                // Decoders normally supply PCM16 in a direct buffer. Read it in bulk and normalize the peak once.
                val shorts = data.asShortBuffer()
                var remaining = samples
                var magnitude = 0
                while (remaining > 0) {
                    val count = minOf(remaining, pcm16.size)
                    shorts.get(pcm16, 0, count)
                    for (i in 0 until count) magnitude = maxOf(magnitude, abs(pcm16[i].toInt()))
                    remaining -= count
                }
                data.position(data.position() + samples * Short.SIZE_BYTES)
                peak = magnitude / 32768f
            }
            PcmEncoding.Unsigned8 -> repeat(samples) { peak = maxOf(peak, abs((data.get().toInt() and 255) - 128) / 128f) }
            PcmEncoding.Packed24 -> repeat(samples) {
                val low = data.get().toInt() and 255
                val mid = data.get().toInt() and 255
                val high = data.get().toInt()
                peak = maxOf(peak, abs((high shl 16) or (mid shl 8) or low) / 8388608f)
            }
            PcmEncoding.Signed32 -> repeat(samples) { peak = maxOf(peak, abs(data.int.toFloat()) / 2147483648f) }
            PcmEncoding.Float32 -> repeat(samples) {
                val value = data.float
                if (value.isFinite()) peak = maxOf(peak, abs(value).coerceAtMost(1f))
            }
        }
        return peak
    }
}

internal const val WaveformBinCount = 128
