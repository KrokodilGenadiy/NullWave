package com.zaus.nullwave.core.playback.media3.engine

import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueOrder
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.playback.media3.state.PlaybackServiceRegistry
import com.zaus.nullwave.core.playback.preparation.PreparedQueue
import com.zaus.nullwave.core.playback.preparation.QueuePreparer
import com.zaus.nullwave.core.playback.runtime.PlaybackIdentities
import com.zaus.nullwave.core.playback.runtime.PlaybackQueueOwner
import com.zaus.nullwave.core.playback.runtime.QueueCommit
import com.zaus.nullwave.core.result.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class Media3QueueReplacementOrderInstrumentedTest {
    @Test fun enablingShuffleWhileAnOrderedAlbumIsPreparingStartsTraversalAtTheSelectedTrack() =
        verifyShuffleDuringPreparation(PlaybackQueueOrder.InOrder, enableShuffle = true)

    @Test fun disablingShuffleWhileAShuffledAlbumIsPreparingRestoresBaseOrder() =
        verifyShuffleDuringPreparation(PlaybackQueueOrder.Shuffled, enableShuffle = false)

    private fun verifyShuffleDuringPreparation(order: PlaybackQueueOrder, enableShuffle: Boolean) =
        withSilentExoPlayer { player, uri ->
            val registry = PlaybackServiceRegistry()
            val identities = PlaybackIdentities()
            val target = Media3QueueCommitTarget(player, identities, registry, PlaybackClock(SystemClock::elapsedRealtime)) {
                error("Unexpected foreground request")
            }
            val ids = listOf("C", "A", "B").map(::TrackId)
            val resolving = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val resolver = object : PlaybackSourceResolver {
                override suspend fun resolveTrack(id: TrackId): AppResult.Success<ResolvedPlaybackTrack> {
                    if (id == ids.first()) {
                        resolving.complete(Unit)
                        release.await()
                    }
                    return AppResult.Success(ResolvedPlaybackTrack(MediaTrack(id, id.value, null, null, 60_000), uri))
                }
            }
            coroutineScope {
                val owner = PlaybackQueueOwner(this, Dispatchers.Main.immediate, QueuePreparer(resolver, Dispatchers.Default), target, identities)
                try {
                    val old = PlaybackQueueRequest.create(listOf(TrackId("old")), 0)
                    assertEquals(AppResult.Success(Unit), owner.execute(PlaybackCommand.ReplaceQueue(old)))
                    val request = PlaybackQueueRequest.create(ids, startIndex = 1, order = order)
                    val replacement = async { owner.execute(PlaybackCommand.ReplaceQueue(request)) }
                    resolving.await()
                    for (enabled in listOf(!enableShuffle, enableShuffle)) {
                        assertEquals(AppResult.Success(Unit), owner.execute(PlaybackCommand.SetShuffle(enabled)))
                        assertEquals(enabled, player.shuffleModeEnabled)
                    }
                    release.complete(Unit)
                    assertEquals(AppResult.Success(Unit), replacement.await())
                    val queue = requireNotNull(registry.snapshot.value).queue
                    assertEquals(enableShuffle, player.shuffleModeEnabled)
                    assertEquals(enableShuffle, queue.shuffleEnabled)
                    assertEquals(ids, queue.entries.map { it.track.id })
                    assertEquals(queue.entries[1].entryId, queue.current?.entryId)
                    assertEquals(1, player.currentMediaItemIndex)
                    assertTrue(player.playWhenReady)
                    if (enableShuffle) assertEquals(queue.current?.entryId, queue.playOrder.first())
                    else assertEquals(queue.entries.map { it.entryId }, queue.playOrder)
                } finally {
                    release.complete(Unit)
                    owner.close()
                    target.close()
                }
            }
        }

    @Test fun shuffledReplacementPlaysEveryEntryAndOrderedReplacementResetsShuffle() = withSilentExoPlayer { player, uri ->
        val registry = PlaybackServiceRegistry()
        val identities = PlaybackIdentities()
        val items = listOf("Z", "A", "M", "C").map {
            ResolvedPlaybackTrack(MediaTrack(TrackId(it), it, null, null, 60_000), uri)
        }
        val target = Media3QueueCommitTarget(player, identities, registry, PlaybackClock(SystemClock::elapsedRealtime)) {
            error("Foreground lease is owned by the caller of install")
        }
        fun install(order: PlaybackQueueOrder, startIndex: Int): QueueCommit {
            val request = PlaybackQueueRequest.create(items.map { it.track.id }, startIndex, order = order)
            val commit = QueueCommit.create(PreparedQueue.create(request, items), identities)
            assertEquals(AppResult.Success(Unit), target.install(commit))
            return commit
        }
        try {
            val shuffled = install(PlaybackQueueOrder.Shuffled, 2)
            val queue = requireNotNull(registry.snapshot.value).queue
            assertTrue(player.playWhenReady)
            assertTrue(queue.shuffleEnabled)
            assertEquals(shuffled.entries, queue.entries)
            assertEquals(shuffled.current, queue.current)
            assertEquals(shuffled.current.entryId, queue.playOrder.first())
            val visited = mutableListOf<String>()
            do {
                visited += requireNotNull(player.currentMediaItem).mediaId
                if (!player.hasNextMediaItem()) break
                player.seekToNextMediaItem()
            } while (visited.size <= items.size)
            assertEquals(shuffled.entries.map { it.entryId.value }.toSet(), visited.toSet())
            assertEquals(items.size, visited.size)
            assertEquals(queue.playOrder.map { it.value }, visited)

            val ordered = install(PlaybackQueueOrder.InOrder, 1)
            val next = requireNotNull(registry.snapshot.value).queue
            assertFalse(next.shuffleEnabled)
            assertTrue(player.playWhenReady)
            assertEquals(ordered.current, next.current)
            assertEquals(1, player.currentMediaItemIndex)
            assertEquals(2, player.nextMediaItemIndex)
            assertEquals(ordered.entries.map { it.entryId }, next.playOrder)
        } finally {
            target.close()
        }
    }
}
