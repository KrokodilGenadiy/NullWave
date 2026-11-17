package com.zaus.nullwave.feature.library.data.playback

import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.os.OperationCanceledException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.library.data.source.MediaStorePlaybackData
import com.zaus.nullwave.feature.library.data.source.MediaStorePlaybackSource
import com.zaus.nullwave.feature.library.data.source.MediaStoreTrackAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real framework exception classes that local JVM Android stubs cannot construct. */
@RunWith(AndroidJUnit4::class)
class MediaStorePlaybackSourceResolverInstrumentedTest {
    private val factories: List<(String) -> Exception> = listOf(
        ::SQLiteException,
        ::SQLiteDatabaseLockedException,
        ::SQLiteDiskIOException,
        ::SQLiteCantOpenDatabaseException,
        ::SQLiteDatabaseCorruptException,
        ::OperationCanceledException,
    )

    @Test
    fun androidProviderFailuresRespectRevocationAndRetainOriginalDiagnostics() = runTest {
        for (factory in factories) for (revoked in listOf(false, true)) {
            val problem = factory("Provider read failed")
            var granted = true
            val source = object : MediaStorePlaybackSource {
                override fun hasReadAccess(): Boolean = granted
                override suspend fun readTrack(address: MediaStoreTrackAddress): MediaStorePlaybackData? {
                    granted = !revoked
                    throw problem
                }
            }
            val resolver = MediaStorePlaybackSourceResolver(source, StandardTestDispatcher(testScheduler))
            val failure = resolver.resolveTrack(TrackId("external_primary:1")) as AppResult.Failure
            if (revoked) assertTrue(problem.javaClass.name, failure.error is PlaybackSourceError.AccessDenied)
            else assertEquals(problem.javaClass.name, PlaybackSourceError.Io, failure.error)
            assertSame(problem, failure.cause)
            assertEquals(problem.message, failure.originalMessage)
        }
    }

    @Test
    fun callerCancellationTakesPriorityOverEveryAndroidProviderFailure() = runTest {
        for (factory in factories) {
            val source = object : MediaStorePlaybackSource {
                override fun hasReadAccess(): Boolean = true
                override suspend fun readTrack(address: MediaStoreTrackAddress): MediaStorePlaybackData? {
                    currentCoroutineContext().cancel(CancellationException("Caller left"))
                    throw factory("Provider failed during cancellation")
                }
            }
            val resolver = MediaStorePlaybackSourceResolver(source, StandardTestDispatcher(testScheduler))
            val request = async { resolver.resolveTrack(TrackId("external_primary:1")) }
            try {
                request.await()
                fail("Caller cancellation must not become a typed provider failure")
            } catch (_: CancellationException) {
                assertTrue(request.isCancelled)
            }
        }
    }
}
