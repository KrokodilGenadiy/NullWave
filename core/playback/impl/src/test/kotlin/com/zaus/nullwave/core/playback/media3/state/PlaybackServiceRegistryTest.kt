package com.zaus.nullwave.core.playback.media3.state

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackServiceRegistryTest {
    @Test
    fun `only the attached service can publish confirmed state and anchors`() {
        val registry = PlaybackServiceRegistry()
        val current = snapshot("current")
        val anchor = progress(current, 10)
        registry.attach(current.sessionId) { anchor }
        assertNull(registry.snapshot.value)

        registry.publish(snapshot("unattached"), null)
        assertNull(registry.snapshot.value)
        registry.publish(current, anchor)

        assertSame(current, registry.snapshot.value)
        assertSame(anchor, registry.anchor.value)
        assertSame(anchor, registry.sampleProgress())
    }

    @Test
    fun `a new service replaces the old sampler and ignores late publication or detach`() {
        val registry = PlaybackServiceRegistry()
        val old = snapshot("old")
        val next = snapshot("next")
        var oldSamples = 0
        var nextSamples = 0
        registry.attach(old.sessionId) { oldSamples++; progress(old, 1) }
        registry.publish(old, progress(old, 1))
        registry.startNotAllowed(old.sessionId)

        registry.attach(next.sessionId) { nextSamples++; progress(next, 2) }
        assertNull(registry.snapshot.value)
        assertNull(registry.anchor.value)
        assertNull(registry.failure.value)
        registry.publish(old, progress(old, 3))
        registry.detach(old.sessionId)
        assertNull(registry.snapshot.value)

        val anchor = progress(next, 4)
        registry.publish(next, anchor)
        assertSame(next, registry.snapshot.value)
        assertSame(anchor, registry.anchor.value)
        assertEquals(next.queue.current, registry.sampleProgress()?.target)
        assertEquals(0, oldSamples)
        assertEquals(1, nextSamples)
    }

    @Test
    fun `detach clears the actual engine sampler as well as published state`() {
        val registry = PlaybackServiceRegistry()
        val state = snapshot("session")
        var samples = 0
        registry.attach(state.sessionId) { samples++; progress(state, 2) }
        registry.publish(state, progress(state, 1))
        registry.startNotAllowed(state.sessionId)

        registry.detach(state.sessionId)
        registry.detach(state.sessionId)

        assertNull(registry.snapshot.value)
        assertNull(registry.anchor.value)
        assertNull(registry.failure.value)
        assertNull(registry.sampleProgress())
        assertEquals(0, samples)
    }

    @Test
    fun `foreground refusal belongs to its service incarnation and can be cleared without losing state`() {
        val registry = PlaybackServiceRegistry()
        val state = snapshot("session")
        registry.attach(state.sessionId) { null }
        registry.publish(state, null)
        registry.startNotAllowed(PlaybackSessionId("stale"))
        assertNull(registry.failure.value)

        registry.startNotAllowed(state.sessionId)
        assertEquals(PlaybackConnectionError.StartNotAllowed, registry.failure.value)
        registry.clearConnectionError()
        assertNull(registry.failure.value)
        assertSame(state, registry.snapshot.value)
    }

    @Test
    fun `demand sampling reads fresh position without rebuilding the control snapshot`() {
        val registry = PlaybackServiceRegistry()
        val state = snapshot("session")
        val initial = progress(state, 1)
        var latest = initial
        registry.attach(state.sessionId) { latest }
        registry.publish(state, initial)

        latest = progress(state, 50)

        assertSame(latest, registry.sampleProgress())
        assertSame(state, registry.snapshot.value)
        assertSame(initial, registry.anchor.value)
    }

    @Test
    fun `immediate observer publication cannot be overwritten by the outer service event`() = runTest {
        val registry = PlaybackServiceRegistry()
        val first = snapshot("session")
        val second = PlaybackSnapshot.create(
            first.sessionId,
            revision = 1,
            queue = first.queue,
            transport = first.transport,
        )
        val firstAnchor = progress(first, 1)
        val secondAnchor = progress(second, 50)
        registry.attach(first.sessionId) { secondAnchor }
        var insidePublish = false
        var publishedBeforeReturn = false
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.snapshot.collect { value ->
                if (value === first) {
                    assertTrue("Observer must run inside the outer setter, not after its return", insidePublish)
                    registry.publish(second, secondAnchor)
                    publishedBeforeReturn = true
                }
            }
        }
        try {
            // The publisher uses the ordinary test dispatcher, outside an active Unconfined trampoline.
            insidePublish = true
            registry.publish(first, firstAnchor)
            insidePublish = false

            assertTrue("Nested publication must finish before the outer publish returns", publishedBeforeReturn)
            assertSame(second, registry.snapshot.value)
            assertSame(secondAnchor, registry.anchor.value)
        } finally {
            observer.cancel()
        }
    }

    @Test
    fun `attach cannot clear a publication created by an immediate observer`() = runTest {
        val registry = PlaybackServiceRegistry()
        val old = snapshot("old")
        val next = snapshot("next")
        val anchor = progress(next, 25)
        registry.attach(old.sessionId) { progress(old, 1) }
        registry.publish(old, progress(old, 1))
        var insideAttach = false
        var publishedBeforeReturn = false
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.snapshot.collect { value ->
                if (value == null && !publishedBeforeReturn) {
                    assertTrue("Observer must run before attach finishes clearing prior state", insideAttach)
                    registry.publish(next, anchor)
                    registry.startNotAllowed(next.sessionId)
                    publishedBeforeReturn = true
                }
            }
        }
        try {
            insideAttach = true
            registry.attach(next.sessionId) { anchor }
            insideAttach = false

            assertTrue(publishedBeforeReturn)
            assertSame(next, registry.snapshot.value)
            assertSame(anchor, registry.anchor.value)
            assertSame(anchor, registry.sampleProgress())
            assertEquals(PlaybackConnectionError.StartNotAllowed, registry.failure.value)
        } finally {
            observer.cancel()
        }
    }

    @Test
    fun `detach cannot clear a replacement service attached by an immediate observer`() = runTest {
        val registry = PlaybackServiceRegistry()
        val old = snapshot("old")
        val next = snapshot("next")
        val anchor = progress(next, 25)
        registry.attach(old.sessionId) { progress(old, 1) }
        registry.publish(old, progress(old, 1))
        var insideDetach = false
        var attachedBeforeReturn = false
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.snapshot.collect { value ->
                if (value == null && !attachedBeforeReturn) {
                    assertTrue("Observer must attach the next service before detach returns", insideDetach)
                    registry.attach(next.sessionId) { anchor }
                    registry.publish(next, anchor)
                    registry.startNotAllowed(next.sessionId)
                    attachedBeforeReturn = true
                }
            }
        }
        try {
            insideDetach = true
            registry.detach(old.sessionId)
            insideDetach = false

            assertTrue(attachedBeforeReturn)
            assertSame(next, registry.snapshot.value)
            assertSame(anchor, registry.anchor.value)
            assertSame(anchor, registry.sampleProgress())
            assertEquals(PlaybackConnectionError.StartNotAllowed, registry.failure.value)
        } finally {
            observer.cancel()
        }
    }

    private fun snapshot(id: String): PlaybackSnapshot {
        val session = PlaybackSessionId(id)
        val entry = QueueEntry(
            QueueEntryId("entry-$id"),
            MediaTrack(TrackId("track-$id"), "Track $id", null, null, 100),
        )
        return PlaybackSnapshot.create(
            session,
            queue = PlaybackQueue.create(listOf(entry), current = PlaybackItemToken(session, entry.entryId, 0)),
            transport = PlaybackTransport(PlaybackPhase.Ready, playRequested = true),
        )
    }

    private fun progress(snapshot: PlaybackSnapshot, position: Long) = PlaybackProgress(
        target = snapshot.queue.current!!,
        positionRevision = snapshot.positionRevision,
        positionMs = position,
        durationMs = 100,
        bufferedPositionMs = null,
        sampledAtMs = position + 1,
        advancing = true,
    )
}
