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
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class Media3QueueMoveInstrumentedTest {
    @Test fun movingInBaseOrderWhilePlayingKeepsTheCurrentEntryAndProgress() = verifyMove(shuffle = false, playing = true)
    @Test fun movingInBaseOrderWhilePausedKeepsTheCurrentEntryAndPosition() = verifyMove(shuffle = false, playing = false)
    @Test fun movingInShuffledOrderWhilePlayingKeepsTheBaseQueueAndProgress() = verifyMove(shuffle = true, playing = true)
    @Test fun movingInShuffledOrderWhilePausedKeepsTheBaseQueueAndPosition() = verifyMove(shuffle = true, playing = false)

    private fun verifyMove(shuffle: Boolean, playing: Boolean) = withSilentExoPlayer { player, uri ->
        val registry = PlaybackServiceRegistry()
        val ids = PlaybackIdentities()
        val target = Media3QueueCommitTarget(player, ids, registry, PlaybackClock(SystemClock::elapsedRealtime)) {
            error("Moving an upcoming entry must not acquire foreground")
        }
        try {
            val items = listOf("A", "A", "B", "A", "C").map { name ->
                ResolvedPlaybackTrack(MediaTrack(TrackId(name), name, null, null, 60_000), uri)
            }
            val request = PlaybackQueueRequest.create(items.map { it.track.id }, startIndex = 1,
                startPositionMs = 12_000, playRequested = playing)
            val commit = QueueCommit.create(PreparedQueue.create(request, items), ids)
            val (a1, active, b, a3, c) = commit.entries
            assertEquals(AppResult.Success(Unit), target.install(commit))
            if (shuffle) {
                player.setShuffleOrder(DefaultShuffleOrder(intArrayOf(4, 1, 0, 2, 3), 0L))
                assertEquals(AppResult.Success(Unit), target.execute(PlaybackCommand.SetShuffle(true)))
            }
            val before = registry.snapshot.filterNotNull().first {
                it.transport.phase == PlaybackPhase.Ready && it.transport.isPlaying == playing && it.queue.shuffleEnabled == shuffle
            }
            assertTrue(PlaybackCapability.MoveQueueEntry in before.capabilities)
            if (playing) while (player.currentPosition <= request.startPositionMs) delay(10)
            val anchor = requireNotNull(target.sampleProgress())
            assertEquals(commit.current, anchor.target)
            assertEquals(playing, anchor.advancing)
            if (!playing) assertEquals(request.startPositionMs, anchor.positionMs)

            // In shuffle A1 is upcoming despite its base slot preceding the active copy of A.
            val move = if (shuffle) PlaybackCommand.MoveQueueEntry(before.sessionId, a1.entryId, null)
            else PlaybackCommand.MoveQueueEntry(before.sessionId, a3.entryId, b.entryId)
            val expectedBase = if (shuffle) commit.entries else listOf(a1, active, a3, b, c)
            val expectedOrder = if (shuffle) listOf(c, active, b, a3, a1) else expectedBase
            assertEquals(AppResult.Success(Unit), target.execute(move))
            assertPositionAfterQueueEdit(player, target, anchor)

            val after = requireNotNull(registry.snapshot.value)
            assertEquals(expectedBase, after.queue.entries)
            assertEquals(expectedBase.map { it.entryId.value }, List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId })
            assertEquals(expectedOrder.map { it.entryId }, after.queue.playOrder)
            assertEquals(expectedOrder[2].entryId.value, player.getMediaItemAt(player.nextMediaItemIndex).mediaId)
            assertEquals(shuffle, after.queue.shuffleEnabled)
            assertEquals(before.queue.revision + 1, after.queue.revision)
            assertEquals(before.transport, after.transport)
            assertEquals(commit.current, after.queue.current)
            assertEquals(active.entryId.value, player.currentMediaItem?.mediaId)
            assertTrue(anchor.matches(after))

            assertEquals(AppResult.Success(Unit), target.execute(move))
            assertSame(after.queue, registry.snapshot.value!!.queue)
            if (shuffle) {
                assertEquals(AppResult.Success(Unit), target.execute(PlaybackCommand.SetShuffle(false)))
                assertEquals(commit.entries.map { it.entryId }, registry.snapshot.value!!.queue.playOrder)
            }
        } finally {
            target.close()
        }
    }
}
