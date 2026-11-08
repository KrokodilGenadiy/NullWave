package com.zaus.nullwave.feature.library.data.waveform

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDiskIOException
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore.Audio.Media
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.data.waveform.work.WaveformWorkCoordinator
import com.zaus.nullwave.feature.library.data.waveform.work.WaveformWorkQueue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaveformProviderFailureInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val folder = TemporaryFolder(instrumentation.targetContext.cacheDir)

    @Test
    fun providerOpenFailureIsLocalAndFailedDurationQueryUsesTheAudioTimeline() = runTest {
        val flac = instrumentation.context.assets.open("waveform/resume-levels.flac").use { it.readBytes() }
        // A zero total-samples field makes the FLAC extractor ask the source for a duration hint.
        flac[21] = (flac[21].toInt() and 0xf0).toByte()
        flac.fill(0, 22, 26)
        val file = folder.newFile("no-duration.flac").apply { writeBytes(flac) }
        val provider = FailingAudioProvider(file)
        val source = MediaStoreWaveformSource(ContentResolver.wrap(provider), Dispatchers.IO, folder.newFolder(), { true })
        val broken = TrackId("external_primary:1")
        val healthy = TrackId("external_primary:2")
        assertNull(source.load(broken))
        assertTrue(requireNotNull(source.load(healthy)).peaks.any { it > 0.5f })
        assertTrue(provider.failedHints > 0)

        val requests = listOf(broken, healthy).map { id ->
            waveformCatalogRequest(id, null, null, file.length(), null)
        }
        val worker = WaveformWorkCoordinator(backgroundScope, WaveformWorkQueue { _, _ -> error("Unexpected requeue") },
            { true }, source::hasPreparedRevision, source::prepareInBackground, elapsedMs = { 0L })
        worker.prepareWaveforms(requests)
        assertFalse(source.hasPreparedRevision(broken, requests.first()))
        assertTrue(source.hasPreparedRevision(healthy, requests.last()))
    }

    private class FailingAudioProvider(private val file: File) : ContentProvider() {
        var failedHints = 0
        override fun onCreate() = true
        override fun getType(uri: Uri) = "audio/flac"
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            if (uri.lastPathSegment == "1") throw SQLiteCantOpenDatabaseException("Missing row database")
            return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0L, file.length())
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = requireNotNull(projection)
            if (columns.size == 1 && columns[0] == Media.DURATION) {
                failedHints++
                throw SQLiteDiskIOException("Missing duration hint")
            }
            return MatrixCursor(columns).apply {
                addRow(columns.map { if (it == Media.SIZE) file.length() else null })
            }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri = error("Unexpected insert")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
            error("Unexpected update")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Unexpected delete")
    }
}
