package com.zaus.nullwave.core.playback.media3.engine

import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.zaus.nullwave.core.playback.preparation.PreparedQueue
import com.zaus.nullwave.core.playback.runtime.PlaybackIdentities
import com.zaus.nullwave.core.playback.runtime.QueueCommit
import com.zaus.nullwave.core.result.AppResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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

    private fun verifyShuffledRemoval(playing: Boolean) = withSilentExoPlayer { player, uri ->
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
            assertEquals(playing, anchor.advancing)
            if (!playing) assertEquals(request.startPositionMs, anchor.positionMs)
            assertEquals(1, player.currentMediaItemIndex)
            assertTrue(PlaybackCapability.RemoveQueueEntry in before.capabilities)

            val remove = PlaybackCommand.RemoveQueueEntry(before.sessionId, upcoming.entryId)
            assertEquals(AppResult.Success(Unit), target.execute(remove))
            assertPositionAfterQueueEdit(player, target, anchor)

            val after = requireNotNull(registry.snapshot.value)
            assertEquals(listOf(active, past), after.queue.entries)
            assertEquals(listOf(past.entryId, active.entryId), after.queue.playOrder)
            assertEquals(0, player.currentMediaItemIndex)
            assertEquals(active.entryId.value, player.currentMediaItem?.mediaId)
            assertEquals(commit.current, after.queue.current)
            assertTrue(after.queue.revision > before.queue.revision)
            assertFalse(PlaybackCapability.RemoveQueueEntry in after.capabilities)
            assertTrue(anchor.matches(after))
            assertEquals(before.transport, after.transport)
            assertEquals(AppResult.Failure(PlaybackCommandError.StaleTarget), target.execute(remove))
        } finally {
            target.close()
        }
    }
}
