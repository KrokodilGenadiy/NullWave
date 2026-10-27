package com.zaus.nullwave.core.playback.api

import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueTest {
    @Test fun emptyQueueHasNoCurrentItemOrTraversalOrder() {
        val queue = PlaybackQueue.create()
        assertTrue(queue.entries.isEmpty())
        assertTrue(queue.playOrder.isEmpty())
        assertNull(queue.current)
    }

    @Test fun repeatedTrackUsesSeparateQueueEntriesAndKeepsTheirOrder() {
        val first = queueEntry("entry-1", "volume:7")
        val second = queueEntry("entry-2", "volume:7")
        val queue = PlaybackQueue.create(entries = listOf(first, second), current = firstToken)
        assertEquals(first.track.id, second.track.id)
        assertEquals(listOf(first, second), queue.entries)
        assertEquals(listOf(first.entryId, second.entryId), queue.playOrder)
    }

    @Test fun duplicateEntryIdentityIsRejectedEvenForDifferentTracks() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue.create(
                entries = listOf(queueEntry("entry-1", "volume:1"), queueEntry("entry-1", "volume:2")),
                current = firstToken,
            )
        }
    }

    @Test fun nonemptyQueueRequiresCurrentEntry() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue.create(entries = listOf(queueEntry()))
        }
    }

    @Test fun emptyQueueCannotCarryCurrentEntry() {
        assertThrows(IllegalArgumentException::class.java) { PlaybackQueue.create(current = firstToken) }
    }

    @Test fun currentEntryMustBelongToTheQueue() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue.create(
                entries = listOf(queueEntry()),
                current = PlaybackItemToken(session, QueueEntryId("not-in-queue"), 1),
            )
        }
    }

    @Test fun traversalOrderMustContainEveryEntryExactlyOnce() {
        val entries = listOf(queueEntry(), queueEntry("entry-2", "volume:2"))
        val second = entries[1].entryId
        for (order in listOf(
            emptyList(),
            listOf(firstEntryId),
            listOf(firstEntryId, firstEntryId),
            listOf(firstEntryId, QueueEntryId("foreign")),
            listOf(firstEntryId, second, QueueEntryId("extra")),
        )) {
            assertThrows("Invalid traversal: $order", IllegalArgumentException::class.java) {
                PlaybackQueue.create(entries = entries, playOrder = order, current = firstToken, shuffleEnabled = true)
            }
        }
    }

    @Test fun reorderedTraversalRequiresShuffleAndDoesNotReorderBaseEntries() {
        val entries = listOf(queueEntry(), queueEntry("entry-2", "volume:2"))
        val reversed = entries.map { it.entryId }.reversed()
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue.create(entries = entries, playOrder = reversed, current = firstToken)
        }
        val shuffled = PlaybackQueue.create(
            entries = entries, playOrder = reversed, current = firstToken, shuffleEnabled = true,
        )
        assertEquals(entries, shuffled.entries)
        assertEquals(reversed, shuffled.playOrder)
        assertEquals(firstToken, shuffled.current)
    }

    @Test fun mutationOfCallerOwnedListsCannotChangePublishedQueue() {
        val entries = mutableListOf(queueEntry(), queueEntry("entry-2", "volume:2"))
        val order = entries.map { it.entryId }.toMutableList()
        val queue = PlaybackQueue.create(entries = entries, playOrder = order, current = firstToken)
        entries.clear()
        order.reverse()
        assertEquals(listOf(firstEntryId, QueueEntryId("entry-2")), queue.entries.map { it.entryId })
        assertEquals(listOf(firstEntryId, QueueEntryId("entry-2")), queue.playOrder)
    }

    @Test fun defaultTraversalIsProtectedAndIndependentFromCallerEntries() {
        val entries = mutableListOf(queueEntry(), queueEntry("entry-2", "volume:2"))
        val queue = PlaybackQueue.create(entries = entries, current = firstToken)
        entries.clear()
        assertEquals(listOf(firstEntryId, QueueEntryId("entry-2")), queue.playOrder)
        assertEquals(queue.playOrder, queue.entries.map { it.entryId })
        assertReadOnlyList(queue.entries, queueEntry("entry-3", "volume:3"))
        assertReadOnlyList(queue.playOrder, QueueEntryId("entry-3"))
    }

    @Test fun returnedListsAndTheirViewsCannotMutateQueue() {
        val queue = currentQueue()
        assertReadOnlyList(queue.entries, queueEntry("entry-2", "volume:2"))
        assertReadOnlyList(queue.playOrder, QueueEntryId("entry-2"))
    }

    @Test fun negativeRevisionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { PlaybackQueue.create(revision = -1) }
    }

    @Test fun emptyQueueMayKeepSessionRepeatAndShufflePreferences() {
        val queue = PlaybackQueue.create(repeatMode = RepeatMode.All, shuffleEnabled = true)
        assertEquals(RepeatMode.All, queue.repeatMode)
        assertTrue(queue.shuffleEnabled)
        assertTrue(queue.playOrder.isEmpty())
    }
}
