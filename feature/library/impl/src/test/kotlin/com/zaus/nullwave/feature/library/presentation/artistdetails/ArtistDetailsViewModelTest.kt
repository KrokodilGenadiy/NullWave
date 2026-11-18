package com.zaus.nullwave.feature.library.presentation.artistdetails

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.mvi.testing.MviTestMain
import com.zaus.nullwave.core.mvi.testing.assertHandlesEveryIntent
import com.zaus.nullwave.core.mvi.testing.bind
import com.zaus.nullwave.core.mvi.testing.send
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistDetails
import com.zaus.nullwave.feature.library.api.repository.LibraryArtistDetailsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private typealias ArtistResult = AppResult<LibraryArtistDetails, DataError>

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailsViewModelTest {
    private val mvi = MviTestMain()
    private val artistId = ArtistId("external_primary:4")
    private val grant = ArtistDetailsIntent.AccessChecked(LibraryAccess.Granted)
    private val reads = mutableListOf<Read>()
    private val repository = object : LibraryArtistDetailsRepository {
        override fun observeArtistDetails(artistId: ArtistId, localeTag: String): Flow<ArtistResult> = flow {
            val read = Read(artistId, localeTag).also(reads::add)
            try {
                for (result in read.results) {
                    read.replyGate?.let { gate ->
                        // A provider callback may finish late; emission still uses the collector's context.
                        withContext(NonCancellable) {
                            read.replyPending = true
                            gate.await()
                            read.lateReplyReceived = true
                        }
                    }
                    emit(result)
                }
            } finally {
                read.closed = true
                read.results.cancel()
            }
        }
    }
    private val first = details("First")
    private val updated = details("Updated")

    @Before fun setUp() = mvi.setUp()
    @After fun tearDown() {
        reads.forEach { it.replyGate?.complete(Unit) }
        mvi.tearDown()
    }

    @Test
    fun `every artist details intent has a handler`() = runTest(mvi.dispatcher) {
        assertHandlesEveryIntent(listOf(grant, ArtistDetailsIntent.LocaleChanged("sv"), ArtistDetailsIntent.Retry), ::createVm)
    }

    @Test
    fun `reading waits for both checked access and binding and stops immediately on unbind`() = runTest(mvi.dispatcher) {
        val vm = createVm()
        val initialBinding = bind(vm)
        assertSame(ArtistDetailsBody.CheckingAccess, vm.body)
        send(vm, ArtistDetailsIntent.Retry)
        send(vm, ArtistDetailsIntent.LocaleChanged("ru"))
        assertSame(ArtistDetailsBody.CheckingAccess, vm.body)
        assertTrue(reads.isEmpty())
        initialBinding.cancel()
        runCurrent()
        send(vm, grant)
        assertEquals(ArtistDetailsBody.Loading(), vm.body)
        assertTrue(reads.isEmpty())

        val binding = bind(vm)
        assertEquals(artistId, reads.single().id)
        assertEquals("ru", reads.single().locale)
        reply(AppResult.Success(first))
        assertSame(first, vm.body.details)
        binding.cancel()
        runCurrent()
        assertTrue(reads.single().closed)
        assertTrue(reads.single().results.trySend(AppResult.Success(updated)).isFailure)
    }

    @Test
    fun `ready passes through repository order complete album summaries and albumless tracks`() = runTest(mvi.dispatcher) {
        val vm = openArtist()
        val snapshot = LibraryArtistDetails.create(
            LibraryArtist(artistId, null, 2, 1),
            listOf(LibraryAlbum(AlbumId("external_primary:folder/Download"), "Download", AlbumArtistCredit.Various, 9)),
            listOf(
                MediaTrack(TrackId("primary:2"), "Z", null, "Download", null),
                MediaTrack(TrackId("primary:1"), "A", null, null, null),
            ),
        )
        reply(AppResult.Success(snapshot))
        assertSame(snapshot, vm.body.details)
        assertEquals(listOf("Z", "A"), vm.body.details!!.tracks.map { it.title })
        assertEquals(9, vm.body.details!!.albums.single().trackCount)
        reply(AppResult.Success(first))
        assertEquals(ArtistDetailsBody.Ready(first), vm.body)
        assertTrue(vm.body.details!!.albums.isEmpty())
        send(vm, ArtistDetailsIntent.Retry)
        assertEquals(1, reads.size)
    }

    @Test
    fun `missing artist drops its snapshot and can return on the same subscription`() = runTest(mvi.dispatcher) {
        val vm = openArtist()
        reply(AppResult.Success(first))
        reply(AppResult.Failure(DataError.NotFound))
        assertSame(ArtistDetailsBody.NotFound, vm.body)
        assertNull(vm.body.details)
        send(vm, ArtistDetailsIntent.Retry)
        reply(AppResult.Success(updated))
        assertEquals(ArtistDetailsBody.Ready(updated), vm.body)
        assertEquals(1, reads.size)
    }

    @Test
    fun `platform denial clears cached error data keeps Android action and waits for recheck`() = runTest(mvi.dispatcher) {
        val vm = openArtist()
        reply(AppResult.Success(first))
        reply(AppResult.Failure(DataError.Io))
        assertEquals(ArtistDetailsBody.ReadFailed(first), vm.body)
        val denied = ArtistDetailsBody.AccessRequired(AccessAction.SettingsSuggested)
        send(vm, ArtistDetailsIntent.AccessChecked(LibraryAccess.Required(denied.action)))
        assertEquals(denied, vm.body)
        assertFalse(vm.snapshot.value.state.platformGranted)
        assertNull(vm.body.details)
        assertTrue(reads.single().closed)
        send(vm, ArtistDetailsIntent.Retry)
        send(vm, ArtistDetailsIntent.LocaleChanged("ru"))
        assertEquals(denied, vm.body)
        assertEquals(1, reads.size)

        send(vm, grant)
        assertEquals(ArtistDetailsBody.Loading(), vm.body)
        assertEquals("ru", reads.last().locale)
        reply(AppResult.Success(updated))
        assertEquals(ArtistDetailsBody.Ready(updated), vm.body)
    }

    @Test
    fun `provider denial blocks rebind locale changes and Retry until a fresh grant`() = runTest(mvi.dispatcher) {
        val vm = createVm()
        val binding = bind(vm)
        send(vm, grant)
        reply(AppResult.Success(first))
        reply(AppResult.Failure(DataError.AccessDenied))
        val denied = ArtistDetailsBody.AccessRequired(AccessAction.CheckAgain)
        assertEquals(denied, vm.body)
        assertTrue(reads.single().closed)
        send(vm, ArtistDetailsIntent.Retry)
        binding.cancel()
        runCurrent()
        bind(vm)
        send(vm, ArtistDetailsIntent.LocaleChanged("de"))
        assertEquals(denied, vm.body)
        assertEquals(1, reads.size)

        send(vm, grant)
        assertEquals(2, reads.size)
        assertEquals("de", reads.last().locale)
        assertEquals(ArtistDetailsBody.Loading(), vm.body)
        reply(AppResult.Success(updated))
        assertEquals(ArtistDetailsBody.Ready(updated), vm.body)
        reply(AppResult.Failure(DataError.AccessDenied))
        assertEquals(denied, vm.body)
        assertTrue(reads.last().closed)
    }

    @Test
    fun `IO can recover without a new subscription`() = runTest(mvi.dispatcher) {
        val vm = openArtist()
        reply(AppResult.Failure(DataError.Io))
        assertEquals(ArtistDetailsBody.ReadFailed(null), vm.body)
        reply(AppResult.Success(first))
        reply(AppResult.Failure(DataError.Io))
        assertEquals(ArtistDetailsBody.ReadFailed(first), vm.body)
        reply(AppResult.Success(updated))
        assertEquals(ArtistDetailsBody.Ready(updated), vm.body)
        assertEquals(1, reads.size)
    }

    @Test
    fun `Retry restarts completed setup failure once and ignores rapid taps while loading`() = runTest(mvi.dispatcher) {
        val vm = openArtist()
        reply(AppResult.Failure(DataError.Io))
        reads.single().results.close()
        runCurrent()
        assertTrue(reads.single().closed)
        send(vm, ArtistDetailsIntent.Retry)
        assertEquals(ArtistDetailsBody.Loading(), vm.body)
        send(vm, ArtistDetailsIntent.Retry)
        assertEquals(2, reads.size)
        reply(AppResult.Success(updated))
        assertEquals(ArtistDetailsBody.Ready(updated), vm.body)
    }

    @Test
    fun `Retry retains last good data but changing locale discards the previous order`() = runTest(mvi.dispatcher) {
        val vm = openArtist()
        reply(AppResult.Success(first))
        reply(AppResult.Failure(DataError.Io))
        send(vm, ArtistDetailsIntent.Retry)
        assertEquals(ArtistDetailsBody.Loading(first), vm.body)
        reply(AppResult.Failure(DataError.Io))
        assertEquals(ArtistDetailsBody.ReadFailed(first), vm.body)
        send(vm, ArtistDetailsIntent.LocaleChanged("sv"))
        assertEquals(ArtistDetailsBody.Loading(), vm.body)
        send(vm, ArtistDetailsIntent.LocaleChanged("sv"))
        assertEquals(listOf("en", "en", "sv"), reads.map { it.locale })
        assertTrue(reads.take(2).all { it.closed })
        reply(AppResult.Success(updated))
        assertEquals(ArtistDetailsBody.Ready(updated), vm.body)
    }

    @Test
    fun `fresh access check invalidates an old denial even when source inputs are unchanged`() = runTest(mvi.dispatcher) {
        val vm = openArtist()
        reply(AppResult.Success(first))
        val old = reads.single().delayReply(AppResult.Failure(DataError.AccessDenied))
        runCurrent()
        assertTrue(old.replyPending)
        send(vm, grant)
        assertFalse(old.closed)
        assertEquals(ArtistDetailsBody.Loading(first), vm.body)
        old.replyGate!!.complete(Unit)
        runCurrent()
        assertTrue(old.lateReplyReceived)
        assertTrue(old.closed)
        assertEquals(2, reads.size)
        assertEquals(ArtistDetailsBody.Loading(first), vm.body)
        reply(AppResult.Success(updated))
        assertEquals(ArtistDetailsBody.Ready(updated), vm.body)
        reply(AppResult.Failure(DataError.AccessDenied))
        assertEquals(ArtistDetailsBody.AccessRequired(AccessAction.CheckAgain), vm.body)
    }

    @Test
    fun `locale A B A cannot reuse a late result from the original A subscription`() = runTest(mvi.dispatcher) {
        val vm = openArtist()
        val old = reads.single().delayReply(AppResult.Success(first))
        runCurrent()
        assertTrue(old.replyPending)
        send(vm, ArtistDetailsIntent.LocaleChanged("sv"))
        assertFalse(old.closed)
        send(vm, ArtistDetailsIntent.LocaleChanged("en"))
        assertEquals(ArtistDetailsBody.Loading(), vm.body)
        old.replyGate!!.complete(Unit)
        runCurrent()
        assertTrue(old.lateReplyReceived)
        assertTrue(old.closed)
        assertEquals(listOf("en", "en"), reads.map { it.locale })
        assertEquals(ArtistDetailsBody.Loading(), vm.body)
        reply(AppResult.Success(updated))
        assertEquals(ArtistDetailsBody.Ready(updated), vm.body)
    }

    @Test
    fun `revocation rejects late content and a recreated ViewModel starts without cached access`() = runTest(mvi.dispatcher) {
        val vm = openArtist()
        reply(AppResult.Success(first))
        val old = reads.single().delayReply(AppResult.Success(updated))
        runCurrent()
        assertTrue(old.replyPending)
        val action = AccessAction.ExplainThenRequest
        send(vm, ArtistDetailsIntent.AccessChecked(LibraryAccess.Required(action)))
        old.replyGate!!.complete(Unit)
        runCurrent()
        assertTrue(old.lateReplyReceived)
        assertTrue(old.closed)
        assertEquals(ArtistDetailsBody.AccessRequired(action), vm.body)
        mvi.store.clear()
        runCurrent()

        val replacement = createVm()
        bind(replacement)
        assertSame(ArtistDetailsBody.CheckingAccess, replacement.body)
        assertEquals(1, reads.size)
        send(replacement, grant)
        assertEquals(2, reads.size)
        reply(AppResult.Success(updated))
        assertEquals(ArtistDetailsBody.Ready(updated), replacement.body)
    }

    @Test
    fun `clearing the entry cancels reading and retained UI callbacks cannot restart it`() = runTest(mvi.dispatcher) {
        val vm = openArtist()
        val api = vm.uiApi()
        mvi.store.clear()
        runCurrent()
        assertTrue(reads.single().closed)
        api.onIntent(grant)
        api.onIntent(ArtistDetailsIntent.Retry)
        runCurrent()
        assertEquals(1, reads.size)
    }

    private val ArtistDetailsViewModel.body get() = snapshot.value.state.body
    private fun createVm() = mvi.own(ArtistDetailsViewModel(artistId, repository, "en"))

    private suspend fun TestScope.openArtist() = createVm().also {
        bind(it)
        send(it, grant)
    }

    private suspend fun TestScope.reply(result: ArtistResult) {
        reads.last().results.send(result)
        runCurrent()
    }

    private fun details(title: String) = LibraryArtistDetails.create(
        LibraryArtist(artistId, "Artist", 1, 0), emptyList(),
        listOf(MediaTrack(TrackId("primary:1"), title, "Artist", null, null)),
    )

    private class Read(val id: ArtistId, val locale: String) {
        val results = Channel<ArtistResult>(Channel.UNLIMITED)
        var replyGate: CompletableDeferred<Unit>? = null
        var replyPending = false
        var lateReplyReceived = false
        var closed = false

        fun delayReply(result: ArtistResult) = apply {
            replyGate = CompletableDeferred()
            check(results.trySend(result).isSuccess)
        }
    }
}
