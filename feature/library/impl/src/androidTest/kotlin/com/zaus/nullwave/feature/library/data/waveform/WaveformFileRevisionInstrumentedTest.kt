package com.zaus.nullwave.feature.library.data.waveform

import android.content.ContentResolver
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.media.waveform.Waveform
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaveformFileRevisionInstrumentedTest {
    @get:Rule val folder = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
    private val track = TrackId("external_primary:1")
    private val discardedPeaks = Waveform.of(List(WaveformBinCount) { 0.9f })
    private val stablePeaks = Waveform.of(List(WaveformBinCount) { 0.25f })

    @Test
    fun ctimeChangeDiscardsTheResultUntilTheNextExplicitLoad() = runBlocking {
        val audio = audioFile()
        val resolver = ContentResolver.wrap(WaveformTestProvider(audio, durationMs = 1_000))
        val cache = folder.newFolder()
        var calls = 0
        val source = MediaStoreWaveformSource(resolver, Dispatchers.IO, cache, { true }) { file, hint, session ->
            calls++
            assertEquals(0L, Os.lseek(file.fileDescriptor, 0, OsConstants.SEEK_CUR))
            if (calls == 1) {
                changeCtime(audio)
                discardedPeaks
            } else {
                AndroidWaveformDecoder.decode(file, hint, session)
            }
        }

        assertNull(source.load(track))
        assertEquals(1, calls)
        assertFalse(source.hasDiskEntry(track))
        assertEquals(stablePeaks, source.load(track))
        assertEquals(2, calls)
        assertEquals(stablePeaks, source.load(track))
        assertEquals(2, calls)
        val reopened = MediaStoreWaveformSource(resolver, Dispatchers.IO, cache, { true }) { _, _, _ ->
            throw AssertionError("Expected the stable result on disk")
        }
        assertEquals(stablePeaks, reopened.load(track))
    }

    private fun audioFile(): File = folder.newFile("test.wav").apply {
        writeBytes(pcmWave(ShortArray(8_000) { 8_192 }))
    }

    private fun changeCtime(file: File) {
        val before = Os.stat(file.path)
        val mode = before.st_mode xor OsConstants.S_IXUSR
        val deadline = SystemClock.elapsedRealtime() + 2_000
        do {
            Os.chmod(file.path, mode)
            val after = Os.stat(file.path)
            if (after.st_ctim.tv_sec != before.st_ctim.tv_sec || after.st_ctim.tv_nsec != before.st_ctim.tv_nsec) {
                assertEquals(before.st_ino, after.st_ino)
                assertEquals(before.st_size, after.st_size)
                assertEquals(before.st_mtim.tv_sec, after.st_mtim.tv_sec)
                assertEquals(before.st_mtim.tv_nsec, after.st_mtim.tv_nsec)
                return
            }
            // A second chmod can land in the same filesystem clock tick.
            SystemClock.sleep(5)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("chmod did not change the fixture ctime")
    }
}
