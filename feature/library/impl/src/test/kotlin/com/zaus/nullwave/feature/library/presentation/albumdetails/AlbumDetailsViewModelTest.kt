package com.zaus.nullwave.feature.library.presentation.albumdetails

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.mvi.testing.MviTestMain
import com.zaus.nullwave.core.mvi.testing.assertHandlesEveryIntent
import com.zaus.nullwave.core.mvi.testing.bind
import com.zaus.nullwave.core.mvi.testing.send
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueOrder
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblem
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumDetails
import com.zaus.nullwave.feature.library.api.repository.LibraryAlbumDetailsRepository
import com.zaus.nullwave.feature.library.presentation.ControlledLibraryPlaybackController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private typealias AlbumResult = AppResult<LibraryAlbumDetails, DataError>

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailsViewModelTest {
    private val mvi = MviTestMain()
    private val playback = ControlledLibraryPlaybackController()
    private val albumId = AlbumId("external_primary:folder/Download")
    private val grant = AlbumDetailsIntent.AccessChecked(LibraryAccess.Granted)
    private val reads = mutableListOf<Read>()
    private val repository = object : LibraryAlbumDetailsRepository {
        override fun observeAlbumDetails(albumId: AlbumId, localeTag: String): Flow<AlbumResult> {
            val read = Read(albumId, localeTag)
            reads += read
            return read.results.consumeAsFlow()
        }
    }
    private val first = details("First")
    private val updated = details("Updated")
    private val playDenied = AppResult.Failure(
        PlaybackCommandError.SourceFailure(first.tracks.single().id, PlaybackSourceError.AccessDenied),
    )

    @Before fun setUp() = mvi.setUp()
    @After fun tearDown() = mvi.tearDown()

    @Test
    fun `every album details intent has a handler`() = runTest(mvi.dispatcher) {
        assertHandlesEveryIntent(listOf(grant, AlbumDetailsIntent.LocaleChanged("sv"), AlbumDetailsIntent.Retry,
            AlbumDetailsIntent.Play(first, AlbumPlaybackStart.At(0)), AlbumDetailsIntent.DismissPlaybackError), ::createVm)
    }

    @Test
    fun `loading waits for both a binding and checked access and releases the read on unbind`() = runTest(mvi.dispatcher) {
        val vm = createVm()
        val binding = bind(vm)
        assertSame(AlbumDetailsBody.CheckingAccess, vm.body)
        send(vm, AlbumDetailsIntent.Retry)
        assertTrue(reads.isEmpty())
        binding.cancel()
        runCurrent()
        send(vm, grant)
        assertEquals(AlbumDetailsBody.Loading(), vm.body)
        assertTrue(reads.isEmpty())
        val visible = bind(vm)
        assertEquals(albumId, reads.single().id)
        assertEquals("en", reads.single().locale)
        reply(AppResult.Success(first))
        assertSame(first, vm.body.details)
        visible.cancel()
        runCurrent()
        assertTrue(reads.single().results.trySend(AppResult.Success(updated)).isFailure)
    }

    @Test
    fun `missing album discards its snapshot and can return through the same read`() = runTest(mvi.dispatcher) {
        val vm = openAlbum()
        reply(AppResult.Success(first))
        reply(AppResult.Failure(DataError.NotFound))
        assertSame(AlbumDetailsBody.NotFound, vm.body)
        send(vm, AlbumDetailsIntent.Retry)
        reply(AppResult.Success(updated))
        assertEquals(AlbumDetailsBody.Ready(updated), vm.body)
        assertEquals(1, reads.size)
    }

    @Test
    fun `platform revocation clears even cached error content and cancels the active read`() = runTest(mvi.dispatcher) {
        val vm = openAlbum()
        reply(AppResult.Success(first))
        reply(AppResult.Failure(DataError.Io))
        assertEquals(AlbumDetailsBody.ReadFailed(first), vm.body)
        val action = AccessAction.SettingsSuggested
        send(vm, AlbumDetailsIntent.AccessChecked(LibraryAccess.Required(action)))
        assertEquals(AlbumDetailsBody.AccessRequired(action), vm.body)
        assertTrue(reads.single().results.trySend(AppResult.Success(updated)).isFailure)
        send(vm, AlbumDetailsIntent.Retry)
        send(vm, AlbumDetailsIntent.LocaleChanged("ru"))
        assertEquals(1, reads.size)
        assertEquals(AlbumDetailsBody.AccessRequired(action), vm.body)
        send(vm, grant)
        assertEquals(AlbumDetailsBody.Loading(), vm.body)
        assertEquals(albumId, reads.last().id)
        assertEquals("ru", reads.last().locale)
        reply(AppResult.Success(updated))
        assertEquals(AlbumDetailsBody.Ready(updated), vm.body)
    }

    @Test
    fun `provider denial survives rebind and locale changes until a fresh grant`() = runTest(mvi.dispatcher) {
        val vm = createVm()
        val binding = bind(vm)
        send(vm, grant)
        reply(AppResult.Success(first))
        reply(AppResult.Failure(DataError.AccessDenied))
        assertTrue(reads.single().results.trySend(AppResult.Success(updated)).isFailure)
        assertEquals(AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain), vm.body)
        send(vm, AlbumDetailsIntent.Retry)
        binding.cancel()
        runCurrent()
        bind(vm)
        assertEquals(1, reads.size)
        send(vm, AlbumDetailsIntent.LocaleChanged("de"))
        assertEquals(AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain), vm.body)
        assertEquals(1, reads.size)
        send(vm, grant)
        assertEquals(2, reads.size)
        assertEquals("de", reads.last().locale)
        assertEquals(AlbumDetailsBody.Loading(), vm.body)
        reply(AppResult.Success(updated))
        assertEquals(AlbumDetailsBody.Ready(updated), vm.body)
    }

    @Test
    fun `retry after a completed setup failure starts just one new read`() = runTest(mvi.dispatcher) {
        val vm = openAlbum()
        reply(AppResult.Failure(DataError.Io))
        reads.single().results.close()
        runCurrent()
        assertEquals(AlbumDetailsBody.ReadFailed(null), vm.body)
        send(vm, AlbumDetailsIntent.Retry)
        assertEquals(AlbumDetailsBody.Loading(), vm.body)
        send(vm, AlbumDetailsIntent.Retry)
        assertEquals(2, reads.size)
        reply(AppResult.Success(updated))
        assertEquals(AlbumDetailsBody.Ready(updated), vm.body)
    }

    @Test
    fun `retry retains the last good snapshot but changing locale discards the old order`() = runTest(mvi.dispatcher) {
        val vm = openAlbum()
        reply(AppResult.Success(first))
        reply(AppResult.Failure(DataError.Io))
        send(vm, AlbumDetailsIntent.Retry)
        assertEquals(AlbumDetailsBody.Loading(first), vm.body)
        reply(AppResult.Failure(DataError.Io))
        assertEquals(AlbumDetailsBody.ReadFailed(first), vm.body)
        send(vm, AlbumDetailsIntent.LocaleChanged("sv"))
        assertEquals(AlbumDetailsBody.Loading(), vm.body)
        send(vm, AlbumDetailsIntent.LocaleChanged("sv"))
        assertEquals(listOf("en", "en", "sv"), reads.map { it.locale })
        assertTrue(reads[1].results.trySend(AppResult.Success(first)).isFailure)
        reply(AppResult.Success(updated))
        assertEquals(AlbumDetailsBody.Ready(updated), vm.body)
    }

    @Test
    fun `rapid play row and shuffle requests use the rendered album even across a reorder`() = runTest(mvi.dispatcher) {
        val vm = openAlbum()
        val tracks = listOf("z", "a", "b").map { first.tracks.single().copy(id = TrackId(it)) }
        val album = LibraryAlbumDetails.create(first.album.copy(trackCount = tracks.size), tracks)
        reply(AppResult.Success(album))
        send(vm, AlbumDetailsIntent.Play(album, AlbumPlaybackStart.At(0)))
        reply(AppResult.Success(LibraryAlbumDetails.create(album.album, tracks.reversed())))
        send(vm, AlbumDetailsIntent.Play(album, AlbumPlaybackStart.At(2)))
        send(vm, AlbumDetailsIntent.Play(album, AlbumPlaybackStart.Shuffle))
        val requests = playback.calls.map { (it.command as PlaybackCommand.ReplaceQueue).request }
        assertEquals(listOf(0, 2), requests.take(2).map { it.startIndex })
        assertEquals(listOf(PlaybackQueueOrder.InOrder, PlaybackQueueOrder.InOrder, PlaybackQueueOrder.Shuffled),
            requests.map { it.order })
        assertTrue(requests.all { it.trackIds == tracks.map { track -> track.id } && it.playRequested && it.startPositionMs == 0L })
        assertTrue(requests.last().startIndex in tracks.indices)
        assertTrue(playback.calls.take(2).all { it.waiterCancelled })
        playback.calls.first().reply.complete(AppResult.Failure(PlaybackCommandError.StartNotAllowed))
        playback.calls.last().reply.complete(AppResult.Success(Unit))
        runCurrent()
        assertEquals(null, vm.snapshot.value.state.playbackError)
    }

    @Test
    fun `resolver denial hides the album and blocks stale taps until rechecked`() = runTest(mvi.dispatcher) {
        val vm = openAlbum()
        val play = AlbumDetailsIntent.Play(first, AlbumPlaybackStart.At(0))
        send(vm, play)
        assertTrue(playback.calls.isEmpty())
        reply(AppResult.Success(first))
        send(vm, play)
        playback.calls.single().reply.complete(playDenied)
        runCurrent()
        assertEquals(AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain), vm.body)
        assertTrue(vm.snapshot.value.state.playbackError != null)
        assertTrue(reads.single().results.trySend(AppResult.Success(first)).isFailure)
        send(vm, AlbumDetailsIntent.LocaleChanged("sv"))
        send(vm, AlbumDetailsIntent.Retry)
        assertEquals(1, reads.size)
        send(vm, play)
        assertEquals(1, playback.calls.size)
        send(vm, AlbumDetailsIntent.DismissPlaybackError)
        assertEquals(null, vm.snapshot.value.state.playbackError)
        send(vm, grant)
        assertEquals(listOf("en", "sv"), reads.map { it.locale })
        reply(AppResult.Success(first))
        send(vm, play)
        assertEquals(2, playback.calls.size)
        reply(AppResult.Failure(DataError.NotFound))
        send(vm, play)
        assertEquals(2, playback.calls.size)
    }

    @Test
    fun `late Play denial after access is restored leaves the refreshed album open`() = runTest(mvi.dispatcher) {
        val vm = openAlbum()
        reply(AppResult.Success(first))
        send(vm, AlbumDetailsIntent.Play(first, AlbumPlaybackStart.At(0)))
        val oldPlay = playback.calls.single()

        reply(AppResult.Failure(DataError.AccessDenied))
        assertEquals(AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain), vm.body)
        send(vm, grant)
        reply(AppResult.Success(updated))

        assertTrue(oldPlay.reply.complete(playDenied))
        runCurrent()
        assertEquals(AlbumDetailsBody.Ready(updated), vm.body)
        assertEquals(null, vm.snapshot.value.state.playbackError)
        reply(AppResult.Failure(DataError.NotFound))
        assertSame(AlbumDetailsBody.NotFound, vm.body)
        assertEquals(2, reads.size)
    }

    @Test
    fun `denial from a new Play closes the restored album even after a routine grant`() = runTest(mvi.dispatcher) {
        val vm = openAlbum()
        reply(AppResult.Success(first))
        reply(AppResult.Failure(DataError.AccessDenied))
        send(vm, grant)
        reply(AppResult.Success(updated))
        send(vm, AlbumDetailsIntent.Play(updated, AlbumPlaybackStart.At(0)))
        val newPlay = playback.calls.single()

        send(vm, grant)
        reply(AppResult.Success(updated))
        assertEquals(AlbumDetailsBody.Ready(updated), vm.body)
        assertTrue(newPlay.reply.complete(playDenied))
        runCurrent()

        assertEquals(AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain), vm.body)
        assertTrue(vm.snapshot.value.state.playbackError != null)
        assertTrue(reads.last().results.trySend(AppResult.Success(updated)).isFailure)
    }

    @Test
    fun `replaying a denial published while unbound leaves the restored album open`() = runTest(mvi.dispatcher) {
        val resumePlayback = CompletableDeferred<Unit>()
        val vm = restoreAlbumBeforePlaybackReconnects(resumePlayback)

        resumePlayback.complete(Unit)
        runCurrent()

        assertEquals(AlbumDetailsBody.Ready(updated), vm.body)
        assertEquals(null, vm.snapshot.value.state.playbackError)
        reply(AppResult.Failure(DataError.NotFound))
        assertSame(AlbumDetailsBody.NotFound, vm.body)
        assertEquals(2, reads.size)
    }

    @Test
    fun `a denial published after the grant closes the album even as the first reconnected snapshot`() = runTest(mvi.dispatcher) {
        val resumePlayback = CompletableDeferred<Unit>()
        val vm = restoreAlbumBeforePlaybackReconnects(resumePlayback)

        publishEngineDenial("after-grant")
        resumePlayback.complete(Unit)
        runCurrent()

        assertEquals(AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain), vm.body)
        assertTrue(reads.last().results.trySend(AppResult.Success(updated)).isFailure)
        assertEquals(null, vm.snapshot.value.state.playbackError)
    }

    @Test
    fun `buffered denial cleared by the service before the recheck cannot hide the album`() = runTest(mvi.dispatcher) {
        val deliverDenial = CompletableDeferred<Unit>()
        val captured = CompletableDeferred<Unit>()
        playback.stateUpdates = playback.states.onEach { connection ->
            if ((connection as? PlaybackConnection.Connected)?.snapshot?.transport?.problem != null) {
                captured.complete(Unit)
                deliverDenial.await()
            }
        }
        val vm = openAlbum()
        reply(AppResult.Success(first))
        send(vm, AlbumDetailsIntent.Play(first, AlbumPlaybackStart.At(0)))
        publishEngineDenial("buffered")
        runCurrent()
        assertTrue(captured.isCompleted)
        reply(AppResult.Failure(DataError.AccessDenied))
        val failed = checkNotNull(playback.publishedSnapshot)
        playback.states.value = PlaybackConnection.Connected(PlaybackSnapshot.create(
            sessionId = failed.sessionId, revision = failed.revision + 1, queue = failed.queue,
        ))
        send(vm, grant)
        reply(AppResult.Success(updated))
        deliverDenial.complete(Unit)
        runCurrent()
        assertEquals(AlbumDetailsBody.Ready(updated), vm.body)
    }

    @Test
    fun `new engine denial closes the album read before acknowledgement and rebind waits for grant`() = runTest(mvi.dispatcher) {
        val vm = createVm()
        val binding = bind(vm)
        send(vm, grant)
        reply(AppResult.Success(first))
        send(vm, AlbumDetailsIntent.Play(first, AlbumPlaybackStart.At(0)))
        publishEngineDenial("denied")
        runCurrent()
        assertEquals(AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain), vm.body)
        assertTrue(reads.single().results.trySend(AppResult.Success(updated)).isFailure)
        playback.calls.single().reply.complete(AppResult.Success(Unit))
        binding.cancel()
        runCurrent()
        bind(vm)
        send(vm, AlbumDetailsIntent.Retry)
        assertEquals(1, reads.size)
        assertEquals(AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain), vm.body)
        send(vm, grant)
        assertEquals(2, reads.size)
        reply(AppResult.Success(updated))
        assertEquals(AlbumDetailsBody.Ready(updated), vm.body)
    }

    private val AlbumDetailsViewModel.body get() = snapshot.value.state.body
    private fun createVm() = mvi.own(AlbumDetailsViewModel(albumId, repository, "en", playback))

    private suspend fun TestScope.restoreAlbumBeforePlaybackReconnects(resumePlayback: CompletableDeferred<Unit>): AlbumDetailsViewModel {
        val vm = createVm()
        val binding = bind(vm)
        send(vm, grant)
        reply(AppResult.Success(first))
        send(vm, AlbumDetailsIntent.Play(first, AlbumPlaybackStart.At(0)))
        reply(AppResult.Failure(DataError.AccessDenied))
        assertEquals(AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain), vm.body)
        binding.cancel()
        runCurrent()
        assertEquals(0, playback.activeStateCollectors)

        publishEngineDenial("before-grant")
        playback.beforeStateCollection = { resumePlayback.await() }
        send(vm, grant)
        bind(vm)
        reply(AppResult.Success(updated))
        assertEquals(AlbumDetailsBody.Ready(updated), vm.body)
        return vm
    }

    private fun publishEngineDenial(id: String) {
        val previous = (playback.states.value as PlaybackConnection.Connected).snapshot
        val entry = QueueEntry(QueueEntryId("playing"), first.tracks.single())
        val token = PlaybackItemToken(previous.sessionId, entry.entryId, 0)
        playback.states.value = PlaybackConnection.Connected(PlaybackSnapshot.create(
            sessionId = previous.sessionId,
            revision = previous.revision + 1,
            queue = PlaybackQueue.create(entries = listOf(entry), current = token),
            transport = PlaybackTransport(problem = PlaybackProblem(PlaybackProblemId(id), PlaybackProblemCode.AccessDenied, token)),
        ))
    }

    private suspend fun TestScope.openAlbum() = createVm().also { vm ->
        bind(vm)
        send(vm, grant)
    }

    private suspend fun TestScope.reply(result: AlbumResult) {
        reads.last().results.send(result)
        runCurrent()
    }

    private fun details(title: String) = LibraryAlbumDetails.create(
        LibraryAlbum(albumId, "Download", AlbumArtistCredit.Unknown, 1),
        listOf(MediaTrack(TrackId("primary:1"), title, null, "Download", null)),
    )

    private class Read(val id: AlbumId, val locale: String) {
        val results = Channel<AlbumResult>(Channel.UNLIMITED)
    }
}
