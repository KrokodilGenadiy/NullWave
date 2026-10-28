package com.zaus.nullwave.feature.library.data.playback

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.library.data.mapping.artworkReferences
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import com.zaus.nullwave.feature.library.data.source.MediaStorePlaybackData
import com.zaus.nullwave.feature.library.data.source.MediaStorePlaybackSource
import com.zaus.nullwave.feature.library.data.source.MediaStoreTrackAddress
import com.zaus.nullwave.feature.library.data.source.audioProjection
import com.zaus.nullwave.feature.library.data.source.readPlaybackAudio
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Second-stage queries use the real resolver and Android cursor/signal plumbing without device media. */
@RunWith(AndroidJUnit4::class)
class MediaStorePlaybackAlbumQueryInstrumentedTest {
    private val firstAddress = MediaStoreTrackAddress("external_primary", 1)

    @Test
    fun cancellationDuringAlbumQueryClosesLateCursorWithoutPublishingAResult() = runBlocking {
        val target = cursor(row())
        val album = cursor(row())
        val entered = CompletableDeferred<CancellationSignal>()
        val signalled = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val queries = AtomicInteger()
        val resolver = resolver { _, _, _, signal ->
            if (queries.incrementAndGet() == 1) target else {
                assertTrue(target.isClosed)
                signal.setOnCancelListener {
                    signalled.complete(Unit)
                    release.countDown()
                }
                entered.complete(signal)
                check(release.await(10, TimeUnit.SECONDS)) { "Album query never received cancellation" }
                // The provider ignores cancellation and returns a cursor late. The helper must close it.
                album
            }
        }
        var published: AppResult<ResolvedPlaybackTrack, PlaybackSourceError>? = null
        val request = async { resolver.resolveTrack(firstAddress.trackId()).also { published = it } }
        try {
            withTimeout(5_000) {
                val albumSignal = entered.await()
                request.cancel()
                signalled.await()
                request.join()
                assertTrue(albumSignal.isCanceled)
            }
            assertEquals(2, queries.get())
            assertTrue(request.isCancelled)
            assertNull(published)
            assertTrue(target.isClosed)
            assertTrue(album.isClosed)
        } finally {
            release.countDown()
            request.cancel()
            try {
                withContext(NonCancellable) { withTimeout(5_000) { request.join() } }
            } finally {
                target.close()
                album.close()
            }
        }
    }

    @Test
    fun albumQueryFailureKeepsDiagnosticsAfterClosingTheTargetCursor() = runBlocking {
        val target = cursor(row())
        val problem = IOException("Album provider read failed")
        val queries = AtomicInteger()
        val resolver = resolver { _, _, _, _ ->
            if (queries.incrementAndGet() == 1) target else {
                assertTrue(target.isClosed)
                throw problem
            }
        }
        try {
            val failure = withTimeout(5_000) {
                resolver.resolveTrack(firstAddress.trackId()) as AppResult.Failure
            }
            assertEquals(2, queries.get())
            assertEquals(PlaybackSourceError.Io, failure.error)
            assertEquals(problem.message, failure.originalMessage)
            // Coroutine stack-trace recovery may wrap the exception across the worker boundary.
            assertTrue(generateSequence(failure.cause) { it.cause }.any { it === problem })
            assertTrue(target.isClosed)
        } finally {
            target.close()
        }
    }

    @Test
    fun albumCursorReadFailureClosesBothCursorsWithoutReturningAResource() = runBlocking {
        val target = cursor(row())
        val malformedAlbum = MatrixCursor(audioProjection).apply {
            addRow(arrayOf<Any?>(null, firstAddress.volume, "Title", "file.mp3", null, null, null, null, 7L, null, null, null))
        }
        val queries = AtomicInteger()
        val resolver = resolver { _, _, _, _ ->
            if (queries.incrementAndGet() == 1) target else {
                assertTrue(target.isClosed)
                malformedAlbum
            }
        }
        try {
            val failure = withTimeout(5_000) {
                resolver.resolveTrack(firstAddress.trackId()) as AppResult.Failure
            }
            assertEquals(2, queries.get())
            assertEquals(PlaybackSourceError.Io, failure.error)
            assertTrue(failure.cause is IOException)
            assertEquals(failure.cause?.message, failure.originalMessage)
            assertTrue(failure.originalMessage?.isNotBlank() == true)
            assertTrue(target.isClosed)
            assertTrue(malformedAlbum.isClosed)
        } finally {
            target.close()
            malformedAlbum.close()
        }
    }

    @Test
    fun cancellingOneAlbumQueryLeavesAnOverlappingResolveIndependent() = runBlocking {
        val secondAddress = MediaStoreTrackAddress("external_primary", 2)
        val firstRow = row()
        val secondRow = row(id = 2, albumId = 8, title = "Fresh second track")
        val secondMembers = listOf(secondRow, row(id = 3, albumId = 8, title = "Album neighbor"))
        val firstTarget = cursor(firstRow)
        val firstAlbum = cursor(firstRow)
        val secondTarget = cursor(secondRow.copy(title = "Earlier second track"))
        val secondAlbum = cursor(*secondMembers.toTypedArray())
        val firstEntered = CompletableDeferred<CancellationSignal>()
        val secondEntered = CompletableDeferred<CancellationSignal>()
        val firstCancelled = CompletableDeferred<Unit>()
        val secondCancelled = CompletableDeferred<Unit>()
        val releaseFirst = CountDownLatch(1)
        val releaseSecond = CountDownLatch(1)
        val firstQueries = AtomicInteger()
        val secondQueries = AtomicInteger()
        // Both requests use the same source and resolver; only each request's provider callback state differs.
        val resolver = resolver { address, _, _, signal ->
            when (address) {
                firstAddress -> if (firstQueries.incrementAndGet() == 1) firstTarget else {
                    assertTrue(firstTarget.isClosed)
                    signal.setOnCancelListener {
                        firstCancelled.complete(Unit)
                        releaseFirst.countDown()
                    }
                    firstEntered.complete(signal)
                    check(releaseFirst.await(10, TimeUnit.SECONDS)) { "First album query did not finish" }
                    firstAlbum
                }
                secondAddress -> if (secondQueries.incrementAndGet() == 1) secondTarget else {
                    assertTrue(secondTarget.isClosed)
                    signal.setOnCancelListener {
                        secondCancelled.complete(Unit)
                        releaseSecond.countDown()
                    }
                    secondEntered.complete(signal)
                    check(releaseSecond.await(10, TimeUnit.SECONDS)) { "Second album query did not finish" }
                    secondAlbum
                }
                else -> error("Unexpected track address: $address")
            }
        }
        var firstPublished: AppResult<ResolvedPlaybackTrack, PlaybackSourceError>? = null
        val first = async { resolver.resolveTrack(firstAddress.trackId()).also { firstPublished = it } }
        val second = async { resolver.resolveTrack(secondAddress.trackId()) }
        try {
            withTimeout(5_000) {
                val firstSignal = firstEntered.await()
                val secondSignal = secondEntered.await()
                assertNotSame(firstSignal, secondSignal)
                first.cancel()
                firstCancelled.await()
                first.join()
                assertTrue(firstSignal.isCanceled)
                assertFalse(secondSignal.isCanceled)
                assertFalse(secondCancelled.isCompleted)
                assertFalse(second.isCompleted)
                assertNull(firstPublished)
                assertTrue(firstTarget.isClosed)
                assertTrue(firstAlbum.isClosed)

                releaseSecond.countDown()
                val resolved = (second.await() as AppResult.Success).value
                assertEquals(secondAddress.trackId(), resolved.track.id)
                assertEquals("Fresh second track", resolved.track.title)
                assertEquals("content://media/external_primary/audio/media/2", resolved.resourceUri)
                assertEquals(secondMembers.artworkReferences().getValue("external_primary:8"), resolved.track.artwork)
                assertFalse(secondSignal.isCanceled)
            }
            assertEquals(2, firstQueries.get())
            assertEquals(2, secondQueries.get())
            assertTrue(first.isCancelled)
            assertTrue(secondTarget.isClosed)
            assertTrue(secondAlbum.isClosed)
        } finally {
            releaseFirst.countDown()
            releaseSecond.countDown()
            first.cancel()
            second.cancel()
            try {
                withContext(NonCancellable) {
                    withTimeout(5_000) {
                        first.join()
                        second.join()
                    }
                }
            } finally {
                listOf(firstTarget, firstAlbum, secondTarget, secondAlbum).forEach { it.close() }
            }
        }
    }

    private fun resolver(
        query: (MediaStoreTrackAddress, String, Array<String>, CancellationSignal) -> Cursor?,
    ): MediaStorePlaybackSourceResolver {
        val source = object : MediaStorePlaybackSource {
            override fun hasReadAccess() = true
            override suspend fun readTrack(address: MediaStoreTrackAddress): MediaStorePlaybackData? =
                readPlaybackAudio(address, Dispatchers.IO, sdk = 30) { selection, arguments, signal ->
                    query(address, selection, arguments, signal)
                }
        }
        return MediaStorePlaybackSourceResolver(source, Dispatchers.IO)
    }

    private fun MediaStoreTrackAddress.trackId() = TrackId("$volume:$id")

    private fun row(id: Long = 1, albumId: Long = 7, title: String = "First track") = MediaStoreAudioRow(
        id = id,
        volume = firstAddress.volume,
        title = title,
        displayName = "track-$id.mp3",
        artist = "Artist",
        album = "Album $albumId",
        durationMs = 180_000,
        dateAddedSeconds = 1,
        albumId = albumId,
        sizeBytes = id * 100,
        dateModifiedSeconds = id,
        artistId = 3,
    )

    private fun cursor(vararg rows: MediaStoreAudioRow): MatrixCursor = MatrixCursor(audioProjection).apply {
        for (row in rows) addRow(arrayOf<Any?>(
            row.id, row.volume, row.title, row.displayName, row.artist, row.album,
            row.durationMs, row.dateAddedSeconds, row.albumId, row.sizeBytes, row.dateModifiedSeconds, row.artistId,
        ))
    }
}
