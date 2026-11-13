package com.zaus.nullwave.feature.search.presentation

import com.zaus.nullwave.core.mvi.testing.MviTestMain
import com.zaus.nullwave.core.mvi.testing.bind
import com.zaus.nullwave.core.mvi.testing.send
import androidx.lifecycle.SavedStateHandle
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblem
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults
import com.zaus.nullwave.feature.library.api.model.search.LibraryTrackSearchHit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertTrue

/** Fresh per test; no inheritance or hidden runner lifecycle. Tests delegate setup/teardown explicitly. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SearchPlaybackFixture {
    val mvi = MviTestMain()
    val dispatcher = mvi.dispatcher
    val repository = FakeSearchRepository()
    val playback = ControlledSearchPlaybackController()
    val store = mvi.store

    fun setUp() = mvi.setUp()

    fun tearDown() {
        repository.releaseCleanup()
        mvi.tearDown()
    }

    fun createVm(handle: SavedStateHandle = SavedStateHandle()): SearchViewModel =
        SearchViewModel(repository, playback, handle, "en").also { mvi.own(it) }

    suspend fun TestScope.readyVm(items: List<MediaTrack>, handle: SavedStateHandle = SavedStateHandle()): SearchViewModel =
        createVm(handle).also {
            bind(it)
            send(it, SearchIntent.AccessChecked(true))
            send(it, SearchIntent.QueryChanged("m4a"))
            settle()
            reply(AppResult.Success(results(items)))
        }

    fun TestScope.settle() {
        advanceTimeBy(250)
        runCurrent()
    }

    fun TestScope.reply(result: SearchResult) {
        repository.subscriptions.last().respond(result)
        runCurrent()
    }

    fun TestScope.respond(result: EmptyResult<PlaybackCommandError>) {
        assertTrue(playback.calls.last().reply.complete(result))
        runCurrent()
    }

    fun request() = (playback.calls.last().command as PlaybackCommand.ReplaceQueue).request
    fun ready(vm: SearchViewModel) = vm.snapshot.value.state.body as SearchBody.Ready
    fun results(items: List<MediaTrack>) = LibrarySearchResults(
        items.map { LibraryTrackSearchHit(it, "${it.title}.m4a") }, emptyList(), emptyList(),
    )

    fun problemSnapshot(
        code: PlaybackProblemCode,
        id: String,
        revision: Long = 0,
        activation: Long = 0,
        includeTarget: Boolean = true,
        sessionId: String = "search-test-session",
    ): PlaybackConnection.Connected = snapshot(
        code, id, revision = revision, activation = activation, includeTarget = includeTarget, sessionId = sessionId,
    )

    fun healthySnapshot(
        activation: Long = 0,
        entryId: String = "playing-beta",
        sessionId: String = "search-test-session",
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
        sessionId: String = "search-test-session",
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

    val zulu = MediaTrack(TrackId("primary:31"), "Zulu", "Third", null, 210_000)
    val beta = MediaTrack(TrackId("primary:4"), "Beta", "First", null, 95_000)
    val alpha = MediaTrack(TrackId("primary:9"), "Alpha", "Second", null, 180_000)
    val songs = listOf(zulu, beta, alpha)
}
