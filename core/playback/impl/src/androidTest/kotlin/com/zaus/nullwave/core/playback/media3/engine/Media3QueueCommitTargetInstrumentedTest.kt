package com.zaus.nullwave.core.playback.media3.engine

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.FakePlayer
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
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.playback.media3.state.PlaybackServiceRegistry
import com.zaus.nullwave.core.playback.preparation.PreparedQueue
import com.zaus.nullwave.core.playback.runtime.PlaybackIdentities
import com.zaus.nullwave.core.playback.runtime.QueueCommit
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Media3 Player/Timeline/listener semantics without an audio decoder, Activity or permission. */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class Media3QueueCommitTargetInstrumentedTest {
    @Test
    fun installationRetainsOccurrencesSelectionMetadataAndLiveModes() = onMain {
        Fixture().use { fixture ->
            fixture.player.repeatMode = Player.REPEAT_MODE_ALL
            fixture.player.shuffleModeEnabled = true
            val commit = fixture.commit(listOf("a", "b", "a"), startIndex = 2, position = 777, play = false)

            success(fixture.target.install(commit))

            val snapshot = fixture.snapshot()
            assertEquals(commit.entries, snapshot.queue.entries)
            assertEquals(commit.current, snapshot.queue.current)
            assertEquals(3, snapshot.queue.entries.map { it.entryId }.toSet().size)
            assertEquals(RepeatMode.All, snapshot.queue.repeatMode)
            assertTrue(snapshot.queue.shuffleEnabled)
            assertEquals(snapshot.queue.entries.map { it.entryId }.toSet(), snapshot.queue.playOrder.toSet())
            assertEquals(commit.entries[2].entryId.value, fixture.player.currentMediaItem!!.mediaId)
            assertEquals("Track a", fixture.player.currentMediaItem!!.mediaMetadata.title.toString())
            assertEquals("Artist", fixture.player.currentMediaItem!!.mediaMetadata.artist.toString())
            assertEquals(777L, fixture.player.currentPosition)
            assertFalse(snapshot.transport.playRequested)
        }
    }

    @Test
    fun positionSamplingDoesNotRebuildControlOrQueueSnapshots() = onMain {
        Fixture().use { fixture ->
            success(fixture.target.install(fixture.commit(listOf("a"))))
            val before = fixture.snapshot()
            val first = fixture.target.sampleProgress()!!
            fixture.now += 250
            val second = fixture.target.sampleProgress()!!

            assertSame(before, fixture.snapshot())
            assertSame(before.queue, fixture.snapshot().queue)
            assertEquals(before.revision, fixture.snapshot().revision)
            assertEquals(before.positionRevision, second.positionRevision)
            assertEquals(first.sampledAtMs + 250, second.sampledAtMs)
            assertTrue(second.matches(before))
            assertEquals(fixture.player.isPlaying, second.advancing)
        }
    }

    @Test
    fun hugeStartPositionIsBoundedBeforeMedia3MicrosecondConversion() = onMain {
        Fixture().use { fixture ->
            val commit = fixture.commit(listOf("a"), position = Long.MAX_VALUE, play = false)
            success(fixture.target.install(commit))
            assertEquals(Long.MAX_VALUE, commit.prepared.request.startPositionMs)
            assertEquals(listOf(Long.MAX_VALUE / 1_000), fixture.startPositions)
            assertTrue(fixture.player.currentPosition >= 0)
        }
    }

    @Test
    fun seekNeedsKnownDurationAndClampsToActualEngineDuration() = onMain {
        Fixture().use { fixture ->
            success(fixture.target.install(fixture.commit(listOf("a"), play = false)))
            fixture.setDuration(C.TIME_UNSET)
            val token = fixture.snapshot().queue.current!!
            assertFalse(PlaybackCapability.SeekTo in fixture.snapshot().capabilities)
            failure(fixture.target.execute(PlaybackCommand.SeekTo(token, 50_000)), PlaybackCommandError.CommandUnavailable)

            fixture.setDuration(10_000)
            assertTrue(PlaybackCapability.SeekTo in fixture.snapshot().capabilities)
            val revision = fixture.snapshot().positionRevision
            success(fixture.target.execute(PlaybackCommand.SeekTo(token, 50_000)))
            assertEquals(10_000L, fixture.player.currentPosition)
            assertEquals(token, fixture.snapshot().queue.current)
            assertTrue(fixture.snapshot().positionRevision > revision)
            assertTrue(fixture.target.sampleProgress()!!.matches(fixture.snapshot()))
        }
    }

    @Test
    fun oldSeekTokenIsRejectedAfterReturningToTheSameQueueOccurrence() = onMain {
        Fixture().use { fixture ->
            success(fixture.target.install(fixture.commit(listOf("a", "b"), play = false)))
            val firstA = fixture.snapshot().queue.current!!
            fixture.player.seekTo(1, 0)
            fixture.target.publish()
            fixture.player.seekTo(0, 0)
            fixture.target.publish()
            fixture.setDuration(10_000)
            val secondA = fixture.snapshot().queue.current!!

            assertEquals(firstA.entryId, secondA.entryId)
            assertNotEquals(firstA, secondA)
            val before = fixture.player.currentPosition
            failure(fixture.target.execute(PlaybackCommand.SeekTo(firstA, 5_000)), PlaybackCommandError.StaleTarget)
            assertEquals(before, fixture.player.currentPosition)
        }
    }

    @Test
    fun sameOccurrenceRepeatAndEndedReplayUseNewActivationTokens() = onMain {
        Fixture().use { fixture ->
            success(fixture.target.install(fixture.commit(listOf("a"))))
            val first = fixture.snapshot().queue.current!!
            fixture.target.onMediaItemTransition(fixture.player.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            fixture.target.publish()
            val repeated = fixture.snapshot().queue.current!!
            assertEquals(first.entryId, repeated.entryId)
            assertNotEquals(first, repeated)

            fixture.player.setPlaybackState(Player.STATE_ENDED)
            // FakePlayer's synthetic items have no seekable source; explicitly model supported seek.
            fixture.player.addCommands(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            fixture.target.publish()
            success(fixture.target.execute(PlaybackCommand.SetPlayRequested(true)))
            val restarted = fixture.snapshot().queue.current!!
            assertEquals(repeated.entryId, restarted.entryId)
            assertNotEquals(repeated, restarted)
            assertEquals(repeated.activation + 1, restarted.activation)
            assertEquals(0L, fixture.seekPositions.single())
            assertTrue(fixture.snapshot().transport.playRequested)
        }
    }

    @Test
    fun nativeNextRepeatingASingleEntryRejectsThePreviousSeekToken() = onMain {
        assertNativeRepeatReactivates { it.seekToNextMediaItem() }
    }

    @Test
    fun nativePreviousRepeatingASingleEntryRejectsThePreviousSeekToken() = onMain {
        assertNativeRepeatReactivates { it.seekToPreviousMediaItem() }
    }

    @Test
    fun portableNextUsesExactlyOneActivationWhenNativeRepeatAlsoReportsSeekTransition() = onMain {
        Fixture().use { fixture ->
            fixture.player.repeatMode = Player.REPEAT_MODE_ALL
            success(fixture.target.install(fixture.commit(listOf("a"))))
            val before = fixture.snapshot().queue.current!!

            success(fixture.target.execute(PlaybackCommand.Next))

            val after = fixture.snapshot().queue.current!!
            assertEquals(before.entryId, after.entryId)
            assertEquals(before.activation + 1, after.activation)
            failure(fixture.target.execute(PlaybackCommand.SeekTo(before, 100)), PlaybackCommandError.StaleTarget)
        }
    }

    @Test
    fun nativeScrubWithinTheSameEntryKeepsItsActivation() = onMain {
        Fixture().use { fixture ->
            success(fixture.target.install(fixture.commit(listOf("a"), play = false)))
            fixture.setDuration(10_000)
            val before = fixture.snapshot()

            fixture.player.seekTo(1_234)
            fixture.target.publish()

            val after = fixture.snapshot()
            assertEquals(before.queue.current, after.queue.current)
            assertTrue(after.positionRevision > before.positionRevision)
            assertEquals(1_234L, fixture.player.currentPosition)
        }
    }

    @Test
    fun nativeNextFromEndedRepeatingTheSameEntryUsesExactlyOneActivation() = onMain {
        Fixture().use { fixture ->
            fixture.player.repeatMode = Player.REPEAT_MODE_ALL
            success(fixture.target.install(fixture.commit(listOf("a"), play = false)))
            fixture.player.setPosition(5_000)
            fixture.player.setPlaybackState(Player.STATE_ENDED)
            fixture.target.publish()
            val ended = fixture.snapshot().queue.current!!

            fixture.player.seekToNextMediaItem()
            fixture.target.publish()

            val replayed = fixture.snapshot().queue.current!!
            assertEquals(ended.entryId, replayed.entryId)
            assertEquals(ended.activation + 1, replayed.activation)
            assertEquals(Player.STATE_READY, fixture.player.playbackState)
            assertEquals(0L, fixture.player.currentPosition)
        }
    }

    @Test
    fun nativePrepareAtEndedDoesNotInventAnActivationOrReplay() = onMain {
        Fixture().use { fixture ->
            success(fixture.target.install(fixture.commit(listOf("a"))))
            fixture.player.setPlaybackState(Player.STATE_ENDED)
            fixture.target.publish()
            val ended = fixture.snapshot()

            fixture.target.prepareFromNative()

            assertSame(ended, fixture.snapshot())
            assertEquals(Player.STATE_ENDED, fixture.player.playbackState)
        }
    }

    @Test
    fun nativeSeekOutOfEndedReactivatesTheSameOccurrenceOnce() = onMain {
        Fixture().use { fixture ->
            success(fixture.target.install(fixture.commit(listOf("a"), play = false)))
            fixture.player.setPosition(5_000)
            fixture.player.setPlaybackState(Player.STATE_ENDED)
            fixture.player.addCommands(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            fixture.target.publish()
            val ended = fixture.snapshot().queue.current!!

            fixture.player.seekTo(0)
            fixture.target.publish()
            val replayed = fixture.snapshot().queue.current!!

            assertEquals(ended.entryId, replayed.entryId)
            assertEquals(ended.activation + 1, replayed.activation)
            assertEquals(Player.STATE_READY, fixture.player.playbackState)
            assertEquals(0L, fixture.player.currentPosition)
        }
    }

    @Test
    fun engineFailureKeepsOriginalDiagnosticsAndRetryChecksProblemAndActivation() = onMain {
        Fixture().use { fixture ->
            success(fixture.target.install(fixture.commit(listOf("a"))))
            val original = IOException("provider message")
            val error = PlaybackException("media3 message", original, PlaybackException.ERROR_CODE_IO_NO_PERMISSION)
            fixture.player.setPlayerError(error)
            fixture.target.publish()
            val before = fixture.snapshot()
            val problem = before.transport.problem!!
            assertEquals(PlaybackPhase.Idle, before.transport.phase)
            assertEquals(PlaybackProblemCode.AccessDenied, problem.code)
            assertEquals(before.queue.current, problem.target)
            assertSame(error, fixture.target.lastEngineFailure!!.cause)
            assertEquals("media3 message", fixture.target.lastEngineFailure!!.originalMessage)
            assertTrue(PlaybackCapability.Retry in before.capabilities)

            success(fixture.target.execute(PlaybackCommand.Retry(before.queue.current!!, problem.id)))
            val retried = fixture.snapshot()
            assertNotEquals(before.queue.current, retried.queue.current)
            assertNull(retried.transport.problem)
            assertSame(error, fixture.target.lastEngineFailure!!.cause)
            failure(fixture.target.execute(PlaybackCommand.Retry(before.queue.current!!, problem.id)), PlaybackCommandError.StaleTarget)
        }
    }

    @Test
    fun missingEngineCommandsAreReflectedInCapabilitiesAndRejectWithoutReplacingQueue() = onMain {
        Fixture().use { fixture ->
            success(fixture.target.install(fixture.commit(listOf("old"))))
            val old = fixture.snapshot().queue
            fixture.player.removeCommands(Player.COMMAND_PREPARE)
            fixture.target.publish()

            assertFalse(PlaybackCapability.ReplaceQueue in fixture.snapshot().capabilities)
            failure(fixture.target.install(fixture.commit(listOf("new"))), PlaybackCommandError.CommandUnavailable)
            assertSame(old, fixture.snapshot().queue)
            assertEquals("old", fixture.snapshot().queue.entries.single().track.id.value)
        }
    }

    @Test
    fun stopAndClearPublishesIdleWithoutCurrentItemCommandsOrPosition() = onMain {
        Fixture().use { fixture ->
            success(fixture.target.install(fixture.commit(listOf("a"))))
            success(fixture.target.execute(PlaybackCommand.StopAndClear))

            val snapshot = fixture.snapshot()
            assertTrue(snapshot.queue.entries.isEmpty())
            assertNull(snapshot.queue.current)
            assertEquals(PlaybackPhase.Idle, snapshot.transport.phase)
            assertFalse(snapshot.transport.playRequested)
            assertNull(snapshot.transport.problem)
            assertFalse(PlaybackCapability.SetPlayRequested in snapshot.capabilities)
            assertNull(fixture.target.sampleProgress())
            assertNull(fixture.registry.anchor.value)
        }
    }

    @Test
    fun targetCloseDetachesItsPublicationsButDoesNotReleaseServicePlayer() = onMain {
        Fixture().use { fixture ->
            success(fixture.target.install(fixture.commit(listOf("a"))))
            fixture.target.close()

            assertNull(fixture.registry.snapshot.value)
            assertNull(fixture.registry.anchor.value)
            assertNull(fixture.registry.sampleProgress())
            assertFalse(fixture.player.released)
        }
    }

    @Test
    fun oldTargetCloseCannotDetachAnotherServiceIncarnation() = onMain {
        val registry = PlaybackServiceRegistry()
        val old = Fixture(registry)
        val replacement = Fixture(registry)
        try {
            val replacementSession = replacement.snapshot().sessionId
            old.target.close()
            assertEquals(replacementSession, registry.snapshot.value!!.sessionId)
            assertNotEquals(old.identities.sessionId, replacementSession)
        } finally {
            old.close()
            replacement.close()
        }
    }

    private fun assertNativeRepeatReactivates(seek: (Player) -> Unit) {
        Fixture().use { fixture ->
            fixture.player.repeatMode = Player.REPEAT_MODE_ALL
            success(fixture.target.install(fixture.commit(listOf("a"))))
            fixture.setDuration(10_000)
            val before = fixture.snapshot().queue.current!!
            val transitions = mutableListOf<Int>()
            fixture.player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { transitions += reason }
            })

            seek(fixture.player)
            fixture.target.publish()

            assertEquals(listOf(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK), transitions)
            val after = fixture.snapshot().queue.current!!
            assertEquals(before.entryId, after.entryId)
            assertEquals(before.activation + 1, after.activation)
            failure(fixture.target.execute(PlaybackCommand.SeekTo(before, 500)), PlaybackCommandError.StaleTarget)
        }
    }

    private class Fixture(val registry: PlaybackServiceRegistry = PlaybackServiceRegistry()) : AutoCloseable {
        val player = FakePlayer(Player.STATE_IDLE, false, emptyList(), 1f, 0)
        var now = 100L
        private var entry = 0
        val identities = PlaybackIdentities(PlaybackSessionId(java.util.UUID.randomUUID().toString())) {
            QueueEntryId("entry-${entry++}")
        }
        val startPositions = mutableListOf<Long>()
        val seekPositions = mutableListOf<Long>()
        private val observedPlayer = object : ForwardingPlayer(player) {
            override fun clearMediaItems() {
                // FakePlayer supports installing an empty playlist but does not implement removal.
                player.setMediaItems(emptyList())
            }

            override fun seekTo(positionMs: Long) {
                seekPositions += positionMs
                super.seekTo(positionMs)
            }

            override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
                startPositions += startPositionMs
                super.setMediaItems(mediaItems, startIndex, startPositionMs)
            }
        }
        val target = Media3QueueCommitTarget(observedPlayer, identities, registry, PlaybackClock { now })

        fun snapshot() = registry.snapshot.value!!

        fun setDuration(duration: Long) {
            val uid = player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window()).uid as String
            player.setDuration(uid, duration)
            // No extractor runs in this fake. Configure the capability independently from duration.
            player.addCommands(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            target.publish()
        }

        fun commit(values: List<String>, startIndex: Int = 0, position: Long = 0, play: Boolean = true): QueueCommit {
            val resources = values.map { value ->
                ResolvedPlaybackTrack(
                    MediaTrack(TrackId(value), "Track $value", "Artist", "Album", 90_000),
                    "content://com.zaus.nullwave.test/$value",
                )
            }
            val request = PlaybackQueueRequest.create(resources.map { it.track.id }, startIndex, position, play)
            return QueueCommit.create(PreparedQueue.create(request, resources), identities)
        }

        override fun close() {
            target.close()
            if (!player.released) player.release()
        }
    }

    private fun onMain(block: () -> Unit) {
        var outcome: Result<Unit>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { outcome = runCatching(block) }
        checkNotNull(outcome).getOrThrow()
    }
    private fun success(result: EmptyResult<PlaybackCommandError>) = assertEquals(AppResult.Success(Unit), result)
    private fun failure(result: EmptyResult<PlaybackCommandError>, expected: PlaybackCommandError) =
        assertEquals(expected, (result as AppResult.Failure).error)
}
