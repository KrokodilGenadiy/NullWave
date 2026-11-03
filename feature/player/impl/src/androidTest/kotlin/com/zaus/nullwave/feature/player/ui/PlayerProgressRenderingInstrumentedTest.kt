package com.zaus.nullwave.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.feature.player.presentation.PlayerState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests the Compose frame loop itself; the independently controlled playback clock is authoritative. */
class PlayerProgressRenderingInstrumentedTest {
    private val motionScale = TestMotionScale()

    @get:Rule
    val compose = createComposeRule(effectContext = motionScale)

    @Test
    fun frameLoopUsesPlaybackClockStopsWithLifecycleAndSleepsAtTheExtrapolationLimit() {
        compose.mainClock.autoAdvance = false
        val clock = CountingPlaybackClock(sampledAtMs)
        val fraction = AtomicReference<(() -> Float?)?>(null)
        lateinit var owner: ProgressLifecycleOwner
        compose.runOnUiThread {
            owner = ProgressLifecycleOwner().apply { registry.currentState = Lifecycle.State.STARTED }
        }
        try {
            compose.setContent {
                NullWaveTheme(effects = NullWaveEffects(reduceMotion = false, glowEnabled = false)) {
                    val value = rememberPlayerProgress(playingState(), clock, owner)
                    SideEffect { fraction.set(value) }
                    // Mirror the DS consumer: position is read only while drawing.
                    Box(Modifier.size(20.dp).drawBehind {
                        drawRect(Color.White, size = Size(size.width * (value() ?: 0f), size.height))
                    })
                }
            }
            frame()
            assertFraction(fraction, 0.2f)
            val initialReads = clock.reads.get()

            // Advancing Compose frame time cannot advance audio time while PlaybackClock is unchanged.
            compose.mainClock.advanceTimeBy(200)
            compose.waitForIdle()
            assertFraction(fraction, 0.2f)
            assertTrue("The interpolation runner must have requested frames", clock.reads.get() > initialReads)

            clock.time.set(sampledAtMs + 250)
            frame()
            assertFraction(fraction, 0.225f)

            compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
            compose.waitForIdle()
            val stoppedReads = clock.reads.get()
            clock.time.set(sampledAtMs + 500)
            compose.mainClock.advanceTimeBy(96)
            compose.waitForIdle()
            assertEquals("STOP must cancel frame-clock reads", stoppedReads, clock.reads.get())
            assertFraction(fraction, 0.225f)

            compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
            frame()
            assertTrue("START must refresh the playback clock", clock.reads.get() > stoppedReads)
            assertFraction(fraction, 0.25f)

            clock.time.set(sampledAtMs + 1_000)
            frame()
            assertFraction(fraction, 0.3f)
            val limitedReads = clock.reads.get()
            clock.time.set(sampledAtMs + 3_000)
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            assertEquals("No frame polling is needed after the anchor reaches its extrapolation limit", limitedReads, clock.reads.get())
            assertFraction(fraction, 0.3f)
        } finally {
            compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.DESTROYED }
        }
    }

    @Test
    fun liveMotionRestrictionsStopPollingAndResumeFromThePlaybackClockWithTheSameProvider() {
        val probe = ProgressProbe()
        try {
            probe.show()
            val provider = probe.fraction.get()
            probe.clock.time.set(sampledAtMs + 250)
            frame()
            assertFraction(probe.fraction, 0.225f)

            compose.runOnUiThread { probe.reduceMotion = true }
            settleChanges()
            assertFraction(probe.fraction, 0.2f)
            assertPollingStopped(probe, sampledAtMs + 400)

            // Removing the app restriction cannot override a disabled system scale.
            compose.runOnUiThread {
                motionScale.factor = 0f
                probe.reduceMotion = false
            }
            settleChanges()
            assertFraction(probe.fraction, 0.2f)
            assertPollingStopped(probe, sampledAtMs + 700)

            compose.runOnUiThread { motionScale.factor = 1f }
            settleChanges()
            assertFraction(probe.fraction, 0.27f)
            probe.clock.time.set(sampledAtMs + 750)
            frame()
            assertFraction(probe.fraction, 0.275f)

            // The scale must also stop an already running frame loop, without an app policy change.
            compose.runOnUiThread { motionScale.factor = 0f }
            settleChanges()
            assertFraction(probe.fraction, 0.2f)
            assertPollingStopped(probe, sampledAtMs + 800)

            // Re-enabling the system cannot override the app's reduced-motion policy either.
            compose.runOnUiThread {
                probe.reduceMotion = true
                motionScale.factor = 1f
            }
            settleChanges()
            assertFraction(probe.fraction, 0.2f)
            assertPollingStopped(probe, sampledAtMs + 850)

            compose.runOnUiThread { probe.reduceMotion = false }
            settleChanges()
            assertFraction(probe.fraction, 0.285f)
            assertSame("Motion policy changes must not recreate the draw provider", provider, probe.fraction.get())
        } finally {
            probe.close()
        }
    }

    @Test
    fun activationAndSeekAnchorsReplaceARunningLoopWithoutKeepingOldPositionOrFrames() {
        val probe = ProgressProbe()
        try {
            probe.show()
            val provider = probe.fraction.get()
            probe.clock.time.set(sampledAtMs + 250)
            frame()
            assertFraction(probe.fraction, 0.225f)

            compose.runOnUiThread {
                probe.state = playingState(
                    activation = 1, positionRevision = 1, positionMs = 6_000,
                    durationMs = 20_000, sampleTimeMs = sampledAtMs + 250,
                )
            }
            settleChanges()
            assertFraction(probe.fraction, 0.3f)
            probe.clock.time.set(sampledAtMs + 450)
            frame()
            assertFraction(probe.fraction, 0.31f)

            // A seek keeps the same activation but starts a different position generation.
            compose.runOnUiThread {
                probe.state = playingState(
                    activation = 1, positionRevision = 2, positionMs = 1_000,
                    durationMs = 20_000, sampleTimeMs = sampledAtMs + 450,
                )
            }
            settleChanges()
            assertFraction(probe.fraction, 0.05f)
            probe.clock.time.set(sampledAtMs + 650)
            frame()
            assertFraction(probe.fraction, 0.06f)

            // Control may change before its progress anchor: the old sample must stop polling too.
            compose.runOnUiThread {
                probe.state = probe.state.copy(
                    connection = playingState(activation = 2, positionRevision = 3).connection,
                )
            }
            settleChanges()
            compose.runOnUiThread { assertNull(checkNotNull(probe.fraction.get())()) }
            assertPollingStopped(probe, sampledAtMs + 850)

            compose.runOnUiThread {
                probe.state = playingState(
                    activation = 2, positionRevision = 3, positionMs = 3_000,
                    durationMs = 20_000, sampleTimeMs = sampledAtMs + 850,
                )
            }
            settleChanges()
            assertFraction(probe.fraction, 0.15f)
            probe.clock.time.set(sampledAtMs + 1_050)
            frame()
            assertFraction(probe.fraction, 0.16f)
            assertSame("New anchors must not recreate the draw provider", provider, probe.fraction.get())
        } finally {
            probe.close()
        }
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun settleChanges() {
        // One frame applies the new composition, the next runs its replacement effect.
        repeat(2) { frame() }
    }

    private fun assertPollingStopped(probe: ProgressProbe, newTimeMs: Long) {
        val reads = probe.clock.reads.get()
        probe.clock.time.set(newTimeMs)
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        assertEquals("A static or mismatched anchor must not poll frame time", reads, probe.clock.reads.get())
    }

    private fun assertFraction(provider: AtomicReference<(() -> Float?)?>, expected: Float) {
        compose.runOnUiThread { assertEquals(expected, checkNotNull(checkNotNull(provider.get())()), 0.0001f) }
    }

    private fun playingState(
        activation: Long = 0,
        positionRevision: Long = 0,
        positionMs: Long = 2_000,
        durationMs: Long = 10_000,
        sampleTimeMs: Long = sampledAtMs,
    ): PlayerState {
        val session = PlaybackSessionId("frame-session")
        val entry = QueueEntry(QueueEntryId("frame-entry"), MediaTrack(TrackId("frame-track"), "Frame track", null, null, 10_000))
        val target = PlaybackItemToken(session, entry.entryId, activation = activation)
        val snapshot = PlaybackSnapshot.create(
            sessionId = session,
            queue = PlaybackQueue.create(entries = listOf(entry), current = target),
            transport = PlaybackTransport(phase = PlaybackPhase.Ready, playRequested = true),
            positionRevision = positionRevision,
        )
        return PlayerState(
            connection = PlaybackConnection.Connected(snapshot),
            progress = PlaybackProgress(target, positionRevision = positionRevision, positionMs = positionMs,
                durationMs = durationMs, bufferedPositionMs = null, sampledAtMs = sampleTimeMs, advancing = true),
        )
    }

    private inner class ProgressProbe {
        val clock = CountingPlaybackClock(sampledAtMs)
        val fraction = AtomicReference<(() -> Float?)?>(null)
        var state by mutableStateOf(playingState())
        var reduceMotion by mutableStateOf(false)
        private lateinit var owner: ProgressLifecycleOwner

        fun show() {
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread {
                owner = ProgressLifecycleOwner().apply { registry.currentState = Lifecycle.State.STARTED }
            }
            compose.setContent {
                NullWaveTheme(effects = NullWaveEffects(reduceMotion = reduceMotion, glowEnabled = false)) {
                    val value = rememberPlayerProgress(state, clock, owner)
                    SideEffect { fraction.set(value) }
                    Box(Modifier.size(20.dp).drawBehind {
                        drawRect(Color.White, size = Size(size.width * (value() ?: 0f), size.height))
                    })
                }
            }
            frame()
        }

        fun close() {
            if (::owner.isInitialized) {
                compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.DESTROYED }
            }
        }
    }

    private class TestMotionScale : MotionDurationScale {
        var factor by mutableFloatStateOf(1f)
        override val scaleFactor: Float get() = factor
    }

    private class ProgressLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private class CountingPlaybackClock(initialTime: Long) : PlaybackClock {
        val time = AtomicLong(initialTime)
        val reads = AtomicInteger()
        override fun nowMs(): Long {
            reads.incrementAndGet()
            return time.get()
        }
    }

    private companion object {
        // Deliberately far from the Compose test clock's frame timestamps.
        const val sampledAtMs = 9_000_000L
    }
}
