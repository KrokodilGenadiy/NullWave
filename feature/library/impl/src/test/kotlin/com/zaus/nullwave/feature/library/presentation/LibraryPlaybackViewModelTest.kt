package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.core.mvi.testing.MviTestMain
import com.zaus.nullwave.core.mvi.testing.bind
import com.zaus.nullwave.core.mvi.testing.send
import androidx.lifecycle.SavedStateHandle
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.observeCurrentTrackId
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblem
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.feature.player.api.launch.PlaybackProblemKey
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.core.result.EmptyResult
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort
import com.zaus.nullwave.feature.library.impl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryPlaybackViewModelTest {
    private val mvi = MviTestMain()
    private val dispatcher = mvi.dispatcher
    private val repository = FakeLibraryRepository()
    private val playback = ControlledLibraryPlaybackController()
    private val store = mvi.store

    @Before
    fun setUp() = mvi.setUp()

    @After
    fun tearDown() {
        repository.releaseCleanup()
        mvi.tearDown()
    }

    @Test
    fun `tap preserves the displayed order and starts the selected song at zero`() = runTest(dispatcher) {
        val vm = readyVm(songs)

        send(vm, LibraryIntent.PlaySong(beta.id, songs))

        val request = request()
        assertEquals(listOf(zulu.id, beta.id, alpha.id), request.trackIds)
        assertEquals(1, request.startIndex)
        assertEquals(0L, request.startPositionMs)
        assertTrue(request.playRequested)
        assertEquals(NOW_PLAYING + 1, playback.stateObservations)
        assertEquals(NOW_PLAYING + 1, playback.activeStateCollectors)
        assertEquals(0, playback.progressObservations)
        assertEquals(LibraryBody.Ready(songs), vm.snapshot.value.state.body)
        respond(AppResult.Success(Unit))
        assertEquals(NOW_PLAYING + 1, playback.stateObservations)
        assertEquals(NOW_PLAYING + 1, playback.activeStateCollectors)
    }

    @Test
    fun `rendered snapshot is used even when the catalog changed before the click is handled`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        val rendered = ready(vm).items
        val replacement = listOf(alpha, zulu)
        reply(AppResult.Success(replacement))

        send(vm, LibraryIntent.PlaySong(beta.id, rendered))

        assertEquals(songs.map { it.id }, request().trackIds)
        assertEquals(1, request().startIndex)
        assertEquals(LibraryBody.Ready(replacement), vm.snapshot.value.state.body)
        respond(AppResult.Success(Unit))
    }

    @Test
    fun `accepted request is protected from later changes to the caller list`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        val rendered = songs.toMutableList()
        send(vm, LibraryIntent.PlaySong(alpha.id, rendered))

        rendered.reverse()
        rendered.clear()

        assertEquals(songs.map { it.id }, request().trackIds)
        assertEquals(2, request().startIndex)
        respond(AppResult.Success(Unit))
    }

    @Test
    fun `loading missing empty inactive and denied inputs never dispatch playback`() = runTest(dispatcher) {
        val vm = createVm()
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        bind(vm)
        reply(AppResult.Success(songs))
        send(vm, LibraryIntent.PlaySong(beta.id, emptyList()))
        send(vm, LibraryIntent.PlaySong(TrackId("missing"), songs))
        send(vm, LibraryIntent.TabSelected(LibraryTab.Albums))
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Required(AccessAction.CheckAgain)))
        send(vm, LibraryIntent.TabSelected(LibraryTab.Songs))
        send(vm, LibraryIntent.PlaySong(beta.id, songs))

        val failed = createVm()
        bind(failed)
        reply(AppResult.Failure(DataError.Io))
        assertTrue(failed.snapshot.value.state.body is LibraryBody.Failed)
        send(failed, LibraryIntent.PlaySong(beta.id, songs))

        assertTrue(playback.calls.isEmpty())
        assertNull(vm.snapshot.value.state.playbackError)
        assertNull(failed.snapshot.value.state.playbackError)
    }

    @Test
    fun `refresh keeps displayed songs playable and a playback failure does not change its status`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.Refresh)
        val catalog = vm.snapshot.value.state.body
        assertEquals(RefreshState.Running, ready(vm).refresh)

        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Failure(PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.Io)))

        assertEquals(songs.map { it.id }, request().trackIds)
        assertSame(catalog, vm.snapshot.value.state.body)
        assertNotNull(vm.snapshot.value.state.playbackError)
        assertEquals(2, repository.tracks.subscriptions.size)
    }

    @Test
    fun `expected command failures expose a dismissible message without replacing the catalog`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        val failures = listOf(
            PlaybackCommandError.ConnectionUnavailable to R.string.library_playback_connection_failed,
            PlaybackCommandError.StartNotAllowed to R.string.library_playback_start_not_allowed,
            PlaybackCommandError.CommandUnavailable to R.string.library_playback_unavailable,
            PlaybackCommandError.InvalidRequest to R.string.library_playback_invalid_request,
            PlaybackCommandError.StaleTarget to R.string.library_playback_unavailable,
            PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.NotFound) to R.string.library_playback_not_found,
            PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.Io) to R.string.library_playback_read_failed,
        )
        val catalog = vm.snapshot.value.state.body
        for ((failure, message) in failures) {
            send(vm, LibraryIntent.PlaySong(beta.id, songs))
            respond(AppResult.Failure(failure, IllegalStateException("Technical diagnostic: $failure")))

            assertEquals("Expected a localized message for $failure", UiText.Resource(message), vm.snapshot.value.state.playbackError)
            assertSame(catalog, vm.snapshot.value.state.body)
            assertEquals(1, repository.tracks.subscriptions.size)

            send(vm, LibraryIntent.DismissPlaybackError)
            assertNull(vm.snapshot.value.state.playbackError)
        }
    }

    @Test
    fun `a new accepted click clears the previous error and superseded is silent`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
        assertNotNull(vm.snapshot.value.state.playbackError)

        send(vm, LibraryIntent.PlaySong(alpha.id, songs))
        assertNull(vm.snapshot.value.state.playbackError)
        respond(AppResult.Failure(PlaybackCommandError.Superseded))

        assertNull(vm.snapshot.value.state.playbackError)
        assertEquals(2, playback.calls.size)
    }

    @Test
    fun `source access rejection gates every tab until a fresh access result permits reading`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Failure(PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.AccessDenied)))

        val gate = LibraryBody.AccessRequired(AccessAction.CheckAgain)
        assertEquals(gate, vm.snapshot.value.state.body)
        assertEquals(gate, vm.snapshot.value.state.albums)
        assertEquals(gate, vm.snapshot.value.state.artists)
        assertEquals(UiText.Resource(R.string.library_playback_access_denied), vm.snapshot.value.state.playbackError)
        reply(AppResult.Success(listOf(alpha)))
        assertEquals("Protected source success cannot bypass the access gate", gate, vm.snapshot.value.state.body)
        reply(AppResult.Failure(DataError.Io))
        assertEquals("Catalog failures cannot replace the access gate either", gate, vm.snapshot.value.state.body)

        send(vm, LibraryIntent.PlaySong(alpha.id, songs))
        assertEquals(1, playback.calls.size)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        reply(AppResult.Success(listOf(alpha)))
        assertEquals(LibraryBody.Ready(listOf(alpha)), vm.snapshot.value.state.body)
    }

    @Test
    fun `latest click cancels only the older waiter and ignores its late failure`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(zulu.id, songs))
        val older = playback.calls.single()

        send(vm, LibraryIntent.PlaySong(alpha.id, songs))

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

    @Test
    fun `late success of a replaced click cannot clear the newer failure`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(zulu.id, songs))
        val older = playback.calls.single()
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Failure(PlaybackCommandError.StartNotAllowed))
        val message = vm.snapshot.value.state.playbackError
        assertNotNull(message)

        older.reply.complete(AppResult.Success(Unit))
        runCurrent()

        assertSame(message, vm.snapshot.value.state.playbackError)
    }

    @Test
    fun `permission revocation cancels the local waiter without issuing stop or accepting its late result`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        val accepted = playback.calls.single()

        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Required(AccessAction.SettingsSuggested)))

        assertTrue(accepted.waiterCancelled)
        assertFalse(accepted.reply.isCancelled)
        assertEquals(LibraryBody.AccessRequired(AccessAction.SettingsSuggested), vm.snapshot.value.state.body)
        accepted.reply.complete(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
        runCurrent()
        assertNull(vm.snapshot.value.state.playbackError)
        assertEquals(1, playback.calls.size)
        assertTrue(playback.calls.single().command is PlaybackCommand.ReplaceQueue)
    }

    @Test
    fun `clearing the ViewModel releases the waiter but does not stop accepted service work`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        val accepted = playback.calls.single()
        val beforeClear = vm.snapshot.value.state

        store.clear()
        runCurrent()

        assertTrue(accepted.waiterCancelled)
        assertFalse(accepted.reply.isCancelled)
        accepted.reply.complete(AppResult.Failure(PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.AccessDenied)))
        runCurrent()
        assertEquals(beforeClear, vm.snapshot.value.state)
        assertTrue(playback.calls.single().command is PlaybackCommand.ReplaceQueue)
    }

    @Test
    fun `restoring Library inputs does not restore errors or replay playback`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = readyVm(songs, handle)
        send(first, LibraryIntent.SortChanged(LibraryTrackSort.Artist, SortDirection.Descending))
        reply(AppResult.Success(songs))
        send(first, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Success(Unit))
        assertTrue(first.snapshot.value.state.playbackLaunch.monitoring)
        send(first, LibraryIntent.PlaySong(alpha.id, songs))
        respond(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
        assertNotNull(first.snapshot.value.state.playbackError)
        val saved = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        store.clear()
        runCurrent()

        val restored = createVm(saved)
        bind(restored)
        reply(AppResult.Success(songs))

        assertEquals(LibraryTrackSort.Artist, restored.snapshot.value.state.query.sort)
        assertEquals(SortDirection.Descending, restored.snapshot.value.state.query.direction)
        assertNull(restored.snapshot.value.state.playbackError)
        assertFalse(restored.snapshot.value.state.playbackLaunch.monitoring)
        assertNull(restored.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
        assertEquals(2, playback.calls.size)
    }

    @Test
    fun `controller cancellation does not become a playback error`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))

        playback.calls.single().reply.completeExceptionally(CancellationException("Connection waiter cancelled"))
        runCurrent()

        assertNull(vm.snapshot.value.state.playbackError)
        assertEquals(LibraryBody.Ready(songs), vm.snapshot.value.state.body)
    }

    @Test
    fun `before the first tap Library only observes the current track for its highlight`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-first-tap")
        runCurrent()

        assertFalse(vm.snapshot.value.state.playbackLaunch.monitoring)
        assertNull(vm.snapshot.value.state.playbackError)
        assertNull(vm.snapshot.value.state.playbackLaunch.handledDenial)
        assertEquals(LibraryBody.Ready(songs), vm.snapshot.value.state.body)
        assertEquals(beta.id, vm.snapshot.value.state.playingTrackId)
        assertEquals(NOW_PLAYING, playback.stateObservations)
        assertEquals(NOW_PLAYING, playback.activeStateCollectors)
        assertEquals(0, playback.progressObservations)
        assertTrue(playback.calls.isEmpty())
    }

    @Test
    fun `highlight follows the current entry, survives reconnection and clears with an empty queue`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        assertNull("The initial empty queue has no current track", vm.snapshot.value.state.playingTrackId)

        playback.states.value = healthySnapshot(entryId = "current-alpha", track = alpha)
        runCurrent()
        assertEquals(alpha.id, vm.snapshot.value.state.playingTrackId)

        for (connection in listOf(
            PlaybackConnection.Connecting,
            PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected),
            PlaybackConnection.Disconnected,
        )) {
            playback.states.value = connection
            runCurrent()
            assertEquals(alpha.id, vm.snapshot.value.state.playingTrackId)
        }

        playback.states.value = PlaybackConnection.Connected(PlaybackSnapshot.create(PlaybackSessionId("library-test-session")))
        runCurrent()
        assertNull(vm.snapshot.value.state.playingTrackId)
        assertTrue(playback.calls.isEmpty())
    }

    @Test
    fun `transport updates and another occurrence of the same track keep one highlight emission`() = runTest(dispatcher) {
        val first = healthySnapshot()
        playback.states.value = first
        val highlights = mutableListOf<TrackId?>()
        backgroundScope.launch { playback.observeCurrentTrackId().toList(highlights) }
        runCurrent()

        playback.states.value = PlaybackConnection.Connected(PlaybackSnapshot.create(
            sessionId = first.snapshot.sessionId, queue = first.snapshot.queue,
            revision = 1, transport = PlaybackTransport(playRequested = true),
        ))
        runCurrent()
        playback.states.value = healthySnapshot(entryId = "another-beta")
        runCurrent()
        playback.states.value = healthySnapshot(track = alpha, sessionId = "new-session")
        runCurrent()
        assertEquals(listOf(beta.id, alpha.id), highlights)
    }

    @Test
    fun `restoring Library never restores the playing highlight`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = readyVm(songs, handle)
        playback.states.value = healthySnapshot()
        runCurrent()
        assertEquals(beta.id, first.snapshot.value.state.playingTrackId)

        val restored = createVm(handle)
        assertNull(restored.snapshot.value.state.playingTrackId)
    }

    @Test
    fun `first attempt gates an engine denial before acknowledgement without a local banner`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-first-ack")
        runCurrent()
        assertTrue(vm.snapshot.value.state.playbackLaunch.monitoring)
        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertNull(vm.snapshot.value.state.playbackError)
        assertEquals(NOW_PLAYING + 1, playback.stateObservations)

        respond(AppResult.Success(Unit))

        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertNull(vm.snapshot.value.state.playbackError)
        assertEquals(NOW_PLAYING + 1, playback.stateObservations)
        assertEquals(PlaybackProblemId("before-first-ack"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
    }

    @Test
    fun `cancelled acknowledgement and failed replacement still gate later denial while preserving the command notice`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        val installed = playback.calls.single()
        playback.states.value = healthySnapshot()
        runCurrent()
        send(vm, LibraryIntent.PlaySong(alpha.id, songs))
        assertTrue(installed.waiterCancelled)
        respond(AppResult.Failure(PlaybackCommandError.SourceFailure(alpha.id, PlaybackSourceError.NotFound)))
        val notice = vm.snapshot.value.state.playbackLaunch.notice
        installed.reply.complete(AppResult.Success(Unit))
        runCurrent()
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "installed-with-cancelled-ack")
        runCurrent()

        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertSame(notice, vm.snapshot.value.state.playbackLaunch.notice)
        assertEquals(UiText.Resource(R.string.library_playback_not_found), vm.snapshot.value.state.playbackError)
        assertEquals(PlaybackProblemId("installed-with-cancelled-ack"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
        assertEquals(NOW_PLAYING + 1, playback.activeStateCollectors)
        assertEquals(0, playback.progressObservations)
    }

    @Test
    fun `engine snapshots during a replacement never create a local command notice`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Success(Unit))
        send(vm, LibraryIntent.PlaySong(alpha.id, songs))
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

    @Test
    fun `healthy snapshots never erase a command refusal for the replacement`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(alpha.id, songs))
        respond(AppResult.Failure(PlaybackCommandError.SourceFailure(alpha.id, PlaybackSourceError.Io)))
        val notice = vm.snapshot.value.state.playbackLaunch.notice
        assertEquals(UiText.Resource(R.string.library_playback_read_failed), notice)

        playback.states.value = healthySnapshot(entryId = "still-playing-beta", activation = 3)
        runCurrent()

        assertSame(notice, vm.snapshot.value.state.playbackLaunch.notice)
        assertEquals(UiText.Resource(R.string.library_playback_read_failed), vm.snapshot.value.state.playbackError)
    }

    @Test
    fun `new activation entry session and empty queue preserve a local command notice`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
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

    @Test
    fun `untargeted engine access denial gates the catalog without a banner`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Success(Unit))
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "untargeted", includeTarget = false)
        runCurrent()

        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertNull(vm.snapshot.value.state.playbackLaunch.notice)
        assertEquals(PlaybackProblemId("untargeted"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
    }

    @Test
    fun `preexisting access problem cannot close the catalog when the first attempt starts`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        val catalog = vm.snapshot.value.state.body
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "preexisting-denial")
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
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

        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertNull(vm.snapshot.value.state.playbackError)
    }

    @Test
    fun `delayed UI observation gates a new pre-ack denial instead of treating it as the baseline`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        val allowUiObservation = CompletableDeferred<Unit>()
        playback.beforeStateCollection = { observation -> if (observation == NOW_PLAYING + 1) allowUiObservation.await() }
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "old-before-dispatch")
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        assertEquals(1, playback.calls.size)
        assertEquals(LibraryBody.Ready(songs), vm.snapshot.value.state.body)
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "new-before-observer", activation = 1)
        runCurrent()
        assertEquals(LibraryBody.Ready(songs), vm.snapshot.value.state.body)
        allowUiObservation.complete(Unit)
        runCurrent()

        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertNull(vm.snapshot.value.state.playbackLaunch.notice)
        assertEquals(PlaybackProblemId("new-before-observer"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
        respond(AppResult.Success(Unit))
        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
    }

    @Test
    fun `replacement during baseline acquisition cancels only the first wait and dispatches the latest song`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        val allowConnection = CompletableDeferred<Unit>()
        playback.beforeCommandDispatch = { allowConnection.await() }
        playback.states.value = PlaybackConnection.Connecting
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        assertTrue(playback.calls.isEmpty())
        assertEquals(NOW_PLAYING, playback.activeStateCollectors)

        send(vm, LibraryIntent.PlaySong(alpha.id, songs))
        assertTrue(playback.calls.isEmpty())
        assertEquals(NOW_PLAYING, playback.activeStateCollectors)
        playback.states.value = healthySnapshot()
        allowConnection.complete(Unit)
        runCurrent()

        assertEquals(1, playback.calls.size)
        assertEquals(2, request().startIndex)
        respond(AppResult.Success(Unit))
        assertEquals(NOW_PLAYING + 1, playback.activeStateCollectors)
        assertNull(vm.snapshot.value.state.playbackError)
    }

    @Test
    fun `first command can finish after UI observation expires and rebind catches its access denial`() = runTest(dispatcher) {
        val vm = createVm()
        val binding = bind(vm)
        reply(AppResult.Success(songs))
        val allowConnection = CompletableDeferred<Unit>()
        playback.beforeCommandDispatch = { allowConnection.await() }
        playback.states.value = PlaybackConnection.Connecting
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        binding.cancel()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(playback.calls.isEmpty())
        assertEquals(0, playback.activeStateCollectors)
        playback.states.value = healthySnapshot()
        allowConnection.complete(Unit)
        runCurrent()
        assertEquals(1, playback.calls.size)
        respond(AppResult.Success(Unit))
        assertEquals(0, playback.activeStateCollectors)
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "while-ui-stopped")
        bind(vm)

        assertEquals(NOW_PLAYING + 1, playback.activeStateCollectors)
        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertNull(vm.snapshot.value.state.playbackLaunch.notice)
        assertEquals(1, playback.calls.size)
    }

    @Test
    fun `connection refusal without a baseline preserves command errors and a later tap can dispatch`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        for ((failure, message) in listOf(
            PlaybackCommandError.ConnectionUnavailable to R.string.library_playback_connection_failed,
            PlaybackCommandError.StartNotAllowed to R.string.library_playback_start_not_allowed,
        )) {
            playback.connectionFailure = AppResult.Failure(failure, IllegalStateException("Original connection diagnostic"))
            send(vm, LibraryIntent.PlaySong(beta.id, songs))

            assertTrue(playback.calls.isEmpty())
            assertEquals(UiText.Resource(message), vm.snapshot.value.state.playbackError)
            assertEquals(NOW_PLAYING, playback.activeStateCollectors)
        }

        // A failed connection is not a baseline for a previously unseen service problem.
        playback.connectionFailure = null
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "old-after-reconnect")
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        assertEquals(1, playback.calls.size)
        assertNull(vm.snapshot.value.state.playbackError)
        assertEquals(LibraryBody.Ready(songs), vm.snapshot.value.state.body)
        respond(AppResult.Success(Unit))

        assertEquals(NOW_PLAYING + 1, playback.activeStateCollectors)
    }

    @Test
    fun `cancellation before the dispatch callback does not poison a later attempt`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        playback.beforeCommandDispatch = { throw CancellationException("Connection waiter cancelled") }

        send(vm, LibraryIntent.PlaySong(beta.id, songs))

        assertTrue(playback.calls.isEmpty())
        assertNull(vm.snapshot.value.state.playbackError)
        assertEquals(NOW_PLAYING, playback.activeStateCollectors)
        playback.beforeCommandDispatch = {}
        send(vm, LibraryIntent.PlaySong(alpha.id, songs))
        assertEquals(1, playback.calls.size)
        assertEquals(2, request().startIndex)
        respond(AppResult.Success(Unit))
        assertEquals(NOW_PLAYING + 1, playback.activeStateCollectors)
    }

    @Test
    fun `denial identity is scoped to the service session for baseline and deduplication`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "reused-id")
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Success(Unit))
        assertEquals(LibraryBody.Ready(songs), vm.snapshot.value.state.body)
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "reused-id", sessionId = "second-service")
        runCurrent()
        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertEquals(PlaybackProblemKey(PlaybackSessionId("second-service"), PlaybackProblemId("reused-id")),
            vm.snapshot.value.state.playbackLaunch.handledDenial)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        reply(AppResult.Success(songs))
        val checkedCatalog = vm.snapshot.value.state.body
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "reused-id", revision = 1, sessionId = "second-service")
        runCurrent()
        assertSame(checkedCatalog, vm.snapshot.value.state.body)
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "reused-id", sessionId = "third-service")
        runCurrent()

        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertNull(vm.snapshot.value.state.playbackError)
        assertEquals(PlaybackSessionId("third-service"), vm.snapshot.value.state.playbackLaunch.handledDenial?.sessionId)
    }

    @Test
    fun `later successful reply does not reopen a catalog closed by engine denial while awaiting it`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Success(Unit))
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-next-ack", activation = 1)
        runCurrent()
        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        respond(AppResult.Success(Unit))

        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertNull(vm.snapshot.value.state.playbackLaunch.notice)
        assertEquals(PlaybackProblemId("before-next-ack"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
        assertEquals(2, playback.calls.size)
        assertEquals(NOW_PLAYING + 1, playback.stateObservations)
    }

    @Test
    fun `non-access engine problems neither create nor erase local command notices`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
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
        send(vm, LibraryIntent.PlaySong(alpha.id, songs))
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
        assertEquals(NOW_PLAYING + 1, playback.stateObservations)
        assertEquals(0, playback.progressObservations)
    }

    @Test
    fun `fresh access survives repeated denial through healthy and other engine snapshots until a new denial`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Success(Unit))
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "first")
        runCurrent()
        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        reply(AppResult.Success(songs))
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

        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertNull(vm.snapshot.value.state.playbackError)
        assertEquals(PlaybackProblemId("second"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
    }

    @Test
    fun `late engine access rejection closes every protected catalog and blocks further taps without a local banner`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Success(Unit))
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "engine-denied")
        runCurrent()

        val gate = LibraryBody.AccessRequired(AccessAction.CheckAgain)
        assertEquals(gate, vm.snapshot.value.state.body)
        assertEquals(gate, vm.snapshot.value.state.albums)
        assertEquals(gate, vm.snapshot.value.state.artists)
        assertNull(vm.snapshot.value.state.playbackError)
        reply(AppResult.Success(listOf(alpha)))
        assertEquals(gate, vm.snapshot.value.state.body)
        send(vm, LibraryIntent.PlaySong(alpha.id, songs))
        assertEquals(1, playback.calls.size)
    }

    @Test
    fun `background lease expires and rebind preserves fresh access despite the same engine denial`() = runTest(dispatcher) {
        val vm = createVm()
        val binding = bind(vm)
        reply(AppResult.Success(songs))
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Success(Unit))
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-background")
        runCurrent()
        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        reply(AppResult.Success(songs))
        assertEquals(LibraryBody.Ready(songs), vm.snapshot.value.state.body)
        assertEquals(NOW_PLAYING + 1, playback.activeStateCollectors)
        binding.cancel()
        runCurrent()
        assertEquals("The existing UI source grace period still applies", NOW_PLAYING + 1, playback.activeStateCollectors)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, playback.activeStateCollectors)
        bind(vm)
        reply(AppResult.Success(songs))

        assertEquals(LibraryBody.Ready(songs), vm.snapshot.value.state.body)
        assertEquals(NOW_PLAYING + 1, playback.activeStateCollectors)
        assertEquals(2 * (NOW_PLAYING + 1), playback.stateObservations)
        assertEquals(1, playback.calls.size)
        assertNull(vm.snapshot.value.state.playbackError)
        assertEquals(PlaybackProblemId("before-background"), vm.snapshot.value.state.playbackLaunch.handledDenial?.problemId)
        assertEquals(0, playback.progressObservations)
    }

    @Test
    fun `fresh granted access on resume does not erase a command refusal`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Failure(PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.Io)))
        val notice = vm.snapshot.value.state.playbackLaunch.notice
        assertNotNull(notice)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        reply(AppResult.Success(songs))

        assertSame(notice, vm.snapshot.value.state.playbackLaunch.notice)
        assertNull(vm.snapshot.value.state.playbackLaunch.handledDenial)
        assertEquals(1, playback.calls.size)
    }

    @Test
    fun `connectivity transitions alone do not invent a playback problem`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
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

    @Test
    fun `unexpected controller failures propagate instead of becoming user facing errors`() {
        val original = DiagnosticFailure("Broken playback adapter", "controller")
        val thrown = assertThrows(DiagnosticFailure::class.java) {
            runTest(dispatcher) {
                val vm = readyVm(songs)
                send(vm, LibraryIntent.PlaySong(beta.id, songs))
                playback.calls.single().reply.completeExceptionally(original)
                runCurrent()
                assertNull(vm.snapshot.value.state.playbackError)
            }
        }

        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === original })
    }

    @Test
    fun `Back to Albums keeps the list when playback replays a denial published before the grant`() = runTest(dispatcher) {
        val reconnect = CompletableDeferred<Unit>()
        val vm = returnToAlbumsBeforePlaybackReconnects(reconnect)
        val displayed = vm.snapshot.value.state.albums

        reconnect.complete(Unit)
        runCurrent()

        assertSame(displayed, vm.snapshot.value.state.albums)
    }

    @Test
    fun `Back to Albums hides the list when the first reconnected denial was published after the grant`() = runTest(dispatcher) {
        val reconnect = CompletableDeferred<Unit>()
        val vm = returnToAlbumsBeforePlaybackReconnects(reconnect)
        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "after-grant")

        reconnect.complete(Unit)
        runCurrent()

        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.albums)
    }

    @Test
    fun `late Play denial after access is restored leaves the refreshed catalog open`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        val oldPlay = playback.calls.single()
        reply(AppResult.Failure(DataError.AccessDenied))
        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)

        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        reply(AppResult.Success(songs))
        assertTrue(oldPlay.waiterCancelled)
        oldPlay.reply.complete(playDenied)
        runCurrent()

        assertEquals(LibraryBody.Ready(songs), vm.snapshot.value.state.body)
        assertNull(vm.snapshot.value.state.playbackError)
    }

    @Test
    fun `denial from a new Play closes the restored catalog even after a routine grant`() = runTest(dispatcher) {
        val vm = readyVm(songs)
        reply(AppResult.Failure(DataError.AccessDenied))
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        reply(AppResult.Success(songs))
        send(vm, LibraryIntent.PlaySong(beta.id, songs))

        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        reply(AppResult.Success(songs))
        assertEquals(LibraryBody.Ready(songs), vm.snapshot.value.state.body)
        respond(playDenied)

        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.snapshot.value.state.body)
        assertEquals(UiText.Resource(R.string.library_playback_access_denied), vm.snapshot.value.state.playbackError)
    }

    private val playDenied get() = AppResult.Failure(PlaybackCommandError.SourceFailure(beta.id, PlaybackSourceError.AccessDenied))

    private suspend fun TestScope.returnToAlbumsBeforePlaybackReconnects(reconnect: CompletableDeferred<Unit>): LibraryViewModel {
        val vm = createVm()
        val binding = bind(vm)
        reply(AppResult.Success(songs))
        send(vm, LibraryIntent.PlaySong(beta.id, songs))
        respond(AppResult.Success(Unit))
        val albums = listOf(LibraryAlbum(AlbumId("primary:8"), "Signals", AlbumArtistCredit.Unknown, 2))
        repository.albums.initialResult = AppResult.Success(albums)
        send(vm, LibraryIntent.TabSelected(LibraryTab.Albums))
        binding.cancel()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, playback.activeStateCollectors)

        playback.states.value = problemSnapshot(PlaybackProblemCode.AccessDenied, "before-grant")
        playback.beforeStateCollection = { reconnect.await() }
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        bind(vm)
        assertEquals(LibraryBody.Ready(albums), vm.snapshot.value.state.albums)
        return vm
    }

    private fun createVm(handle: SavedStateHandle = SavedStateHandle()): LibraryViewModel =
        LibraryViewModel(repository, handle, "en", playback).also { mvi.own(it) }

    private suspend fun TestScope.readyVm(items: List<MediaTrack>, handle: SavedStateHandle = SavedStateHandle()): LibraryViewModel =
        createVm(handle).also {
            bind(it)
            reply(AppResult.Success(items))
        }

    private fun TestScope.reply(result: LibraryResult) {
        repository.tracks.subscriptions.last().respond(result)
        runCurrent()
    }

    private fun TestScope.respond(result: EmptyResult<PlaybackCommandError>) {
        assertTrue(playback.calls.last().reply.complete(result))
        runCurrent()
    }

    private fun request() = (playback.calls.last().command as PlaybackCommand.ReplaceQueue).request
    private fun ready(vm: LibraryViewModel) = vm.snapshot.value.state.body as LibraryBody.Ready<MediaTrack>

    private fun problemSnapshot(
        code: PlaybackProblemCode,
        id: String,
        revision: Long = 0,
        activation: Long = 0,
        includeTarget: Boolean = true,
        sessionId: String = "library-test-session",
    ): PlaybackConnection.Connected = snapshot(
        code, id, revision = revision, activation = activation, includeTarget = includeTarget, sessionId = sessionId,
    )

    private fun healthySnapshot(
        activation: Long = 0,
        entryId: String = "playing-beta",
        sessionId: String = "library-test-session",
        track: MediaTrack = beta,
    ): PlaybackConnection.Connected = snapshot(
        activation = activation, entryId = entryId, sessionId = sessionId, track = track,
    )

    private fun snapshot(
        code: PlaybackProblemCode? = null,
        id: String = "unused-without-problem",
        revision: Long = 0,
        activation: Long = 0,
        entryId: String = "playing-beta",
        sessionId: String = "library-test-session",
        track: MediaTrack = beta,
        includeTarget: Boolean = true,
    ): PlaybackConnection.Connected {
        val session = PlaybackSessionId(sessionId)
        val entry = QueueEntry(QueueEntryId(entryId), track)
        val token = PlaybackItemToken(session, entry.entryId, activation)
        return PlaybackConnection.Connected(PlaybackSnapshot.create(
            sessionId = session,
            revision = revision,
            queue = PlaybackQueue.create(entries = listOf(entry), current = token),
            transport = PlaybackTransport(problem = code?.let { PlaybackProblem(PlaybackProblemId(id), it, token.takeIf { includeTarget }) }),
        ))
    }

    private class DiagnosticFailure(message: String, val diagnostic: String) : IllegalArgumentException(message)

    private companion object {
        /** The bound ViewModel's control-only current-track observer, independent of access monitoring. */
        const val NOW_PLAYING = 1
    }

    private val zulu = MediaTrack(TrackId("primary:31"), "Zulu", "Third", null, 210_000)
    private val beta = MediaTrack(TrackId("primary:4"), "Beta", "First", null, 95_000)
    private val alpha = MediaTrack(TrackId("primary:9"), "Alpha", "Second", null, 180_000)
    private val songs = listOf(zulu, beta, alpha)
}
