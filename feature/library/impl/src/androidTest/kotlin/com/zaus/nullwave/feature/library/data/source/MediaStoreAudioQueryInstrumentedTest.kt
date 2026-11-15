package com.zaus.nullwave.feature.library.data.source

import android.database.MatrixCursor
import android.database.sqlite.SQLiteDiskIOException
import android.os.OperationCanceledException
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.data.repository.MediaStoreLibraryRepository
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test

/** Uses Android Cursor/CancellationSignal without reading, modifying or requesting access to media. */
@RunWith(AndroidJUnit4::class)
class MediaStoreAudioQueryInstrumentedTest {
    @Test
    fun ordinaryAlbumQueryBindsVolumeAndProviderIdAndReadsPackedTrackOnAndroidTen() = runBlocking {
        val rows = queryCatalogRows(AlbumId("card:37"), 29, Dispatchers.IO) { columns, selection, args, _ ->
            assertEquals("(${Media.IS_PENDING} = 0) AND ${Media.VOLUME_NAME} = ? AND ${Media.ALBUM_ID} = ?", selection)
            assertEquals(listOf("card", "37"), args?.toList())
            MatrixCursor(columns).apply {
                newRow().add(Media._ID, 1L).add(Media.TRACK, 2010)
                newRow().add(Media._ID, 2L)
            }
        }
        assertEquals(listOf(2010L, null), rows.map { it.trackNumber })
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun downloadQueryReadsEligibleRowsOnOneVolumeWithGroupingAndOrderingColumns() = runBlocking {
        val rows = queryCatalogRows(AlbumId("external_primary:folder/Download"), 30, Dispatchers.IO) { columns, selection, args, _ ->
            assertEquals("(${Media.IS_PENDING} = 0 AND ${Media.IS_TRASHED} = 0) AND ${Media.VOLUME_NAME} = ?", selection)
            assertEquals(listOf("external_primary"), args?.toList())
            MatrixCursor(columns).apply {
                for (index in 1..9) {
                    newRow().add(Media._ID, index).add(Media.VOLUME_NAME, "external_primary")
                        .add(Media.ALBUM_ID, if (index == 1) 37 else 52).add(Media.ALBUM, "Download")
                        .add(Media.RELATIVE_PATH, "Download/").add(Media.ALBUM_ARTIST, null).add(Media.TRACK, 1000 + index)
                }
            }
        }
        assertEquals(listOf(37L, 52L), rows.map { it.albumId }.distinct())
        assertEquals((1001L..1009L).toList(), rows.map { it.trackNumber })
        assertTrue(rows.all { it.relativePath == "Download/" && it.albumArtist == null })
    }

    @Test
    fun readsLongAndNullableColumnsAndClosesCursor() = runBlocking {
        val cursor = MatrixCursor(audioProjection).apply {
            addRow(arrayOf<Any?>(4_000_000_001L, "external_primary", "Title", "file.mp3", null, null, null,
                2_000_000_000L, 5_000_000_001L, 6_000_000_001L, 2_000_000_001L, 7_000_000_001L))
            addRow(arrayOf<Any?>(2L, "external_primary", "Other", "other.mp3", null, null, null, null, null, null, null, null))
        }
        val rows = queryAudioRows(Dispatchers.IO) { cursor }
        assertEquals(4_000_000_001L, rows.first().id)
        assertEquals(null, rows.first().durationMs)
        assertEquals(2_000_000_000L, rows.first().dateAddedSeconds)
        assertEquals(5_000_000_001L, rows.first().albumId)
        assertEquals(6_000_000_001L, rows.first().sizeBytes)
        assertEquals(2_000_000_001L, rows.first().dateModifiedSeconds)
        assertEquals(7_000_000_001L, rows.first().artistId)
        assertEquals(null, rows.first().generationModified)
        assertEquals(null, rows.last().albumId)
        assertEquals(null, rows.last().sizeBytes)
        assertEquals(null, rows.last().dateModifiedSeconds)
        assertEquals(null, rows.last().artistId)
        assertTrue(cursor.isClosed)
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun catalogReadsGenerationFolderAndAlbumArtistFromTheirColumns() = runBlocking {
        val cursor = MatrixCursor(catalogProjection(30)).apply {
            newRow().add(MediaStore.MediaColumns._ID, 23L)
                .add(MediaStore.MediaColumns.GENERATION_MODIFIED, 5_000_000_007L)
                .add(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
                .add(MediaStore.MediaColumns.ALBUM_ARTIST, "Album credit")
        }
        val row = queryAudioRows(Dispatchers.IO) { cursor }.single()
        assertEquals(5_000_000_007L, row.generationModified)
        assertEquals("Download/", row.relativePath)
        assertEquals("Album credit", row.albumArtist)
        assertTrue(cursor.isClosed)
    }

    @Test
    fun androidTenCatalogReadsWithoutAnAlbumArtistColumn() = runBlocking {
        val projection = catalogProjection(29)
        assertFalse("album_artist" in projection)
        val cursor = MatrixCursor(projection).apply {
            newRow().add(MediaStore.MediaColumns._ID, 23L)
        }
        val row = queryAudioRows(Dispatchers.IO) { cursor }.single()
        assertEquals(null, row.relativePath)
        assertEquals(null, row.albumArtist)
        assertTrue(cursor.isClosed)
    }

    @Test
    fun emptyCursorIsSuccessButNullCursorIsFailure() = runBlocking {
        val cursor = MatrixCursor(audioProjection)
        assertTrue(queryAudioRows(Dispatchers.IO) { cursor }.isEmpty())
        assertTrue(cursor.isClosed)
        val problem = runCatching { queryAudioRows(Dispatchers.IO) { null } }.exceptionOrNull()
        assertTrue(problem is IOException)
    }

    @Test
    fun closesCursorWhenProjectionIsInvalid() = runBlocking {
        val cursor = MatrixCursor(arrayOf("wrong_column"))
        val problem = runCatching { queryAudioRows(Dispatchers.IO) { cursor } }.exceptionOrNull()
        assertTrue(problem is IllegalArgumentException)
        assertTrue(cursor.isClosed)
    }

    @Test
    fun cancellationSignalsBlockedQueryAndWaitsForCursorCleanup() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val signalled = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val cursor = MatrixCursor(audioProjection)
        val reading = launch {
            queryAudioRows(Dispatchers.IO) { cancellation ->
                cancellation.setOnCancelListener { signalled.complete(Unit) }
                entered.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS)) { "Test did not release query" }
                cursor
            }
        }
        try {
            withTimeout(5_000) { entered.await() }
            reading.cancel()
            withTimeout(5_000) { signalled.await() }
            assertFalse(reading.isCompleted)
        } finally {
            release.countDown()
            withTimeout(5_000) { reading.join() }
        }
        assertTrue(reading.isCancelled)
        assertTrue(cursor.isClosed)
    }

    @Test
    fun providerCancellationAndDiskFailureRetainOriginalErrorAsRetryableIo() = runBlocking {
        for (problem in listOf(OperationCanceledException("provider cancelled"), SQLiteDiskIOException("read failed"))) {
            val source = object : MediaStoreAudioSource {
                override fun hasReadAccess() = true
                override fun observeChanges(onChanged: () -> Unit) = AutoCloseable { }
                override suspend fun readRows(albumId: AlbumId?): List<MediaStoreAudioRow> = queryAudioRows(Dispatchers.IO) { throw problem }
            }
            val result = MediaStoreLibraryRepository(source, Dispatchers.IO)
                .observeTracks(LibraryTrackQuery("en")).first() as AppResult.Failure
            assertEquals(DataError.Io, result.error)
            assertTrue(generateSequence(result.cause) { it.cause }.any { it === problem })
            assertEquals(problem.message, result.originalMessage)
        }
    }
}
