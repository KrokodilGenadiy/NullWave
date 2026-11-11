package com.zaus.nullwave.core.playback.media3.engine

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.playback.media3.state.PlaybackServiceRegistry
import com.zaus.nullwave.core.playback.media3.testing.createSilentWav
import com.zaus.nullwave.core.playback.preparation.PreparedQueue
import com.zaus.nullwave.core.playback.runtime.PlaybackIdentities
import com.zaus.nullwave.core.playback.runtime.QueueCommit
import com.zaus.nullwave.core.result.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class Media3QueueRemovalInstrumentedTest {
    @Test fun removingEarlierBaseDuplicateWhilePlayingKeepsCurrentEntryAndAdvancingPosition() =
        verifyShuffledRemoval(playing = true)

    @Test fun removingEarlierBaseDuplicateWhilePausedKeepsCurrentEntryAndExactPosition() =
        verifyShuffledRemoval(playing = false)

    private fun verifyShuffledRemoval(playing: Boolean): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = createSilentWav(context.cacheDir, durationSeconds = 60)
        try {
            withContext(Dispatchers.Main) {
                withTimeout(15_000) { checkRemoval(context, Uri.fromFile(file).toString(), playing) }
            }
        } finally {
            file.delete()
        }
    }

    private suspend fun checkRemoval(context: Context, uri: String, playing: Boolean) {
        val player = ExoPlayer.Builder(context).build()
        val registry = PlaybackServiceRegistry()
        val ids = PlaybackIdentities()
        val target = Media3QueueCommitTarget(player, ids, registry, PlaybackClock(SystemClock::elapsedRealtime)) {
            error("Removing an upcoming entry must not acquire foreground")
        }
        try {
            val track = MediaTrack(TrackId("a"), "A", null, null, 60_000)
            val a = ResolvedPlaybackTrack(track, uri)
            val b = a.copy(track = track.copy(id = TrackId("b"), title = "B"))
            val request = PlaybackQueueRequest.create(
                listOf(track.id, track.id, b.track.id), startIndex = 1,
                startPositionMs = 12_000, playRequested = playing,
            )
            val commit = QueueCommit.create(PreparedQueue.create(request, listOf(a, a, b)), ids)
            assertEquals(AppResult.Success(Unit), target.install(commit))
            player.setShuffleOrder(DefaultShuffleOrder(intArrayOf(2, 1, 0), 0L))
            assertEquals(AppResult.Success(Unit), target.execute(PlaybackCommand.SetShuffle(true)))
            assertEquals(AppResult.Success(Unit), target.execute(PlaybackCommand.SetRepeat(RepeatMode.All)))
            val (upcoming, active, past) = commit.entries
            val before = registry.snapshot.filterNotNull().first {
                it.transport.phase == PlaybackPhase.Ready && it.transport.isPlaying == playing &&
                    it.queue.playOrder == listOf(past.entryId, active.entryId, upcoming.entryId)
            }
            if (playing) while (player.currentPosition <= request.startPositionMs) delay(10)
            val anchor = requireNotNull(target.sampleProgress())
            assertEquals(commit.current, anchor.target)
            assertEquals(playing, before.transport.playRequested)
            assertEquals(1, player.currentMediaItemIndex)
            assertTrue(PlaybackCapability.RemoveQueueEntry in before.capabilities)

            val remove = PlaybackCommand.RemoveQueueEntry(before.sessionId, upcoming.entryId)
            assertEquals(AppResult.Success(Unit), target.execute(remove))
            // Wait for the playback thread to process the edit as well as the immediate playlist update.
            val applied = CompletableDeferred<Unit>()
            player.createMessage { _, _ -> applied.complete(Unit) }.send()
            applied.await()
            val positionAfterEdit = player.currentPosition
            assertTrue(
                "Removal rewound playback from ${anchor.positionMs} to $positionAfterEdit",
                positionAfterEdit >= anchor.positionMs,
            )
            if (playing) while (player.currentPosition <= positionAfterEdit) delay(10)

            val after = requireNotNull(registry.snapshot.value)
            val progress = requireNotNull(target.sampleProgress())
            assertEquals(listOf(active, past), after.queue.entries)
            assertEquals(listOf(past.entryId, active.entryId), after.queue.playOrder)
            assertEquals(0, player.currentMediaItemIndex)
            assertEquals(active.entryId.value, player.currentMediaItem?.mediaId)
            assertEquals(commit.current, after.queue.current)
            assertTrue(after.queue.revision > before.queue.revision)
            assertFalse(PlaybackCapability.RemoveQueueEntry in after.capabilities)
            assertTrue(anchor.matches(after))
            assertTrue(progress.matches(after))
            assertEquals(before.transport, after.transport)
            assertEquals(playing, progress.advancing)
            if (playing) {
                val advance = progress.positionMs - anchor.positionMs
                assertTrue(
                    "Position must advance continuously: $anchor -> $progress",
                    advance in 1..(progress.sampledAtMs - anchor.sampledAtMs + 100),
                )
            } else {
                assertEquals(request.startPositionMs, anchor.positionMs)
                assertEquals(anchor.positionMs, progress.positionMs)
            }
            assertEquals(AppResult.Failure(PlaybackCommandError.StaleTarget), target.execute(remove))
        } finally {
            target.close()
            player.release()
        }
    }
}
