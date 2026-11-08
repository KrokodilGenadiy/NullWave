package com.zaus.nullwave.feature.library.data.waveform

import android.content.ContentResolver
import android.content.ContentUris
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.data.waveform.work.WaveformWorkCoordinator
import com.zaus.nullwave.feature.library.data.waveform.work.WaveformWorkQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaveformCatalogRevisionInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val folder = TemporaryFolder(context.cacheDir)
    private val id = TrackId("external_primary:1")

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun delayedOlderWorkLeavesTheNewStampReadyForAColdCatalogScan() = runTest {
        val provider = provider()
        val before = provider.request(id)
        provider.dateModifiedSeconds++
        val current = provider.request(id)
        val resolver = ContentResolver.wrap(provider)
        fun newSource() = MediaStoreWaveformSource(resolver, Dispatchers.IO, folder.root, { true })

        assertEquals(WaveformPreparation.Ready, newSource().prepareInBackground(id, current))
        val delayedWorker = newSource()
        assertEquals(WaveformPreparation.Unavailable, delayedWorker.prepareInBackground(id, before))
        val restarted = newSource()
        assertTrue(restarted.hasPreparedRevision(id, current))
        assertFalse(restarted.hasPreparedRevision(id, before))

        val enqueued = mutableListOf<String>()
        val catalog = WaveformWorkCoordinator(backgroundScope, WaveformWorkQueue { entries, _ -> enqueued.addAll(entries) },
            { true }, restarted::hasPreparedRevision, restarted::prepareInBackground)
        catalog.submit(listOf(provider.row(id)))
        runCurrent()
        assertTrue(enqueued.isEmpty())
    }

    @Test
    fun rowChangingDuringCacheHitPreservesThePreviousStampUntilCurrentWorkArrives() = runTest {
        val provider = provider()
        val resolver = ContentResolver.wrap(provider)
        val initial = provider.request(id)
        val source = MediaStoreWaveformSource(resolver, Dispatchers.IO, folder.root, { true })
        assertEquals(WaveformPreparation.Ready, source.prepareInBackground(id, initial))

        val worker = MediaStoreWaveformSource(resolver, Dispatchers.IO, folder.root, { true }) { _, _, _ ->
            throw AssertionError("Unchanged audio must reuse its stored waveform")
        }
        provider.dateModifiedSeconds++
        val pending = provider.request(id)
        provider.advanceDateAfterNextQuery = true
        assertEquals(WaveformPreparation.Unavailable, worker.prepareInBackground(id, pending))
        assertTrue(worker.hasPreparedRevision(id, initial))
        assertFalse(worker.hasPreparedRevision(id, pending))

        val current = provider.request(id)
        assertTrue(current != pending)
        assertEquals(WaveformPreparation.Ready, worker.prepareInBackground(id, current))
        assertTrue(worker.hasPreparedRevision(id, current))
    }

    @Test
    fun metadataChangingDuringDecodePreventsPublishingTheObsoleteStamp() = runTest {
        val provider = provider()
        val resolver = ContentResolver.wrap(provider)
        val before = provider.request(id)
        val source = MediaStoreWaveformSource(resolver, Dispatchers.IO, folder.root, { true }) { file, hint, session ->
            AndroidWaveformDecoder.decode(file, hint, session).also { provider.dateModifiedSeconds++ }
        }
        assertEquals(WaveformPreparation.Unavailable, source.prepareInBackground(id, before))
        val restarted = MediaStoreWaveformSource(resolver, Dispatchers.IO, folder.root, { true })
        val current = provider.request(id)
        assertTrue(current != before)
        assertFalse(restarted.hasPreparedRevision(id, before))
        assertEquals(WaveformPreparation.Ready, restarted.prepareInBackground(id, current))
        assertTrue(restarted.hasPreparedRevision(id, current))
    }

    @Test
    fun api29StampUsesDateSizeAndNullableDurationWithoutRequestingGeneration() {
        val file = folder.newFile().apply { writeBytes(ByteArray(42)) }
        val provider = WaveformTestProvider(file, durationMs = null)
        val resolver = ContentResolver.wrap(provider)
        val uri = ContentUris.withAppendedId(Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), 1)
        val revision = requireNotNull(resolver.readWaveformCatalogRevision(id, uri, CancellationSignal(), sdk = 29))
        assertEquals("external_primary:1|null|7|42|null", revision.request)
        assertFalse(Media.GENERATION_MODIFIED in provider.lastProjection)
    }

    private fun provider(): WaveformTestProvider {
        val file = folder.newFile("test.wav").apply { writeBytes(pcmWave(ShortArray(8_000))) }
        return WaveformTestProvider(file, durationMs = 1_000L)
    }
}
