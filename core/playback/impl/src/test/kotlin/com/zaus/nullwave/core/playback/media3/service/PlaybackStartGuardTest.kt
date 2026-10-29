package com.zaus.nullwave.core.playback.media3.service

import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.result.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackStartGuardTest {
    @Test
    fun `foreground is acquired before work and released only after accepted work finishes`() = runTest {
        val events = mutableListOf<String>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val guard = PlaybackStartGuard(
            acquire = {
                events += "foreground"
                AppResult.Success(AutoCloseable { events += "release" })
            },
            onDenied = { error("Unexpected denial") },
        )
        val work = async {
            guard.run(true) {
                events += "work"
                entered.complete(Unit)
                release.await()
                AppResult.Success(Unit)
            }
        }
        entered.await()
        assertEquals(listOf("foreground", "work"), events)
        release.complete(Unit)
        assertEquals(AppResult.Success(Unit), work.await())
        assertEquals(listOf("foreground", "work", "release"), events)
    }

    @Test
    fun `denied start revokes older work without entering new preparation and preserves cause`() = runTest {
        val cause = IllegalStateException("Background start denied")
        val rejected = AppResult.Failure(PlaybackCommandError.StartNotAllowed, cause)
        var revocations = 0
        val guard = PlaybackStartGuard({ rejected }, { revocations++ })
        assertSame(rejected, guard.run(true) { error("Denied work must not run") })
        assertEquals(1, revocations)
        assertSame(cause, rejected.cause)
    }

    @Test
    fun `cancellation releases the preparation lease`() = runTest {
        val entered = CompletableDeferred<Unit>()
        var releases = 0
        val guard = PlaybackStartGuard(
            { AppResult.Success(AutoCloseable { releases++ }) },
            { error("Unexpected denial") },
        )
        val work = async {
            guard.run(true) {
                entered.complete(Unit)
                CompletableDeferred<Unit>().await()
                AppResult.Success(Unit)
            }
        }
        entered.await()
        work.cancelAndJoin()
        assertEquals(1, releases)
    }

    @Test
    fun `ordinary controls do not acquire foreground and cleanup keeps the original failure`() = runTest {
        val original = IllegalArgumentException("Engine failed")
        val cleanup = IllegalStateException("Notification failed")
        val guard = PlaybackStartGuard(
            { AppResult.Success(AutoCloseable { throw cleanup }) },
            { error("Unexpected denial") },
        )
        assertEquals(AppResult.Success(Unit), guard.run(false) { AppResult.Success(Unit) })
        val actual = try {
            guard.run(true) { throw original }
            error("Expected failure")
        } catch (failure: IllegalArgumentException) { failure }
        assertSame(original, actual)
        assertEquals(listOf(cleanup), actual.suppressed.toList())
    }
}
