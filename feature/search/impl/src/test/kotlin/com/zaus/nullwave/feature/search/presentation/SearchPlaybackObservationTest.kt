package com.zaus.nullwave.feature.search.presentation

import androidx.lifecycle.SavedStateHandle
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.search.impl.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Engine snapshots, pre-dispatch baseline, access and UI observation lifetime. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchPlaybackObservationTest {
    private val fixture = SearchPlaybackFixture()

    @Before
    fun setUp() = fixture.setUp()

    @After
    fun tearDown() = fixture.tearDown()

    @Test
    fun `opening and reading Search never acquires a playback connection before the first tap`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            playback.states.value = problemSnapshot(PlaybackProblemCode.Io, "unobserved")
            runCurrent()

            assertFalse(vm.snapshot.value.state.monitorPlayback)
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(0, playback.stateObservations)
            assertEquals(0, playback.activeStateCollectors)
            assertEquals(0, playback.progressObservations)
            assertTrue(playback.calls.isEmpty())
        }
    }

    @Test
    fun `first attempt observes a new engine failure even before its install acknowledgement`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            playback.states.value = problemSnapshot(PlaybackProblemCode.Io, "before-first-ack")
            runCurrent()
            assertTrue(vm.snapshot.value.state.monitorPlayback)
            assertEquals(UiText.Resource(R.string.search_playback_read_failed), vm.snapshot.value.state.playbackError)
            assertEquals(1, playback.stateObservations)

            respond(AppResult.Success(Unit))

            assertTrue(vm.snapshot.value.state.monitorPlayback)
            assertEquals(1, playback.stateObservations)
            assertEquals(UiText.Resource(R.string.search_playback_read_failed), vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackProblemId("before-first-ack"), vm.snapshot.value.state.lastPlaybackProblem?.problemId)
        }
    }

    @Test
    fun `cancelled first acknowledgement and failed replacement still observe the installed queue`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            val installed = playback.calls.single()
            playback.states.value = healthySnapshot()
            runCurrent()

            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            assertTrue(installed.waiterCancelled)
            respond(AppResult.Failure(PlaybackCommandError.SourceFailure(alpha.id, PlaybackSourceError.NotFound)))
            installed.reply.complete(AppResult.Success(Unit))
            runCurrent()
            assertEquals(UiText.Resource(R.string.search_playback_not_found), vm.snapshot.value.state.playbackError)

            playback.states.value = problemSnapshot(PlaybackProblemCode.DecoderFailure, "installed-with-cancelled-ack")
            runCurrent()

            assertTrue(vm.snapshot.value.state.monitorPlayback)
            assertEquals(1, playback.activeStateCollectors)
            assertEquals(UiText.Resource(R.string.search_playback_decoder_failed), vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackProblemId("installed-with-cancelled-ack"), vm.snapshot.value.state.lastPlaybackProblem?.problemId)
            assertEquals(0, playback.progressObservations)
        }
    }

    @Test
    fun `healthy replacement removes the previous activation failure even before the new acknowledgement`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            playback.states.value = problemSnapshot(PlaybackProblemCode.DecoderFailure, "old-during-preparation")
            runCurrent()
            assertNotNull(vm.snapshot.value.state.playbackError)

            playback.states.value = healthySnapshot(entryId = "new-alpha", track = alpha, activation = 1)
            runCurrent()
            assertNull(vm.snapshot.value.state.playbackError)
            respond(AppResult.Success(Unit))

            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackProblemId("old-during-preparation"), vm.snapshot.value.state.lastPlaybackProblem?.problemId)
        }
    }

    @Test
    fun `healthy snapshots never erase a command refusal for the replacement`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            respond(AppResult.Failure(PlaybackCommandError.SourceFailure(alpha.id, PlaybackSourceError.Io)))
            val notice = vm.snapshot.value.state.playbackNotice
            assertTrue(notice is SearchPlaybackNotice.Command)

            playback.states.value = healthySnapshot(entryId = "still-playing-beta", activation = 3)
            runCurrent()

            assertSame(notice, vm.snapshot.value.state.playbackNotice)
            assertEquals(UiText.Resource(R.string.search_playback_read_failed), vm.snapshot.value.state.playbackError)
        }
    }

    @Test
    fun `new activation entry session and empty queue clear only an old engine notice`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            val replacements = listOf(
                healthySnapshot(activation = 1),
                healthySnapshot(entryId = "another-occurrence-of-beta"),
                healthySnapshot(sessionId = "another-service"),
                PlaybackConnection.Connected(PlaybackSnapshot.create(PlaybackSessionId("empty-service"))),
            )

            for ((index, replacement) in replacements.withIndex()) {
                playback.states.value = problemSnapshot(PlaybackProblemCode.Io, "old-engine-$index")
                runCurrent()
                assertTrue(vm.snapshot.value.state.playbackNotice is SearchPlaybackNotice.Engine)
                playback.states.value = replacement
                runCurrent()

                assertNull("The old activation must not own a notice after replacement $index", vm.snapshot.value.state.playbackError)
                assertEquals(PlaybackProblemId("old-engine-$index"), vm.snapshot.value.state.lastPlaybackProblem?.problemId)
            }
        }
    }

    @Test
    fun `preexisting access problem cannot close the catalog when the first attempt starts`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            val catalog = vm.snapshot.value.state.body
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "preexisting-denial")

            send(vm, SearchIntent.PlayTrack(beta.id, songs))

            assertEquals(1, playback.calls.size)
            assertSame(catalog, vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
            respond(AppResult.Success(Unit))
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "preexisting-denial", revision = 1)
            runCurrent()
            assertSame(catalog, vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)

            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "new-denial", activation = 1)
            runCurrent()
            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertEquals(UiText.Resource(R.string.search_playback_access_denied), vm.snapshot.value.state.playbackError)
        }
    }

    @Test
    fun `replacement during baseline acquisition cancels only the first wait and dispatches the latest track`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            val allowConnection = CompletableDeferred<Unit>()
            playback.beforeCommandDispatch = { allowConnection.await() }
            playback.states.value = PlaybackConnection.Connecting
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            assertTrue(playback.calls.isEmpty())
            assertEquals(0, playback.activeStateCollectors)

            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            assertTrue(playback.calls.isEmpty())
            assertEquals(0, playback.activeStateCollectors)
            playback.states.value = healthySnapshot()
            allowConnection.complete(Unit)
            runCurrent()

            assertEquals(1, playback.calls.size)
            assertEquals(2, request().startIndex)
            respond(AppResult.Success(Unit))
            assertEquals(1, playback.activeStateCollectors)
            assertNull(vm.snapshot.value.state.playbackError)
        }
    }

    @Test
    fun `connection refusal without a baseline preserves command errors and a later tap can dispatch`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            for ((failure, message) in listOf(
                PlaybackCommandError.ConnectionUnavailable to R.string.search_playback_connection_failed,
                PlaybackCommandError.StartNotAllowed to R.string.search_playback_start_not_allowed,
            )) {
                playback.connectionFailure = AppResult.Failure(failure, IllegalStateException("Original connection diagnostic"))
                send(vm, SearchIntent.PlayTrack(beta.id, songs))

                assertTrue(playback.calls.isEmpty())
                assertEquals(UiText.Resource(message), vm.snapshot.value.state.playbackError)
                assertEquals(0, playback.activeStateCollectors)
            }

            // A failed connection is not a baseline for a previously unseen service problem.
            playback.connectionFailure = null
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "old-after-reconnect")
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            assertEquals(1, playback.calls.size)
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
            respond(AppResult.Success(Unit))

            assertEquals(1, playback.activeStateCollectors)
        }
    }

    @Test
    fun `problem identity is scoped to the service session for baseline and dismissal`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            playback.states.value = problemSnapshot(PlaybackProblemCode.Io, "reused-id")
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            assertNull(vm.snapshot.value.state.playbackError)

            playback.states.value = problemSnapshot(PlaybackProblemCode.Io, "reused-id", sessionId = "second-service")
            runCurrent()
            assertEquals(UiText.Resource(R.string.search_playback_read_failed), vm.snapshot.value.state.playbackError)
            assertEquals(SearchPlaybackProblemKey(PlaybackSessionId("second-service"), PlaybackProblemId("reused-id")),
                vm.snapshot.value.state.lastPlaybackProblem)
            send(vm, SearchIntent.DismissPlaybackError)
            playback.states.value = problemSnapshot(PlaybackProblemCode.Io, "reused-id", revision = 1, sessionId = "second-service")
            runCurrent()
            assertNull(vm.snapshot.value.state.playbackError)

            playback.states.value = problemSnapshot(PlaybackProblemCode.Io, "reused-id", sessionId = "third-service")
            runCurrent()
            assertEquals(UiText.Resource(R.string.search_playback_read_failed), vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackSessionId("third-service"), vm.snapshot.value.state.lastPlaybackProblem?.sessionId)
        }
    }

    @Test
    fun `asynchronous engine failures produce localized messages without replacing catalog data`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            val catalog = vm.snapshot.value.state.body
            val failures = listOf(
                PlaybackProblemCode.NotFound to R.string.search_playback_not_found,
                PlaybackProblemCode.Io to R.string.search_playback_read_failed,
                PlaybackProblemCode.UnsupportedMedia to R.string.search_playback_unsupported,
                PlaybackProblemCode.DecoderFailure to R.string.search_playback_decoder_failed,
                PlaybackProblemCode.OutputUnavailable to R.string.search_playback_output_unavailable,
                PlaybackProblemCode.Unknown to R.string.search_playback_failed,
            )

            for ((code, message) in failures) {
                playback.states.value = problemSnapshot(code, "engine:$code")
                runCurrent()
                assertEquals(UiText.Resource(message), vm.snapshot.value.state.playbackError)
                assertEquals(PlaybackProblemId("engine:$code"), vm.snapshot.value.state.lastPlaybackProblem?.problemId)
                assertSame(catalog, vm.snapshot.value.state.body)
            }
            assertEquals(1, playback.calls.size)
            assertEquals(1, playback.stateObservations)
            assertEquals(0, playback.progressObservations)
        }
    }

    @Test
    fun `dismissed engine problem stays acknowledged across publications and a new problem appears`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            playback.states.value = problemSnapshot(PlaybackProblemCode.Io, "first")
            runCurrent()
            assertNotNull(vm.snapshot.value.state.playbackError)
            send(vm, SearchIntent.DismissPlaybackError)

            playback.states.value = problemSnapshot(PlaybackProblemCode.Io, "first", revision = 1)
            runCurrent()
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackProblemId("first"), vm.snapshot.value.state.lastPlaybackProblem?.problemId)

            playback.states.value = problemSnapshot(PlaybackProblemCode.Io, "second", revision = 2)
            runCurrent()
            assertEquals(UiText.Resource(R.string.search_playback_read_failed), vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackProblemId("second"), vm.snapshot.value.state.lastPlaybackProblem?.problemId)
        }
    }

    @Test
    fun `fresh granted access on resume does not erase a playback failure`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            playback.states.value = problemSnapshot(PlaybackProblemCode.DecoderFailure, "decoder")
            runCurrent()
            val message = vm.snapshot.value.state.playbackError

            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(songs)))

            assertSame(message, vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackProblemId("decoder"), vm.snapshot.value.state.lastPlaybackProblem?.problemId)
            assertEquals(1, playback.calls.size)
        }
    }

    @Test
    fun `connectivity transitions alone do not invent a playback problem`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))

            for (connection in listOf(
                PlaybackConnection.Connecting,
                PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected),
                PlaybackConnection.Disconnected,
            )) {
                playback.states.value = connection
                runCurrent()
                assertNull(vm.snapshot.value.state.playbackError)
            }
        }
    }

    @Test
    fun `query clear edit and locale changes do not cancel an accepted playback command`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            val accepted = playback.calls.single()

            send(vm, SearchIntent.ClearQuery)
            assertEquals(SearchBody.Idle, vm.snapshot.value.state.body)
            send(vm, SearchIntent.QueryChanged("another"))
            send(vm, SearchIntent.LocaleChanged("ru-RU"))

            assertFalse(accepted.waiterCancelled)
            assertFalse(accepted.reply.isCancelled)
            assertEquals(songs.map { it.id }, request().trackIds)
            assertEquals(1, request().startIndex)
            respond(AppResult.Failure(PlaybackCommandError.StartNotAllowed))
            assertEquals(UiText.Resource(R.string.search_playback_start_not_allowed), vm.snapshot.value.state.playbackError)
            assertEquals("another", vm.snapshot.value.state.rawQuery)
            assertEquals("ru-RU", vm.snapshot.value.state.localeTag)
            assertEquals(1, playback.calls.size)
        }
    }

    @Test
    fun `late engine denial after clearing the query still gates the next edit`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            send(vm, SearchIntent.ClearQuery)
            val reads = repository.calls.size

            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "denied-after-clear")
            runCurrent()
            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            send(vm, SearchIntent.QueryChanged("another"))
            settle()

            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertEquals(SearchAccess.Required, vm.snapshot.value.state.access)
            assertEquals(reads, repository.calls.size)
            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(songs)))
            assertTrue(vm.snapshot.value.state.body is SearchBody.Ready)
        }
    }

    @Test
    fun `unbind immediately releases observations while an accepted command finishes independently`() = with(fixture) {
        runTest(dispatcher) {
            val vm = createVm()
            val binding = bind(vm)
            send(vm, SearchIntent.AccessChecked(true))
            send(vm, SearchIntent.QueryChanged("m4a"))
            settle()
            reply(AppResult.Success(results(songs)))
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            val accepted = playback.calls.single()

            binding.cancel()
            runCurrent()

            assertEquals(0, repository.activeCount)
            assertEquals(0, playback.activeStateCollectors)
            assertFalse(accepted.waiterCancelled)
            assertFalse(accepted.reply.isCancelled)
            respond(AppResult.Success(Unit))
            playback.states.value = problemSnapshot(PlaybackProblemCode.DecoderFailure, "while-unbound")
            val rebound = bind(vm)
            assertEquals(UiText.Resource(R.string.search_playback_decoder_failed), vm.snapshot.value.state.playbackError)
            assertEquals(1, playback.calls.size)
            assertEquals(0, playback.progressObservations)

            send(vm, SearchIntent.DismissPlaybackError)
            rebound.cancel()
            runCurrent()
            bind(vm)
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(1, playback.calls.size)
            store.clear()
            runCurrent()
            assertEquals(0, playback.activeStateCollectors)
        }
    }

    @Test
    fun `restoration keeps raw query but never replays playback or restores its transient state`() = with(fixture) {
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val vm = readyVm(songs, handle)
            send(vm, SearchIntent.QueryChanged("  M4A  "))
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            playback.states.value = problemSnapshot(PlaybackProblemCode.Io, "not-persisted")
            runCurrent()
            assertTrue(vm.snapshot.value.state.monitorPlayback)
            assertNotNull(vm.snapshot.value.state.playbackError)
            val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            store.clear()
            runCurrent()

            val restored = createVm(restoredHandle)
            bind(restored)

            assertEquals("  M4A  ", restored.snapshot.value.state.rawQuery)
            assertEquals(SearchAccess.Unchecked, restored.snapshot.value.state.access)
            assertFalse(restored.snapshot.value.state.monitorPlayback)
            assertNull(restored.snapshot.value.state.lastPlaybackProblem)
            assertNull(restored.snapshot.value.state.playbackError)
            assertEquals(1, playback.calls.size)
            assertEquals(0, playback.activeStateCollectors)
        }
    }

    @Test
    fun `delayed state collection compares against dispatch baseline instead of hiding a new failure`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            val allowObservation = CompletableDeferred<Unit>()
            playback.beforeStateCollection = { allowObservation.await() }
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-dispatch")
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            assertEquals(1, playback.calls.size)
            assertNull(vm.snapshot.value.state.playbackError)

            playback.states.value = problemSnapshot(PlaybackProblemCode.DecoderFailure, "before-collection", activation = 1)
            runCurrent()
            assertNull(vm.snapshot.value.state.playbackError)
            allowObservation.complete(Unit)
            runCurrent()

            assertEquals(UiText.Resource(R.string.search_playback_decoder_failed), vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackProblemId("before-collection"), vm.snapshot.value.state.lastPlaybackProblem?.problemId)
            respond(AppResult.Success(Unit))
            assertNotNull(vm.snapshot.value.state.playbackError)
        }
    }
}
