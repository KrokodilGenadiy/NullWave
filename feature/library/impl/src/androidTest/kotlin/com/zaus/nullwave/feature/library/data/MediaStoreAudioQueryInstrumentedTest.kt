package com.zaus.nullwave.feature.library.data

import android.database.MatrixCursor
import android.database.sqlite.SQLiteDiskIOException
import android.os.OperationCanceledException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.LibraryQuery
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
import org.junit.Test
import org.junit.runner.RunWith

/** Uses Android Cursor/CancellationSignal without reading, modifying or requesting access to media. */
@RunWith(AndroidJUnit4::class)
class MediaStoreAudioQueryInstrumentedTest {
    @Test
    fun readsLongAndNullableColumnsAndClosesCursor() = runBlocking {
        val cursor = MatrixCursor(audioProjection).apply {
            addRow(arrayOf<Any?>(4_000_000_001L, "external_primary", "Title", "file.mp3", null, null, null,
                2_000_000_000L, 5_000_000_001L, 6_000_000_001L, 2_000_000_001L))
            addRow(arrayOf<Any?>(2L, "external_primary", "Other", "other.mp3", null, null, null, null, null, null, null))
        }
        val rows = queryAudioRows(Dispatchers.IO) { cursor }
        assertEquals(4_000_000_001L, rows.first().id)
        assertEquals(null, rows.first().durationMs)
        assertEquals(2_000_000_000L, rows.first().dateAddedSeconds)
        assertEquals(5_000_000_001L, rows.first().albumId)
        assertEquals(6_000_000_001L, rows.first().sizeBytes)
        assertEquals(2_000_000_001L, rows.first().dateModifiedSeconds)
        assertEquals(null, rows.last().albumId)
        assertEquals(null, rows.last().sizeBytes)
        assertEquals(null, rows.last().dateModifiedSeconds)
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
                override suspend fun readRows(): List<MediaStoreAudioRow> = queryAudioRows(Dispatchers.IO) { throw problem }
            }
            val result = MediaStoreLibraryRepository(source, Dispatchers.IO)
                .observeTracks(LibraryQuery("en")).first() as AppResult.Failure
            assertEquals(DataError.Io, result.error)
            assertTrue(generateSequence(result.cause) { it.cause }.any { it === problem })
            assertEquals(problem.message, result.originalMessage)
        }
    }
}
