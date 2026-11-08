package com.zaus.nullwave.feature.library.data.waveform

import android.content.res.AssetFileDescriptor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.waveform.Waveform
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidWaveformDecoderInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun rawPcmPreservesLevelsAndOrderAcrossTheTrack() = runBlocking {
        val amplitudes = shortArrayOf(0, -4096, 24576, 8192)
        val pcm = ShortArray(8192) { amplitudes[it / 2048] }
        assertQuarterLevels(decodeAssetSlice(pcmWave(pcm)))
    }

    @Test
    fun compressedFlacPreservesLevelsAndOrderAcrossTheTrack() = runBlocking {
        // Lossless encoding of the same four mono levels, 2048 samples per level at 8 kHz.
        val flac = instrumentation.context.assets.open("waveform/quarter-levels.flac").use { it.readBytes() }
        assertQuarterLevels(decodeAssetSlice(flac))
    }

    @Test
    fun flacWithUnknownSampleCountUsesTheDecodedTimeline() = runBlocking {
        val flac = assetBytes("resume-levels.flac")
        val expected = decodeAssetSlice(flac)
        clearFlacSampleCount(flac)
        withAssetSlice(flac) { file ->
            val reader = MediaExtractor()
            try {
                reader.setDataSource(file)
                assertTrue(reader.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION, 0L) <= 0)
            } finally {
                reader.release()
            }
            assertEquals(expected, requireNotNull(AndroidWaveformDecoder.decode(file)).peaks)
        }
    }

    @Test
    fun durationProbeRetriesFromTheBeginningWhenTailDecodeThrows() = runBlocking {
        withAssetSlice(assetBytes("quarter-tones.mp3")) { file ->
            // The real extractor chooses a point near the end. Model a codec that only opens at the start.
            for (failure in listOf(IllegalStateException("Decoder rejected seek"), IOException("Tail read failed"))) {
                val starts = mutableListOf<Long?>()
                val duration = AndroidWaveformDecoder.measureDuration(file, index = 0, session = null) { startUs ->
                    starts.add(startUs)
                    if (startUs != null) throw failure
                    2_000_000L
                }
                assertEquals(2_000_000L, requireNotNull(duration))
                assertEquals(2, starts.size)
                assertTrue(requireNotNull(starts.first()) > 0L)
                assertNull(starts.last())
            }
        }
    }

    @Test
    fun durationProbePropagatesCancellationAndAccessLossWithoutRetrying() = runBlocking {
        withAssetSlice(assetBytes("quarter-tones.mp3")) { file ->
            for (failure in listOf(CancellationException("cancelled"), SecurityException("access revoked"))) {
                var calls = 0
                val result = runCatching {
                    AndroidWaveformDecoder.measureDuration(file, index = 0, session = null) { startUs ->
                        calls++
                        assertTrue(requireNotNull(startUs) > 0L)
                        throw failure
                    }
                }
                assertSame(failure, result.exceptionOrNull())
                assertEquals(1, calls)
            }
        }
    }

    @Test
    fun cbrMp3PreservesLevelsAndOrderAcrossTheTrack() = runBlocking {
        assertLossyTones("quarter-tones.mp3")
    }

    @Test
    fun aacInM4aPreservesLevelsAndOrderAcrossTheTrack() = runBlocking {
        assertLossyTones("quarter-tones.m4a")
    }

    private suspend fun assertLossyTones(name: String) {
        // Two seconds of 440 Hz mono audio, quarter amplitudes 3%, 12.5%, 75%, 25%.
        withAssetSlice(assetBytes(name)) { file ->
            val final = requireNotNull(AndroidWaveformDecoder.decode(file))
            // AAC can attenuate individual packets. Use the middle half of each steady tone,
            // away from transitions, and compare their relative levels across platform decoders.
            val quarterSize = final.peaks.size / 4
            val levels = List(4) { quarter ->
                val start = quarter * quarterSize + quarterSize / 4
                final.peaks.subList(start, start + quarterSize / 2).max()
            }
            val loud = levels[2]
            assertTrue("$name lost its loud passage: $levels", loud >= 0.45f)
            for ((quarter, expectedRatio) in listOf(0.04f, 1f / 6f, 1f, 1f / 3f).withIndex()) {
                assertEquals("$name quarter $quarter: $levels", expectedRatio, levels[quarter] / loud,
                    expectedRatio * 0.25f + 0.02f)
            }
        }
    }

    private fun assetBytes(name: String): ByteArray =
        instrumentation.context.assets.open("waveform/$name").use { it.readBytes() }

    private fun clearFlacSampleCount(flac: ByteArray) {
        // FLAC STREAMINFO reserves a zero 36-bit total-samples field for unknown length.
        flac[21] = (flac[21].toInt() and 0xf0).toByte()
        flac.fill(0, fromIndex = 22, toIndex = 26)
    }

    @Test
    fun resumingCompressedFilesAfterANonzeroSeekPreservesTheOriginalTimeline() = runBlocking {
        for (asset in listOf("resume-levels.flac", "quarter-tones.mp3")) {
            withAssetSlice(assetBytes(asset)) { file ->
                val expected = requireNotNull(AndroidWaveformDecoder.decode(file))
                // A worker cannot fall back to starting the whole file again. Exercise an actual resume.
                val resume = WaveformDecodeSession(
                    previous = interruptAtCheckpoint(file, afterUs = 1_400_000L),
                    timeLimitMs = 90_000L,
                    save = { true },
                )
                assertEquals(asset, expected, AndroidWaveformDecoder.decode(file, session = resume))
            }
        }
    }

    @Test
    fun workerTimeoutDuringPrerollKeepsTheCheckpointForAnUnboundedRead() = runBlocking {
        withAssetSlice(assetBytes("quarter-tones.mp3")) { file ->
            val expected = requireNotNull(AndroidWaveformDecoder.decode(file))
            // An early checkpoint starts its preroll at the beginning on every extractor.
            val saved = interruptAtCheckpoint(file, afterUs = 400_000L)
            assertTrue("Fixture needs a checkpoint inside the opening second", saved.positionUs < 1_000_000L)
            var now = 0L
            val worker = WaveformDecodeSession(
                previous = saved,
                save = { error("Preroll cannot advance the checkpoint") },
                discard = { error("Preroll timeout must retain the checkpoint") },
                timeLimitMs = 1L,
                clockMs = { now++ },
            )
            assertNull(AndroidWaveformDecoder.decode(file, session = worker))
            assertEquals(WaveformDecodeSession.SliceEnd.NoProgress, worker.sliceEnd)
            val foreground = WaveformDecodeSession(previous = saved, save = { true })
            assertEquals(expected, AndroidWaveformDecoder.decode(file, session = foreground))
        }
    }

    @Test
    fun correctedDurationLetsABoundedScanSaveAnEarlierPositionAndResumeOnTheNewTimeline() = runBlocking {
        val flac = assetBytes("resume-levels.flac")
        val expected = decodeAssetSlice(flac)
        clearFlacSampleCount(flac)
        withAssetSlice(flac) { file ->
            val durationUs = 6_144_000L // 49,152 samples at 8 kHz.
            val old = WaveformCheckpoint(durationUs * 2, 4_000_000L, Waveform.of(List(WaveformBinCount) { 1f }))
            var saved: WaveformCheckpoint? = old
            var discarded = 0
            var now = 0L
            val worker = WaveformDecodeSession(
                previous = old,
                discard = {
                    discarded++
                    saved = null
                },
                save = { checkpoint ->
                    assertEquals(1, discarded)
                    saved = checkpoint
                    true
                },
                timeLimitMs = 4L,
                clockMs = { now++ },
            )
            assertNull(AndroidWaveformDecoder.decode(file, { durationUs }, worker))
            assertEquals(WaveformDecodeSession.SliceEnd.Advanced, worker.sliceEnd)
            val checkpoint = requireNotNull(saved)
            assertEquals(durationUs, checkpoint.durationUs)
            assertTrue(checkpoint.positionUs in 1L until old.positionUs)

            val next = WaveformDecodeSession(previous = checkpoint, timeLimitMs = 90_000L, save = { true })
            assertEquals(expected, requireNotNull(AndroidWaveformDecoder.decode(file, { durationUs }, next)).peaks)
        }
    }

    private suspend fun interruptAtCheckpoint(file: AssetFileDescriptor, afterUs: Long): WaveformCheckpoint {
        var saved: WaveformCheckpoint? = null
        val abort = CancellationException("Simulated stop after persisting the checkpoint")
        val interrupted = WaveformDecodeSession(previous = null, checkpointIntervalMs = 0, save = {
            if (it.positionUs > afterUs) {
                saved = it
                throw abort
            }
            false
        })
        try {
            AndroidWaveformDecoder.decode(file, session = interrupted)
            error("Expected a checkpoint after $afterUs microseconds")
        } catch (cancelled: CancellationException) {
            assertSame(abort, cancelled)
        }
        return requireNotNull(saved)
    }

    @Test
    fun silentFileProducesOnlyZeroPeaks() = runBlocking {
        val peaks = decodeAssetSlice(pcmWave(ShortArray(8192)))
        assertTrue(peaks.isNotEmpty())
        assertTrue(peaks.all { it == 0f })
    }

    private suspend fun decodeAssetSlice(audio: ByteArray): List<Float> = withAssetSlice(audio) { descriptor ->
        requireNotNull(AndroidWaveformDecoder.decode(descriptor)) { "Fixture must yield a waveform" }.peaks
    }

    private suspend fun <T> withAssetSlice(audio: ByteArray, block: suspend (AssetFileDescriptor) -> T): T {
        val file = File.createTempFile("waveform-", ".bin", instrumentation.targetContext.cacheDir)
        try {
            // Android content providers may expose a slice from a larger file.
            val prefix = ByteArray(256) { 0x55.toByte() }
            file.outputStream().use { out ->
                out.write(prefix)
                out.write(audio)
                out.write(prefix)
            }
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(fd, prefix.size.toLong(), audio.size.toLong()).use { descriptor ->
                block(descriptor)
            }
        } finally {
            file.delete()
        }
    }

    private fun assertQuarterLevels(peaks: List<Float>) {
        val levels = listOf(0f, 0.125f, 0.75f, 0.25f)
        val quarter = peaks.size / levels.size
        assertTrue("Expected time resolution across all four levels", quarter >= 3)
        for ((index, level) in levels.withIndex()) {
            // Buckets touching a boundary can contain a sample from the adjacent quarter.
            val start = index * quarter + 1
            val end = (index + 1) * quarter - 1
            for (bucket in start until end) {
                assertEquals("Peak $bucket in quarter $index", level, peaks[bucket], 0.001f)
            }
        }
    }
}
