package com.zaus.nullwave.feature.search.presentation

import com.zaus.nullwave.core.mvi.testing.bind
import com.zaus.nullwave.core.mvi.testing.send
import androidx.lifecycle.SavedStateHandle
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.feature.player.api.launch.PlaybackProblemKey
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.search.impl.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
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

/** Highlight and access observations share a controller. Access monitoring waits for the first dispatch baseline. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchPlaybackObservationTest {
    private val fixture = SearchPlaybackFixture()

    @Before
    fun setUp() = fixture.setUp()

    @After
    fun tearDown() = fixture.tearDown()

    @Test
    fun `opening Search observes the current track without handling access denials before the first tap`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-first-tap")
            runCurrent()

            assertFalse(vm.snapshot.value.state.playbackLaunch.monitoring)
            assertNull(vm.snapshot.value.state.playbackError)
            assertNull(vm.snapshot.value.state.playbackLaunch.handledDenial)
            assertEquals(beta.id, vm.snapshot.value.state.playingTrackId)
            assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
            assertEquals(1, playback.stateObservations)
            assertEquals(1, playback.activeStateCollectors)
            assertEquals(0, playback.progressObservations)
            assertTrue(playback.calls.isEmpty())
        }
    }

    @Test
    fun `highlight follows external playback through disconnect and clears on a confirmed empty queue`() = with(fixture) {
        runTest(dispatcher) {
            playback.states.value = healthySnapshot()
            val vm = readyVm(songs)
            val displayed = ready(vm)
            assertEquals(beta.id, vm.snapshot.value.state.playingTrackId)

            playback.states.value = PlaybackConnection.Disconnected
            runCurrent()
            assertEquals(beta.id, vm.snapshot.value.state.playingTrackId)
            playback.states.value = healthySnapshot(entryId = "next", track = alpha)
            runCurrent()
            assertEquals(alpha.id, vm.snapshot.value.state.playingTrackId)
            playback.states.value = PlaybackConnection.Connected(PlaybackSnapshot.create(PlaybackSessionId("empty")))
            runCurrent()
            assertNull(vm.snapshot.value.state.playingTrackId)
            assertSame(displayed, ready(vm))
            assertEquals(1, repository.subscriptions.size)
            assertEquals(0, playback.progressObservations)
            assertTrue(playback.calls.isEmpty())
        }
    }

    @Test
    fun `first attempt gates an engine denial before acknowledgement without a local banner`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-first-ack")
            runCurrent()
            assertTrue(vm.snapshot.value.state.playbackLaunch.monitoring)
            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(2, playback.stateObservations)

            respond(AppResult.Success(Unit))

            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(2, playback.stateObservations)
            assertEquals(PlaybackProblemId("before-first-ack"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
        }
    }

    @Test
    fun `cancelled acknowledgement and failed replacement still gate later denial while preserving the command notice`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            val installed = playback.calls.single()
            playback.states.value = healthySnapshot()
            runCurrent()
            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            assertTrue(installed.waiterCancelled)
            respond(AppResult.Failure(PlaybackCommandError.SourceFailure(alpha.id, PlaybackSourceError.NotFound)))
            val notice = vm.snapshot.value.state.playbackLaunch.notice
            installed.reply.complete(AppResult.Success(Unit))
            runCurrent()
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "installed-with-cancelled-ack")
            runCurrent()

            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertSame(notice, vm.snapshot.value.state.playbackLaunch.notice)
            assertEquals(UiText.Resource(R.string.search_playback_not_found), vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackProblemId("installed-with-cancelled-ack"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
            assertEquals(2, playback.activeStateCollectors)
            assertEquals(0, playback.progressObservations)
        }
    }

    @Test
    fun `engine snapshots during a replacement never create a local command notice`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            val catalog = vm.snapshot.value.state.body
            playback.states.value = problemSnapshot(PlaybackProblemCode.DecoderFailure, "old-during-preparation")
            runCurrent()
            assertNull(vm.snapshot.value.state.playbackLaunch.notice)
            assertSame(catalog, vm.snapshot.value.state.body)
            playback.states.value = healthySnapshot(entryId = "new-alpha", track = alpha, activation = 1)
            runCurrent()
            respond(AppResult.Success(Unit))

            assertNull(vm.snapshot.value.state.playbackLaunch.notice)
            assertNull(vm.snapshot.value.state.playbackLaunch.handledDenial)
            assertSame(catalog, vm.snapshot.value.state.body)
        }
    }

    @Test
    fun `healthy snapshots never erase a command refusal for the replacement`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            respond(AppResult.Failure(PlaybackCommandError.SourceFailure(alpha.id, PlaybackSourceError.Io)))
            val notice = vm.snapshot.value.state.playbackLaunch.notice
            assertEquals(UiText.Resource(R.string.search_playback_read_failed), notice)

            playback.states.value = healthySnapshot(entryId = "still-playing-beta", activation = 3)
            runCurrent()

            assertSame(notice, vm.snapshot.value.state.playbackLaunch.notice)
            assertEquals(UiText.Resource(R.string.search_playback_read_failed), vm.snapshot.value.state.playbackError)
        }
    }

    @Test
    fun `new activation entry session and empty queue preserve a local command notice`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
            val notice = vm.snapshot.value.state.playbackLaunch.notice
            assertNotNull(notice)
            val replacements = listOf(
                healthySnapshot(activation = 1),
                healthySnapshot(entryId = "another-occurrence-of-beta"),
                healthySnapshot(sessionId = "another-service"),
                PlaybackConnection.Connected(PlaybackSnapshot.create(PlaybackSessionId("empty-service"))),
            )

            for (replacement in replacements) {
                playback.states.value = replacement
                runCurrent()
                assertSame(notice, vm.snapshot.value.state.playbackLaunch.notice)
                assertNull(vm.snapshot.value.state.playbackLaunch.handledDenial)
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
            assertNull(vm.snapshot.value.state.playbackLaunch.handledDenial)
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "new-denial", activation = 1)
            runCurrent()

            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
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
            assertEquals(1, playback.activeStateCollectors)

            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            assertTrue(playback.calls.isEmpty())
            assertEquals(1, playback.activeStateCollectors)
            playback.states.value = healthySnapshot()
            allowConnection.complete(Unit)
            runCurrent()

            assertEquals(1, playback.calls.size)
            assertEquals(2, request().startIndex)
            respond(AppResult.Success(Unit))
            assertEquals(2, playback.activeStateCollectors)
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
                assertEquals(1, playback.activeStateCollectors)
            }

            // A failed connection is not a baseline for a previously unseen service problem.
            playback.connectionFailure = null
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "old-after-reconnect")
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            assertEquals(1, playback.calls.size)
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
            respond(AppResult.Success(Unit))

            assertEquals(2, playback.activeStateCollectors)
        }
    }

    @Test
    fun `denial identity is scoped to the service session for baseline and deduplication`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "reused-id")
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "reused-id", sessionId = "second-service")
            runCurrent()
            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertEquals(PlaybackProblemKey(PlaybackSessionId("second-service"), PlaybackProblemId("reused-id")),
                vm.snapshot.value.state.playbackLaunch.handledDenial)
            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(songs)))
            val checkedCatalog = vm.snapshot.value.state.body
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "reused-id", revision = 1, sessionId = "second-service")
            runCurrent()
            assertSame(checkedCatalog, vm.snapshot.value.state.body)
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "reused-id", sessionId = "third-service")
            runCurrent()

            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackSessionId("third-service"), vm.snapshot.value.state.playbackLaunch.handledDenial?.sessionId)
        }
    }

    @Test
    fun `non-access engine problems neither create nor erase local command notices`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            val catalog = vm.snapshot.value.state.body
            val failures = PlaybackProblemCode.entries.filter { it != PlaybackProblemCode.AccessDenied }

            for (code in failures) {
                playback.states.value = problemSnapshot(code, "before-command:$code")
                runCurrent()
                assertNull(vm.snapshot.value.state.playbackLaunch.notice)
                assertNull(vm.snapshot.value.state.playbackLaunch.handledDenial)
                assertSame(catalog, vm.snapshot.value.state.body)
            }
            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            respond(AppResult.Failure(PlaybackCommandError.SourceFailure(alpha.id, PlaybackSourceError.Io)))
            val notice = vm.snapshot.value.state.playbackLaunch.notice
            assertNotNull(notice)
            for (code in failures) {
                playback.states.value = problemSnapshot(code, "after-command:$code")
                runCurrent()
                assertSame(notice, vm.snapshot.value.state.playbackLaunch.notice)
                assertNull(vm.snapshot.value.state.playbackLaunch.handledDenial)
                assertSame(catalog, vm.snapshot.value.state.body)
            }
            assertEquals(2, playback.calls.size)
            assertEquals(2, playback.stateObservations)
            assertEquals(0, playback.progressObservations)
        }
    }

    @Test
    fun `fresh access survives repeated denial through healthy and other engine snapshots until a new denial`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Success(Unit))
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "first")
            runCurrent()
            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(songs)))
            val checkedCatalog = vm.snapshot.value.state.body
            for (connection in listOf(
                healthySnapshot(),
                problemSnapshot(PlaybackProblemCode.Io, "unrelated-io"),
                problemSnapshot(PlaybackProblemCode.AccessDenied, "first", revision = 1),
            )) {
                playback.states.value = connection
                runCurrent()
                assertSame(checkedCatalog, vm.snapshot.value.state.body)
                assertNull(vm.snapshot.value.state.playbackError)
                assertEquals(PlaybackProblemId("first"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
            }
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "second", revision = 2)
            runCurrent()

            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackProblemId("second"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
        }
    }

    @Test
    fun `fresh granted access on resume does not erase a command refusal`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Failure(PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.Io)))
            val notice = vm.snapshot.value.state.playbackLaunch.notice
            assertNotNull(notice)
            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(songs)))

            assertSame(notice, vm.snapshot.value.state.playbackLaunch.notice)
            assertNull(vm.snapshot.value.state.playbackLaunch.handledDenial)
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
            assertNull(vm.snapshot.value.state.playbackLaunch.notice)
            send(vm, SearchIntent.QueryChanged("another"))
            settle()

            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertEquals(SearchAccess.Required, vm.snapshot.value.state.access)
            assertEquals(reads, repository.calls.size)
            assertNull(vm.snapshot.value.state.playbackLaunch.notice)
            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(songs)))
            assertTrue(vm.snapshot.value.state.body is SearchBody.Ready)
        }
    }

    @Test
    fun `unbind immediately releases observations and rebind gates a late denial without replaying commands`() = with(fixture) {
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
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "while-unbound")
            val rebound = bind(vm)
            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(1, playback.calls.size)
            assertEquals(0, playback.progressObservations)
            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(songs)))
            rebound.cancel()
            runCurrent()
            bind(vm)
            settle()
            reply(AppResult.Success(results(songs)))

            assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(1, playback.calls.size)
            assertEquals(PlaybackProblemId("while-unbound"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
            store.clear()
            runCurrent()
            assertEquals(0, playback.activeStateCollectors)
        }
    }

    @Test
    fun `restoration keeps raw query but never replays playback or restores command error and denial state`() = with(fixture) {
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val vm = readyVm(songs, handle)
            send(vm, SearchIntent.QueryChanged("  M4A  "))
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Failure(PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.Io)))
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "not-persisted")
            runCurrent()
            assertTrue(vm.snapshot.value.state.playbackLaunch.monitoring)
            assertNotNull(vm.snapshot.value.state.playbackError)
            assertNotNull(vm.snapshot.value.state.playbackLaunch.handledDenial)
            val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            store.clear()
            runCurrent()
            val restored = createVm(restoredHandle)
            assertNull(restored.snapshot.value.state.playingTrackId)
            playback.states.value = healthySnapshot(track = alpha)
            bind(restored)

            assertEquals("  M4A  ", restored.snapshot.value.state.rawQuery)
            assertEquals(SearchAccess.Unchecked, restored.snapshot.value.state.access)
            assertFalse(restored.snapshot.value.state.playbackLaunch.monitoring)
            assertEquals(alpha.id, restored.snapshot.value.state.playingTrackId)
            assertNull(restored.snapshot.value.state.playbackLaunch.handledDenial)
            assertNull(restored.snapshot.value.state.playbackError)
            assertEquals(1, playback.calls.size)
            assertEquals(1, playback.activeStateCollectors)
        }
    }

    @Test
    fun `Back to Search keeps the results when playback replays a denial published before the grant`() = with(fixture) {
        runTest(dispatcher) {
            val reconnect = CompletableDeferred<Unit>()
            val vm = returnToSearchBeforePlaybackReconnects(reconnect)
            val displayed = ready(vm)

            reconnect.complete(Unit)
            runCurrent()

            assertSame(displayed, vm.snapshot.value.state.body)
        }
    }

    @Test
    fun `Back keeps refreshed results when a Play denied while Search was unbound replies late`() = with(fixture) {
        runTest(dispatcher) {
            val vm = leaveSearchWithPlayPending()
            val deniedBeforeBack = playDenied()
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-back")
            restoreSearch(vm, SearchIntent.AccessChecked(true))
            val refreshed = ready(vm)

            respond(deniedBeforeBack)

            assertSame(refreshed, vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
        }
    }

    @Test
    fun `Back to Search hides the results when the first reconnected denial was published after the grant`() = with(fixture) {
        runTest(dispatcher) {
            val reconnect = CompletableDeferred<Unit>()
            val vm = returnToSearchBeforePlaybackReconnects(reconnect)
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "after-grant")

            reconnect.complete(Unit)
            runCurrent()

            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
        }
    }

    @Test
    fun `a delayed denial cleared by the service before the grant leaves Search results visible`() = with(fixture) {
        runTest(dispatcher) {
            val captured = CompletableDeferred<Unit>()
            val deliver = CompletableDeferred<Unit>()
            playback.stateUpdates = playback.states.onEach { connection ->
                if ((connection as? PlaybackConnection.Connected)?.snapshot?.transport?.problem != null) {
                    captured.complete(Unit)
                    deliver.await()
                }
            }
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "cleared")
            runCurrent()
            assertTrue(captured.isCompleted)
            playback.states.value = healthySnapshot()
            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(songs)))
            val displayed = ready(vm)

            deliver.complete(Unit)
            runCurrent()

            assertSame(displayed, vm.snapshot.value.state.body)
        }
    }

    private suspend fun TestScope.leaveSearchWithPlayPending() = with(fixture) {
        val vm = createVm()
        val binding = bind(vm)
        send(vm, SearchIntent.AccessChecked(true))
        send(vm, SearchIntent.QueryChanged("m4a"))
        settle()
        reply(AppResult.Success(results(songs)))
        send(vm, SearchIntent.PlayTrack(beta.id, songs))
        binding.cancel()
        runCurrent()
        assertEquals(1, playback.calls.size)
        assertEquals(0, playback.activeStateCollectors)
        vm
    }

    private suspend fun TestScope.restoreSearch(vm: SearchViewModel, check: SearchIntent.AccessChecked) = with(fixture) {
        send(vm, check)
        bind(vm)
        settle()
        reply(AppResult.Success(results(songs)))
        assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
    }

    private suspend fun TestScope.returnToSearchBeforePlaybackReconnects(reconnect: CompletableDeferred<Unit>) = with(fixture) {
        val vm = createVm()
        val binding = bind(vm)
        send(vm, SearchIntent.AccessChecked(true))
        send(vm, SearchIntent.QueryChanged("m4a"))
        settle()
        reply(AppResult.Success(results(songs)))
        send(vm, SearchIntent.PlayTrack(beta.id, songs))
        respond(AppResult.Success(Unit))
        binding.cancel()
        runCurrent()
        assertEquals(0, playback.activeStateCollectors)

        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-grant")
        playback.beforeStateCollection = { reconnect.await() }
        send(vm, SearchIntent.AccessChecked(true))
        bind(vm)
        settle()
        reply(AppResult.Success(results(songs)))
        assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
        vm
    }

    @Test
    fun `late Play denial after access is restored leaves the refreshed results open`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            val oldPlay = playback.calls.single()
            reply(AppResult.Failure(DataError.AccessDenied))
            val deniedBeforeGrant = playDenied()
            assertSame(SearchBody.AccessRequired, vm.snapshot.value.state.body)

            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(songs)))
            assertFalse(oldPlay.waiterCancelled)
            oldPlay.reply.complete(deniedBeforeGrant)
            runCurrent()

            assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
        }
    }

    @Test
    fun `late Play denial after clearing the query and restoring access leaves new results visible`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            val oldPlay = playback.calls.single()
            reply(AppResult.Failure(DataError.AccessDenied))
            assertSame(SearchBody.AccessRequired, vm.snapshot.value.state.body)

            val deniedBeforeGrant = playDenied()
            send(vm, SearchIntent.ClearQuery)
            assertSame(SearchBody.Idle, vm.snapshot.value.state.body)
            send(vm, SearchIntent.AccessChecked(true))
            send(vm, SearchIntent.QueryChanged(alpha.title))
            settle()
            val newResults = results(listOf(alpha))
            reply(AppResult.Success(newResults))
            assertEquals(SearchBody.Ready(newResults), vm.snapshot.value.state.body)

            assertTrue(oldPlay.reply.complete(deniedBeforeGrant))
            runCurrent()
            assertEquals(SearchBody.Ready(newResults), vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
        }
    }

    @Test
    fun `denial from a new Play closes the restored results even after a routine grant`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-new-play")
            runCurrent()
            assertSame(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(songs)))
            send(vm, SearchIntent.PlayTrack(beta.id, songs))

            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(songs)))
            assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
            respond(playDenied())

            assertSame(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertEquals(UiText.Resource(R.string.search_playback_access_denied), vm.snapshot.value.state.playbackError)
        }
    }

    private fun SearchPlaybackFixture.playDenied() =
        AppResult.Failure(PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.AccessDenied()))

    @Test
    fun `delayed state collection compares against dispatch baseline instead of missing a new denial`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            val allowObservation = CompletableDeferred<Unit>()
            playback.beforeStateCollection = { allowObservation.await() }
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-dispatch")
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            assertEquals(1, playback.calls.size)
            assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
            playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-collection", activation = 1)
            runCurrent()
            assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
            allowObservation.complete(Unit)
            runCurrent()

            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(PlaybackProblemId("before-collection"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
            respond(AppResult.Success(Unit))
            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
        }
    }
}
