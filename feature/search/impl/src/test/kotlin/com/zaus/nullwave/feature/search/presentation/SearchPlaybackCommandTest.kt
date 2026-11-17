package com.zaus.nullwave.feature.search.presentation

import com.zaus.nullwave.core.mvi.testing.bind
import com.zaus.nullwave.core.mvi.testing.send
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.search.impl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Queue requests, expected command failures and latest UI-await ownership. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchPlaybackCommandTest {
    private val fixture = SearchPlaybackFixture()

    @Before
    fun setUp() = fixture.setUp()

    @After
    fun tearDown() = fixture.tearDown()

    @Test
    fun `tap preserves the displayed order and starts the selected track at zero`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)

            send(vm, SearchIntent.PlayTrack(beta.id, songs))

            val request = request()
            assertEquals(listOf(zulu.id, beta.id, alpha.id), request.trackIds)
            assertEquals(1, request.startIndex)
            assertEquals(0L, request.startPositionMs)
            assertTrue(request.playRequested)
            assertEquals(2, playback.stateObservations)
            assertEquals(2, playback.activeStateCollectors)
            assertEquals(0, playback.progressObservations)
            assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
            respond(AppResult.Success(Unit))
            assertEquals(2, playback.stateObservations)
            assertEquals(2, playback.activeStateCollectors)
        }
    }

    @Test
    fun `rendered snapshot is used even when the catalog changed before the click is handled`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            val rendered = ready(vm).results.tracks.map { it.track }
            val replacement = listOf(alpha, zulu)
            reply(AppResult.Success(results(replacement)))

            send(vm, SearchIntent.PlayTrack(beta.id, rendered))

            assertEquals(songs.map { it.id }, request().trackIds)
            assertEquals(1, request().startIndex)
            assertEquals(SearchBody.Ready(results(replacement)), vm.snapshot.value.state.body)
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `accepted request is protected from later changes to the caller list`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            val rendered = songs.toMutableList()
            send(vm, SearchIntent.PlayTrack(alpha.id, rendered))

            rendered.reverse()
            rendered.clear()

            assertEquals(songs.map { it.id }, request().trackIds)
            assertEquals(2, request().startIndex)
            respond(AppResult.Success(Unit))
        }
    }

    @Test
    fun `expected command failures expose a dismissible message without replacing the catalog`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            val failures = listOf(
                PlaybackCommandError.ConnectionUnavailable to R.string.search_playback_connection_failed,
                PlaybackCommandError.StartNotAllowed to R.string.search_playback_start_not_allowed,
                PlaybackCommandError.CommandUnavailable to R.string.search_playback_unavailable,
                PlaybackCommandError.InvalidRequest to R.string.search_playback_invalid_request,
                PlaybackCommandError.StaleTarget to R.string.search_playback_unavailable,
                PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.NotFound) to R.string.search_playback_not_found,
                PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.Io) to R.string.search_playback_read_failed,
            )
            val catalog = vm.snapshot.value.state.body
            for ((failure, message) in failures) {
                send(vm, SearchIntent.PlayTrack(beta.id, songs))
                respond(AppResult.Failure(failure, IllegalStateException("Technical diagnostic: $failure")))

                assertEquals("Expected a localized message for $failure", UiText.Resource(message), vm.snapshot.value.state.playbackError)
                assertSame(catalog, vm.snapshot.value.state.body)
                assertEquals(1, repository.subscriptions.size)

                send(vm, SearchIntent.DismissPlaybackError)
                assertNull(vm.snapshot.value.state.playbackError)
            }
        }
    }

    @Test
    fun `a new accepted click clears the previous error and superseded is silent`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
            assertNotNull(vm.snapshot.value.state.playbackError)

            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            assertNull(vm.snapshot.value.state.playbackError)
            respond(AppResult.Failure(PlaybackCommandError.Superseded))

            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(2, playback.calls.size)
        }
    }

    @Test
    fun `latest click cancels only the older waiter and ignores its late failure`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(zulu.id, songs))
            val older = playback.calls.single()

            send(vm, SearchIntent.PlayTrack(alpha.id, songs))

            assertEquals(2, playback.calls.size)
            assertTrue(older.waiterCancelled)
            assertFalse("UI cancellation cannot cancel accepted service work", older.reply.isCancelled)
            assertEquals(2, request().startIndex)
            respond(AppResult.Success(Unit))
            older.reply.complete(AppResult.Failure(PlaybackCommandError.SourceFailure(zulu.id, PlaybackSourceError.NotFound)))
            runCurrent()

            assertNull(vm.snapshot.value.state.playbackError)
            assertTrue(playback.calls.all { it.command is PlaybackCommand.ReplaceQueue })
        }
    }

    @Test
    fun `late success of a replaced click cannot clear the newer failure`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(zulu.id, songs))
            val older = playback.calls.single()
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Failure(PlaybackCommandError.StartNotAllowed))
            val message = vm.snapshot.value.state.playbackError
            assertNotNull(message)

            older.reply.complete(AppResult.Success(Unit))
            runCurrent()

            assertSame(message, vm.snapshot.value.state.playbackError)
        }
    }

    @Test
    fun `permission revocation cancels the local waiter without issuing stop or accepting its late result`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            val accepted = playback.calls.single()

            send(vm, SearchIntent.AccessChecked(false))

            assertTrue(accepted.waiterCancelled)
            assertFalse(accepted.reply.isCancelled)
            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            accepted.reply.complete(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
            runCurrent()
            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(1, playback.calls.size)
            assertTrue(playback.calls.single().command is PlaybackCommand.ReplaceQueue)
        }
    }

    @Test
    fun `clearing the ViewModel releases the waiter but does not stop accepted service work`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            val accepted = playback.calls.single()
            val beforeClear = vm.snapshot.value.state

            store.clear()
            runCurrent()

            assertTrue(accepted.waiterCancelled)
            assertFalse(accepted.reply.isCancelled)
            accepted.reply.complete(AppResult.Failure(PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.AccessDenied())))
            runCurrent()
            assertEquals(beforeClear, vm.snapshot.value.state)
            assertTrue(playback.calls.single().command is PlaybackCommand.ReplaceQueue)
        }
    }

    @Test
    fun `controller cancellation does not become a playback error`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))

            playback.calls.single().reply.completeExceptionally(CancellationException("Connection waiter cancelled"))
            runCurrent()

            assertNull(vm.snapshot.value.state.playbackError)
            assertEquals(SearchBody.Ready(results(songs)), vm.snapshot.value.state.body)
        }
    }

    @Test
    fun `unexpected controller failures propagate instead of becoming user facing errors`() = with(fixture) {
        val original = DiagnosticFailure("Broken playback adapter", "controller")
        val thrown = assertThrows(DiagnosticFailure::class.java) {
            runTest(dispatcher) {
                val vm = readyVm(songs)
                send(vm, SearchIntent.PlayTrack(beta.id, songs))
                playback.calls.single().reply.completeExceptionally(original)
                runCurrent()
                assertNull(vm.snapshot.value.state.playbackError)
            }
        }

        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === original })
    }

    @Test
    fun `only ready nonempty displayed tracks can dispatch and query loading blocks stale clicks`() = with(fixture) {
        runTest(dispatcher) {
            val vm = createVm()
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            bind(vm)
            send(vm, SearchIntent.AccessChecked(true))
            send(vm, SearchIntent.QueryChanged("m4a"))
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            settle()
            reply(AppResult.Failure(DataError.Io))
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            send(vm, SearchIntent.Retry)
            settle()
            reply(AppResult.Success(results(songs)))
            send(vm, SearchIntent.PlayTrack(beta.id, emptyList()))
            send(vm, SearchIntent.PlayTrack(TrackId("missing"), songs))
            send(vm, SearchIntent.QueryChanged("new query"))
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            send(vm, SearchIntent.AccessChecked(false))
            send(vm, SearchIntent.PlayTrack(beta.id, songs))

            assertTrue(playback.calls.isEmpty())
            assertNull(vm.snapshot.value.state.playbackError)
        }
    }

    @Test
    fun `playback source denial fences late search emissions until a fresh access check`() = with(fixture) {
        runTest(dispatcher) {
            val vm = readyVm(songs)
            send(vm, SearchIntent.PlayTrack(beta.id, songs))
            respond(AppResult.Failure(PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.AccessDenied())))

            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            assertEquals(UiText.Resource(R.string.search_playback_access_denied), vm.snapshot.value.state.playbackError)
            reply(AppResult.Success(results(listOf(alpha))))
            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            reply(AppResult.Failure(DataError.Io))
            assertEquals(SearchBody.AccessRequired, vm.snapshot.value.state.body)
            send(vm, SearchIntent.PlayTrack(alpha.id, songs))
            assertEquals(1, playback.calls.size)

            send(vm, SearchIntent.AccessChecked(true))
            settle()
            reply(AppResult.Success(results(listOf(alpha))))
            assertEquals(SearchBody.Ready(results(listOf(alpha))), vm.snapshot.value.state.body)
            assertNotNull("A fresh access check must not erase a playback notice", vm.snapshot.value.state.playbackError)
        }
    }

    private class DiagnosticFailure(message: String, val diagnostic: String) : IllegalArgumentException(message)

}
