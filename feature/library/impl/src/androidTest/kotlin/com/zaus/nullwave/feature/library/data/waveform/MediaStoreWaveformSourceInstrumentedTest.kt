package com.zaus.nullwave.feature.library.data.waveform

import android.content.ContentUris
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.media.waveform.Waveform
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreWaveformSourceInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val volume = MediaStore.VOLUME_EXTERNAL_PRIMARY

    @get:Rule val folder = TemporaryFolder(context.cacheDir)

    @Test
    fun freshSourcesReuseOnlyReadableUnchangedFilesAndRebuildCorruptEntries() = runBlocking {
        val calls = AtomicInteger()
        val allowed = AtomicBoolean(true)
        fun newSource() = source(hasAccess = allowed::get) { file, hint ->
            calls.incrementAndGet()
            AndroidWaveformDecoder.decode(file, hint)
        }
        val first = newSource()
        val deletedId = withTrack { id, uri ->
            val silent = requireNotNull(first.load(id))
            assertTrue(silent.peaks.all { it == 0f })
            assertSame(silent, first.load(id))
            assertEquals(silent, newSource().load(id))
            assertEquals(1, calls.get())

            allowed.set(false)
            assertNull(first.load(id))
            assertNull(newSource().load(id))
            allowed.set(true)
            writeAudio(uri, 24_576)
            val changed = requireNotNull(first.load(id))
            assertTrue(changed.peaks.all { it in 0.74f..0.76f })
            assertEquals(2, calls.get())

            val cacheFile = folder.root.listFiles().orEmpty().single { it.extension == "wave" }
            val damaged = cacheFile.readBytes()
            damaged[100] = (damaged[100].toInt() xor 1).toByte()
            cacheFile.writeBytes(damaged)
            assertEquals(changed, newSource().load(id))
            assertEquals(3, calls.get())
            id
        }
        assertNull(first.load(deletedId))
        assertNull(newSource().load(deletedId))
    }

    @Test
    fun losingAccessDuringDecodeDiscardsTheResultAndAllowsRetry() = runBlocking {
        withTrack { id, _ ->
            val allowed = AtomicBoolean(true)
            val calls = AtomicInteger()
            val source = source(hasAccess = allowed::get) { file, hint ->
                AndroidWaveformDecoder.decode(file, hint).also {
                    if (calls.incrementAndGet() == 1) allowed.set(false)
                }
            }
            assertNull(source.load(id))
            assertFalse(source.hasDiskEntry(id))
            allowed.set(true)
            assertTrue(requireNotNull(source.load(id)).peaks.all { it == 0f })
            assertTrue(source.hasDiskEntry(id))
            assertEquals(2, calls.get())
        }
    }

    @Test
    fun sameTrackWaitsForOneDecodeWhileAnotherTrackCanFinishIndependently() = runBlocking {
        withTrack { backgroundId, backgroundUri ->
            withTrack { selectedId, _ ->
                val calls = AtomicInteger()
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val source = source { file, hint ->
                    if (calls.incrementAndGet() == 1) {
                        started.complete(Unit)
                        release.await()
                    }
                    AndroidWaveformDecoder.decode(file, hint)
                }
                withTimeout(10_000) {
                    val background = async { source.load(backgroundId) }
                    started.await()
                    val revision = catalogRevision(backgroundId, backgroundUri)
                    assertEquals(WaveformPreparation.Busy, source.prepareInBackground(backgroundId, revision))
                    val cancelledWaiter = async(start = CoroutineStart.UNDISPATCHED) { source.load(backgroundId) }
                    val otherWaiter = async(start = CoroutineStart.UNDISPATCHED) { source.load(backgroundId) }
                    cancelledWaiter.cancelAndJoin()
                    assertTrue(requireNotNull(source.load(selectedId)).peaks.all { it == 0f })
                    assertFalse(background.isCompleted)
                    assertFalse(otherWaiter.isCompleted)
                    release.complete(Unit)
                    assertEquals(requireNotNull(background.await()), otherWaiter.await())
                    assertEquals(2, calls.get())
                }
            }
        }
    }

    @Test
    fun cancelledDecodeLeavesOnlyItsPrivateCheckpointAndWaitingCallerCanFinish() = runBlocking {
        withTrack { id, _ ->
            val calls = AtomicInteger()
            val started = CompletableDeferred<Unit>()
            val clock = AtomicLong()
            val source = MediaStoreWaveformSource(resolver, Dispatchers.IO, folder.root, { true },
                clockMs = clock::get) { file, hint, session ->
                if (calls.incrementAndGet() == 1) {
                    val peaks = PcmPeaks(1_000_000)
                    peaks.add(ByteBuffer.allocate(2), 0, PcmFormat(PcmEncoding.Signed16, 1, 8_000))
                    clock.addAndGet(30_000)
                    assertTrue(session.afterPacket(100, 1_000_000, peaks))
                    started.complete(Unit)
                    awaitCancellation()
                }
                assertEquals(100L, requireNotNull(session.previous).positionUs)
                AndroidWaveformDecoder.decode(file, hint, session)
            }
            withTimeout(10_000) {
                val background = async { source.load(id) }
                started.await()
                assertFalse(source.hasDiskEntry(id))
                val waiting = async(start = CoroutineStart.UNDISPATCHED) { source.load(id) }
                background.cancelAndJoin()
                val waveform = requireNotNull(waiting.await())
                assertTrue(source.hasDiskEntry(id))
                val cold = source { _, _ -> error("Expected the completed disk entry") }
                assertEquals(waveform, cold.load(id))
                assertEquals(2, calls.get())
            }
        }
    }

    @Test
    fun waitingCallerReadsTheReplacementWhenTheFileChangesDuringDecode() = runBlocking {
        withTrack { id, uri ->
            val measured = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            val source = source { file, hint ->
                AndroidWaveformDecoder.decode(file, hint).also {
                    if (calls.incrementAndGet() == 1) {
                        measured.complete(Unit)
                        release.await()
                    }
                }
            }
            withTimeout(10_000) {
                val old = async { source.load(id) }
                measured.await()
                val next = async(start = CoroutineStart.UNDISPATCHED) { source.load(id) }
                writeAudio(uri, 24_576)
                release.complete(Unit)
                assertNull(old.await())
                assertTrue(requireNotNull(next.await()).peaks.all { it in 0.74f..0.76f })
                assertEquals(2, calls.get())
            }
        }
    }

    @Test
    fun workerSliceWithoutFurtherProgressKeepsItsPreviousCheckpointForAFreshPlayer() = runBlocking {
        val audio = InstrumentationRegistry.getInstrumentation().context.assets
            .open("waveform/quarter-tones.mp3").use { it.readBytes() }
        withTrack(audio, extension = "mp3", mimeType = "audio/mpeg") { id, uri ->
            val revision = catalogRevision(id, uri)
            val expected = requireNotNull(source(directory = folder.newFolder()).load(id)) {
                "Expected a complete waveform for the published MP3"
            }
            val clock = AtomicLong()
            val worker = MediaStoreWaveformSource(resolver, Dispatchers.IO, folder.root, { true },
                clockMs = { clock.addAndGet(25_000L) })
            assertEquals(WaveformPreparation.Incomplete, worker.prepareInBackground(id, revision))
            assertFalse(worker.hasDiskEntry(id))
            val stalledWorker = MediaStoreWaveformSource(resolver, Dispatchers.IO, folder.root, { true },
                clockMs = clock::get) { _, _, session ->
                assertTrue(requireNotNull(session.previous).positionUs > 0)
                clock.addAndGet(90_000)
                assertFalse(session.withinTimeLimit())
                null
            }
            assertEquals(WaveformPreparation.Unavailable, stalledWorker.prepareInBackground(id, revision))
            val player = MediaStoreWaveformSource(resolver, Dispatchers.IO, folder.root, { true }) { file, hint, session ->
                assertTrue("Expected an on-disk checkpoint", requireNotNull(session.previous).positionUs > 0)
                AndroidWaveformDecoder.decode(file, hint, session)
            }
            assertEquals(expected, player.load(id))
            assertTrue(player.hasDiskEntry(id))
        }
    }

    @Test
    fun storageFailureStillReturnsMeasuredPeaks() = runBlocking {
        val source = source(directory = folder.newFile("not-a-directory"))
        withTrack { id, _ ->
            assertTrue(requireNotNull(source.load(id)).peaks.all { it == 0f })
            assertFalse(source.hasDiskEntry(id))
        }
    }

    @Test
    fun coldWorkerAdoptsUnstampedPeaksAndOpeningPlayerPreservesTheCatalogueStamp() = runBlocking {
        withTrack { id, uri ->
            val revision = catalogRevision(id, uri)
            val peaks = requireNotNull(source().load(id))
            val worker = source { _, _ -> error("Physical file already has peaks") }
            assertFalse(worker.hasPreparedRevision(id, revision))
            assertEquals(WaveformPreparation.Ready, worker.prepareInBackground(id, revision))
            val player = source { _, _ -> error("Expected verified disk entry") }
            assertEquals(peaks, player.load(id))
            assertTrue(player.hasPreparedRevision(id, revision))
        }
    }

    @Test
    fun failedOverwriteKeepsTheCheckpointAndMemoryRepairsTheOldEntryAfterStorageRecovers() = runBlocking {
        val cache = folder.newFolder("waveforms")
        val mode = Os.stat(cache.path).st_mode
        val clock = AtomicLong()
        val calls = AtomicInteger()
        val source = MediaStoreWaveformSource(resolver, Dispatchers.IO, cache, { true }, clockMs = clock::get) {
            file, hint, session ->
            val complete = requireNotNull(AndroidWaveformDecoder.decode(file, hint))
            if (calls.incrementAndGet() == 2) {
                val partial = PcmPeaks(1_000_000)
                partial.add(ByteBuffer.allocate(2), 100, PcmFormat(PcmEncoding.Signed16, 1, 8_000))
                clock.addAndGet(30_000)
                assertTrue(session.afterPacket(100, 1_000_000, partial))
                assertTrue(cache.listFiles().orEmpty().any { it.extension == "part" })
                Os.chmod(cache.path, OsConstants.S_IRUSR or OsConstants.S_IXUSR)
            }
            complete
        }
        withTrack { id, uri ->
            val old = catalogRevision(id, uri)
            assertEquals(WaveformPreparation.Ready, source.prepareInBackground(id, old))
            assertTrue(source.hasPreparedRevision(id, old))
            assertEquals(1, resolver.update(uri, ContentValues().apply { put(Media.IS_PENDING, 1) }, null, null))
            writeAudio(uri, amplitude = 24_576, samples = 16_000)
            assertEquals(1, resolver.update(uri, ContentValues().apply { put(Media.IS_PENDING, 0) }, null, null))
            val new = catalogRevision(id, uri)
            assertTrue(new != old)
            try {
                assertEquals(WaveformPreparation.Unavailable, source.prepareInBackground(id, new))
                assertTrue(requireNotNull(source.load(id)).peaks.all { it in 0.74f..0.76f })
                assertTrue(source.hasPreparedRevision(id, old))
                assertFalse(source.hasPreparedRevision(id, new))
                assertTrue(cache.listFiles().orEmpty().any { it.extension == "part" })
            } finally {
                Os.chmod(cache.path, mode)
            }
            assertEquals(WaveformPreparation.Ready, source.prepareInBackground(id, new))
            assertTrue(source.hasPreparedRevision(id, new))
            assertFalse(source.hasPreparedRevision(id, old))
            assertFalse(cache.listFiles().orEmpty().any { it.extension == "part" })
            assertEquals(2, calls.get())
            val cold = source(directory = cache) { _, _ -> error("Expected repaired disk entry") }
            assertTrue(requireNotNull(cold.load(id)).peaks.all { it in 0.74f..0.76f })
        }
    }

    private fun source(
        directory: File = folder.root,
        hasAccess: () -> Boolean = { true },
        decode: suspend (AssetFileDescriptor, () -> Long?) -> Waveform? = { file, hint ->
            AndroidWaveformDecoder.decode(file, hint)
        },
    ) = MediaStoreWaveformSource(resolver, Dispatchers.IO, directory, hasAccess) { file, hint, _ -> decode(file, hint) }

    private suspend fun <T> withTrack(
        audio: ByteArray = pcmWave(ShortArray(8_000)),
        extension: String = "wav",
        mimeType: String = "audio/wav",
        block: suspend (TrackId, Uri) -> T,
    ): T {
        val values = ContentValues().apply {
            put(Media.DISPLAY_NAME, "nullwave-waveform-${UUID.randomUUID()}.$extension")
            put(Media.MIME_TYPE, mimeType)
            put(Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(Media.getContentUri(volume), values))
        try {
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(audio) }
            assertEquals(1, resolver.update(uri, ContentValues().apply { put(Media.IS_PENDING, 0) }, null, null))
            return block(TrackId("${volume}:${ContentUris.parseId(uri)}"), uri)
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    private fun catalogRevision(id: TrackId, uri: Uri): String =
        requireNotNull(resolver.readWaveformCatalogRevision(id, uri, CancellationSignal())).request

    private fun writeAudio(uri: Uri, amplitude: Short, samples: Int = 8_000) {
        requireNotNull(resolver.openOutputStream(uri, "wt")).use {
            it.write(pcmWave(ShortArray(samples) { amplitude }))
        }
    }
}
