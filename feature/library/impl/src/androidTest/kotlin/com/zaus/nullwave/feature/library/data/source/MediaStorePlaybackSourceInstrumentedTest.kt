package com.zaus.nullwave.feature.library.data.source

import android.database.MatrixCursor
import android.os.OperationCanceledException
import android.provider.MediaStore.Audio.Media
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android cursor/signal/URI plumbing with a local query callback; no device media is changed. */
@RunWith(AndroidJUnit4::class)
class MediaStorePlaybackSourceInstrumentedTest {
    private val address = MediaStoreTrackAddress("external_primary", 4_294_967_296L)

    @Test
    fun targetAndAlbumQueriesBindBothIdentitiesAndPreserveAllAlbumMembers() = runTest {
        val target = row()
        val refreshed = target.copy(title = "Fresh title")
        val members = listOf(refreshed, target.copy(id = 8, title = "Another track"))
        val cursors = mutableListOf<MatrixCursor>()
        var queries = 0
        val data = readPlaybackAudio(address, StandardTestDispatcher(testScheduler), sdk = 30) { selection, args, _ ->
            assertTrue(selection.contains("${Media.IS_PENDING} = 0"))
            assertTrue(selection.contains("${Media.IS_TRASHED} = 0"))
            queries++
            val result = if (queries == 1) {
                assertTrue(selection.endsWith("${Media._ID} = ? AND ${Media.VOLUME_NAME} = ?"))
                assertEquals(listOf(address.id.toString(), address.volume), args.toList())
                cursor(target)
            } else {
                assertEquals(2, queries)
                assertTrue(selection.endsWith("${Media.ALBUM_ID} = ? AND ${Media.VOLUME_NAME} = ?"))
                assertEquals(listOf("7", address.volume), args.toList())
                cursor(*members.toTypedArray())
            }
            cursors += result
            result
        }
        assertEquals(2, queries)
        assertEquals(refreshed, data?.row)
        assertEquals(members, data?.albumRows)
        assertEquals("content://media/external_primary/audio/media/4294967296", data?.resourceUri)
        assertTrue(cursors.all { it.isClosed })
    }

    @Test
    fun missingRowReturnsNullAndClosesCursorWithoutAlbumQuery() = runTest {
        val empty = cursor()
        var queries = 0
        val data = readPlaybackAudio(address, StandardTestDispatcher(testScheduler), 29) { selection, _, _ ->
            queries++
            assertFalse(selection.contains(Media.IS_TRASHED))
            empty
        }
        assertNull(data)
        assertEquals(1, queries)
        assertTrue(empty.isClosed)
    }

    @Test
    fun absentAlbumIdentityNeedsOnlyOneQuery() = runTest {
        val target = row().copy(albumId = null)
        var queries = 0
        val data = readPlaybackAudio(address, StandardTestDispatcher(testScheduler), 30) { _, _, _ ->
            queries++
            cursor(target)
        }
        assertEquals(1, queries)
        assertEquals(target, data?.row)
        assertTrue(checkNotNull(data).albumRows.isEmpty())
    }

    @Test
    fun nullCursorIsIoFailureRatherThanMissingTrack() {
        assertThrows(IOException::class.java) {
            runBlocking { readPlaybackAudio(address, Dispatchers.IO, 30) { _, _, _ -> null } }
        }
    }

    @Test
    fun wrongOrDuplicateTargetRowsAreRejectedAndTheirCursorsClose() {
        for (rows in listOf(
            listOf(row().copy(id = 6)),
            listOf(row().copy(volume = "another_volume")),
            listOf(row(), row()),
        )) {
            val result = cursor(*rows.toTypedArray())
            assertThrows(IOException::class.java) {
                runBlocking { readPlaybackAudio(address, Dispatchers.IO, 30) { _, _, _ -> result } }
            }
            assertTrue(result.isClosed)
        }
    }

    @Test
    fun inconsistentAlbumMembershipIsRejectedWithoutPublishingPartialArtwork() {
        for (rows in listOf(
            emptyList(),
            listOf(row().copy(id = 8)),
            listOf(row(), row()),
            listOf(row().copy(volume = "another_volume")),
            listOf(row().copy(albumId = 9)),
            listOf(row(), row().copy(id = -1)),
        )) {
            val target = cursor(row())
            val album = cursor(*rows.toTypedArray())
            var queries = 0
            assertThrows(IOException::class.java) {
                runBlocking {
                    readPlaybackAudio(address, Dispatchers.IO, 30) { _, _, _ ->
                        if (++queries == 1) target else album
                    }
                }
            }
            assertEquals(2, queries)
            assertTrue(target.isClosed)
            assertTrue(album.isClosed)
        }
    }

    @Test
    fun cursorClosesWhenProviderDataFailsDuringReading() {
        val malformed = MatrixCursor(audioProjection).apply {
            addRow(arrayOf<Any?>(null, address.volume, "Title", "file.mp3", null, null, null, null, null, null, null, null))
        }
        assertThrows(IOException::class.java) {
            runBlocking { readPlaybackAudio(address, Dispatchers.IO, 30) { _, _, _ -> malformed } }
        }
        assertTrue(malformed.isClosed)
    }

    @Test
    fun callerCancellationSignalsTheBlockingProviderBeforeJoiningItsWorker() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val signalled = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val job = launch {
            readPlaybackAudio(address, Dispatchers.IO, 30) { _, _, signal ->
                signal.setOnCancelListener {
                    signalled.complete(Unit)
                    release.countDown()
                }
                entered.complete(Unit)
                check(release.await(10, TimeUnit.SECONDS)) { "Provider never received cancellation" }
                signal.throwIfCanceled()
                error("A cancelled query must not return a cursor")
            }
        }
        try {
            withTimeout(5_000) {
                entered.await()
                job.cancelAndJoin()
                signalled.await()
            }
            assertTrue(job.isCancelled)
        } finally {
            release.countDown()
            job.cancelAndJoin()
        }
    }

    @Test
    fun providerInitiatedCancellationRemainsDistinctFromCallerCancellation() {
        assertThrows(OperationCanceledException::class.java) {
            runBlocking {
                readPlaybackAudio(address, Dispatchers.IO, 30) { _, _, _ ->
                    throw OperationCanceledException("Provider stopped its query")
                }
            }
        }
    }

    private fun row() = MediaStoreAudioRow(
        id = address.id,
        volume = address.volume,
        title = "Track title",
        displayName = "track.mp3",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000,
        dateAddedSeconds = 1,
        albumId = 7,
        sizeBytes = 200,
        dateModifiedSeconds = 2,
        artistId = 3,
    )

    private fun cursor(vararg rows: MediaStoreAudioRow): MatrixCursor = MatrixCursor(audioProjection).apply {
        for (row in rows) addRow(arrayOf<Any?>(
            row.id, row.volume, row.title, row.displayName, row.artist, row.album,
            row.durationMs, row.dateAddedSeconds, row.albumId, row.sizeBytes, row.dateModifiedSeconds, row.artistId,
        ))
    }
}
