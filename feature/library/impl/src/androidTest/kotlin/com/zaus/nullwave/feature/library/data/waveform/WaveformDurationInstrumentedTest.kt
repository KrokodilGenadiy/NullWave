package com.zaus.nullwave.feature.library.data.waveform

import android.content.ContentResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.TrackId
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaveformDurationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val folder = TemporaryFolder(instrumentation.targetContext.cacheDir)
    private val track = TrackId("external_primary:1")
    private val decodeCount = AtomicInteger()

    @Test
    fun firstPlayerReadAfterDurationCorrectionRebuildsWarmAndColdPeaks() = runBlocking {
        val provider = provider(durationMs = 12_288)
        val warm = source(provider, folder.newFolder())
        val coldCache = folder.newFolder()
        val old = requireNotNull(warm.load(track))
        assertEquals(old, source(provider, coldCache).load(track))

        provider.durationMs = 6_144
        val expected = requireNotNull(source(provider, folder.newFolder()).load(track))
        assertTrue(old.peaks.indexOfLast { it > 0f } < expected.peaks.indexOfLast { it > 0f })
        assertEquals(expected, warm.load(track))
        val cold = source(provider, coldCache)
        assertEquals(expected, cold.load(track))
        assertEquals(expected, cold.load(track))
        assertEquals(5, decodeCount.get())
    }

    @Test
    fun coldWorkerRebuildsPeaksBeforeStampingTheCorrectedDuration() = runBlocking {
        val provider = provider(durationMs = 12_288)
        val cache = folder.newFolder()
        val first = source(provider, cache)
        val old = provider.request(track)
        assertEquals(WaveformPreparation.Ready, first.prepareInBackground(track, old))

        provider.durationMs = 6_144
        val current = provider.request(track)
        val restarted = source(provider, cache)
        assertEquals(WaveformPreparation.Ready, restarted.prepareInBackground(track, current))
        assertTrue(restarted.hasPreparedRevision(track, current))
        assertFalse(restarted.hasPreparedRevision(track, old))
        val expected = requireNotNull(source(provider, folder.newFolder()).load(track))
        assertEquals(expected, source(provider, cache).load(track))
        assertEquals(3, decodeCount.get())
    }

    @Test
    fun losingAndGainingADurationHintUsesTheMatchingTimeline() = runBlocking {
        val provider = provider(durationMs = null)
        val cache = folder.newFolder()
        val measured = requireNotNull(source(provider, cache).load(track))
        provider.durationMs = 12_288
        val source = source(provider, cache)
        val fromHint = requireNotNull(source.load(track))
        assertTrue(fromHint.peaks.indexOfLast { it > 0f } < measured.peaks.indexOfLast { it > 0f })
        provider.durationMs = null
        assertEquals(measured, source.load(track))
        assertEquals(3, decodeCount.get())
    }

    @Test
    fun containerDurationAllowsReuseAcrossAnUnrelatedCatalogCorrection() = runBlocking {
        val provider = provider(durationMs = 12_288, containerDuration = true)
        val cache = folder.newFolder()
        val expected = requireNotNull(source(provider, cache).load(track))
        assertTrue(provider.lastProjection.isEmpty())
        provider.durationMs = 6_144
        val restarted = source(provider, cache)
        assertEquals(expected, restarted.load(track))
        assertTrue(provider.lastProjection.isEmpty())
        val current = provider.request(track)
        assertEquals(WaveformPreparation.Ready, restarted.prepareInBackground(track, current))
        assertTrue(restarted.hasPreparedRevision(track, current))
        assertEquals(1, decodeCount.get())
    }

    private fun source(provider: WaveformTestProvider, cache: File) =
        MediaStoreWaveformSource(ContentResolver.wrap(provider), Dispatchers.IO, cache, { true }) { file, hint, session ->
            decodeCount.incrementAndGet()
            AndroidWaveformDecoder.decode(file, hint, session)
        }

    private fun provider(durationMs: Long?, containerDuration: Boolean = false): WaveformTestProvider {
        val flac = instrumentation.context.assets.open("waveform/resume-levels.flac").use { it.readBytes() }
        if (!containerDuration) clearFlacSampleCount(flac)
        return WaveformTestProvider(folder.newFile().apply { writeBytes(flac) }, durationMs)
    }
}
