package com.zaus.nullwave.feature.library.data.waveform

import android.content.res.AssetFileDescriptor
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import com.zaus.nullwave.core.media.waveform.Waveform
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer

/** Called on the source's I/O dispatcher. The caller owns [AssetFileDescriptor]. */
internal object AndroidWaveformDecoder {
    suspend fun decode(
        file: AssetFileDescriptor,
        durationHintUs: () -> Long? = { null },
        session: WaveformDecodeSession? = null,
    ): Waveform? {
        if (session?.previous == null) return decodeFile(file, durationHintUs, session)
        val resumed = tryDecodeFromSeek {
            decodeFile(file, durationHintUs, session)
        }
        if (resumed != null || !session.canRestartFromBeginning) return resumed
        session.discardUnusableCheckpoint()
        return decodeFile(file, durationHintUs, session)
    }

    private suspend fun decodeFile(
        file: AssetFileDescriptor, durationHintUs: () -> Long?, session: WaveformDecodeSession?,
    ): Waveform? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue
                val declaredUs = format.getLong(MediaFormat.KEY_DURATION, 0)
                val durationUs = declaredUs.takeIf { it > 0 } ?: durationHintUs()?.takeIf { it > 0 }
                    ?: session?.previous?.durationUs ?: measureDuration(file, index, session) { startUs ->
                        measurePcmDuration(file, index, format, mime, session, startUs)
                    } ?: return null
                extractor.selectTrack(index)
                // Keep the original time origin when seeking. Output PTS from a resumed decoder is still absolute.
                val firstTimeUs = extractor.sampleTime
                val previous = session?.checkpointFor(durationUs)
                if (previous != null && previous.positionUs > PrerollUs) {
                    val desiredUs = previous.positionUs - PrerollUs
                    extractor.seekTo(firstTimeUs + desiredUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val usableStart = firstTimeUs..(firstTimeUs + desiredUs)
                    if (extractor.sampleTime !in usableStart) {
                        // Some extractors round a byte-based seek past the requested sync point.
                        // Seek one preroll earlier and keep the same minimum warm-up before resuming.
                        val earlierUs = (desiredUs - PrerollUs).coerceAtLeast(0L)
                        extractor.seekTo(firstTimeUs + earlierUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        if (extractor.sampleTime !in usableStart) return null
                    }
                }
                val peaks = PcmPeaks(durationUs, initial = previous?.waveform)
                var reachedCheckpoint = previous == null
                val complete = readPcm(extractor, format, mime, firstTimeUs) { data, startUs, layout ->
                    // Discard codec warm-up. Retain a short overlap to cover packet alignment at the boundary.
                    if (previous != null && startUs < previous.positionUs - OverlapUs) {
                        return@readPcm session?.withinTimeLimit() != false
                    }
                    if (!reachedCheckpoint && previous != null) {
                        val endUs = layout.packetEndUs(startUs, data) ?: return@readPcm false
                        reachedCheckpoint = endUs >= previous.positionUs
                    }
                    peaks.add(data, startUs, layout) && (session?.afterPacket(startUs, durationUs, peaks) != false)
                }
                return if (complete && reachedCheckpoint) peaks.waveform() else null
            }
            return null
        } finally {
            extractor.release()
        }
    }

    // Visit compressed timestamps without decoding the whole recording just to measure its length.
    internal suspend fun measureDuration(
        file: AssetFileDescriptor, index: Int, session: WaveformDecodeSession?,
        measureFrom: suspend (Long?) -> Long?,
    ): Long? {
        val packets = MediaExtractor()
        val tailStartUs = try {
            packets.setDataSource(file)
            packets.selectTrack(index)
            val first = packets.sampleTime
            if (first < 0) return null
            var last = first
            val context = currentCoroutineContext()
            while (packets.advance()) {
                context.ensureActive()
                if (session?.withinTimeLimit() == false) return null
                last = maxOf(last, packets.sampleTime)
            }
            (last - PrerollUs).takeIf { it > first }
        } finally {
            packets.release()
        }
        if (tailStartUs != null) {
            tryDecodeFromSeek { measureFrom(tailStartUs) }?.let { return it }
        }
        if (session?.withinTimeLimit() == false) return null
        // Short recordings and extractors without a usable seek index use a fresh stream from the beginning.
        return measureFrom(null)
    }

    /** The same file may decode sequentially even when the extractor or codec rejected a seek. */
    private suspend inline fun <T> tryDecodeFromSeek(read: () -> T?): T? {
        val value = try {
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
        if (value == null) currentCoroutineContext().ensureActive()
        return value
    }

    private suspend fun measurePcmDuration(
        file: AssetFileDescriptor, index: Int, format: MediaFormat, mime: String,
        session: WaveformDecodeSession?, tailStartUs: Long?,
    ): Long? {
        val probe = MediaExtractor()
        try {
            probe.setDataSource(file)
            probe.selectTrack(index)
            val firstTimeUs = probe.sampleTime
            if (tailStartUs != null) {
                probe.seekTo(tailStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                if (probe.sampleTime !in firstTimeUs..tailStartUs) return null
            }
            var durationUs = 0L
            val complete = readPcm(probe, format, mime, firstTimeUs) { data, startUs, layout ->
                if (session?.withinTimeLimit() == false) return@readPcm false
                val endUs = layout.packetEndUs(startUs, data) ?: return@readPcm false
                durationUs = maxOf(durationUs, endUs)
                true
            }
            return durationUs.takeIf { complete && it > 0 }
        } finally {
            probe.release()
        }
    }

    private suspend fun readPcm(
        extractor: MediaExtractor,
        format: MediaFormat,
        mime: String,
        firstTimeUs: Long,
        consume: (ByteBuffer, Long, PcmFormat) -> Boolean,
    ): Boolean {
        if (mime != MediaFormat.MIMETYPE_AUDIO_RAW) return decodeCompressed(extractor, mime, format, firstTimeUs, consume)
        val layout = format.pcmFormat() ?: return false
        val context = currentCoroutineContext()
        var buffer = ByteBuffer.allocateDirect(16 * 1024)
        while (true) {
            context.ensureActive()
            val size = extractor.sampleSize
            if (size < 0) break
            if (size > MaxRawPacketBytes || extractor.isEncrypted()) return false
            if (size > buffer.capacity()) buffer = ByteBuffer.allocateDirect(size.toInt())
            buffer.clear()
            if (extractor.readSampleData(buffer, 0).toLong() != size) return false
            buffer.position(0).limit(size.toInt())
            if (!consume(buffer, extractor.sampleTime - firstTimeUs, layout)) return false
            if (!extractor.advance()) break
        }
        return true
    }

    private suspend fun decodeCompressed(
        extractor: MediaExtractor,
        mime: String,
        format: MediaFormat,
        firstTimeUs: Long,
        consume: (ByteBuffer, Long, PcmFormat) -> Boolean,
    ): Boolean {
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val context = currentCoroutineContext()
            val info = MediaCodec.BufferInfo()
            var inputFinished = false
            var pcm: PcmFormat? = null
            var lastActivityMs = SystemClock.elapsedRealtime()
            while (true) {
                context.ensureActive()
                val input = if (inputFinished) -1 else codec.dequeueInputBuffer(0)
                if (input >= 0) {
                    val buffer = codec.getInputBuffer(input) ?: return false
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputFinished = true
                    } else {
                        val sampleFlags = extractor.sampleFlags
                        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) return false
                        val partial = sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0
                        val flags = if (partial) MediaCodec.BUFFER_FLAG_PARTIAL_FRAME else 0
                        codec.queueInputBuffer(input, 0, size, extractor.sampleTime, flags)
                        extractor.advance()
                    }
                }

                // Feed the codec without waiting between input packets. Block briefly only if
                // nothing could be fed; cancellation is checked again on the next iteration.
                val output = codec.dequeueOutputBuffer(info, if (input >= 0) 0 else CodecPollUs)
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    pcm = codec.outputFormat.pcmFormat() ?: return false
                } else if (output >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val buffer = codec.getOutputBuffer(output) ?: return false
                        val layout = pcm ?: codec.getOutputFormat(output).pcmFormat() ?: return false
                        // getOutputBuffer sets position and limit to the valid bytes (API 21+).
                        if (!consume(buffer, info.presentationTimeUs - firstTimeUs, layout)) return false
                        pcm = layout
                    }
                    codec.releaseOutputBuffer(output, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }
                if (input >= 0 || output != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    lastActivityMs = SystemClock.elapsedRealtime()
                } else if (SystemClock.elapsedRealtime() - lastActivityMs >= CodecStallMs) {
                    return false
                }
            }
        } finally {
            codec.release()
        }
    }

    private fun MediaExtractor.isEncrypted(): Boolean = sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0

    private fun PcmFormat.packetEndUs(startUs: Long, data: ByteBuffer): Long? {
        val frames = framesIn(data) ?: return null
        val lengthUs = (frames * 1_000_000L + sampleRate - 1) / sampleRate
        return if (startUs <= Long.MAX_VALUE - lengthUs) startUs + lengthUs else null
    }

    private fun MediaFormat.pcmFormat(): PcmFormat? {
        val encoding = PcmEncoding.fromAndroid(getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT))
            ?: return null
        return PcmFormat(encoding, getInteger(MediaFormat.KEY_CHANNEL_COUNT, 0), getInteger(MediaFormat.KEY_SAMPLE_RATE, 0))
    }

    private const val MaxRawPacketBytes = 1024 * 1024
    private const val PrerollUs = 1_000_000L
    private const val OverlapUs = 100_000L
    private const val CodecPollUs = 10_000L
    private const val CodecStallMs = 5_000L
}
