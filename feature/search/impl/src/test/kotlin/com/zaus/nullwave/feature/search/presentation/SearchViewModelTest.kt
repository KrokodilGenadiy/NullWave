package com.zaus.nullwave.feature.search.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.search.LibraryAlbumSearchHit
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults
import com.zaus.nullwave.feature.library.api.model.search.LibraryTrackSearchHit
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrack
import com.zaus.nullwave.feature.library.api.model.track.TrackId
import com.zaus.nullwave.feature.search.impl.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeSearchRepository()
    private var nextKey = 0

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() {
        repository.releaseCleanup()
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `state collectors and intents without binding never start a search`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.state.collect {} }
        send(vm, SearchIntent.QueryChanged("Signal"))
        send(vm, SearchIntent.AccessChecked(true))
        settle()
        assertTrue(repository.calls.isEmpty())
        bind(vm)
        settle()
        assertEquals("signal", repository.calls.single().text)
    }

    @Test
    fun `empty input stays idle across access checks retries and locale changes`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        for (intent in listOf(
            SearchIntent.AccessChecked(false), SearchIntent.Retry, SearchIntent.AccessChecked(true),
            SearchIntent.QueryChanged(" \t\n\u00A0"), SearchIntent.LocaleChanged("en-US"), SearchIntent.ClearQuery,
        )) {
            send(vm, intent)
            settle()
            assertSame(SearchBody.Idle, vm.state.value.body)
            assertNull(vm.state.value.query)
        }
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `a nonempty query waits for a fresh platform access check`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        send(vm, SearchIntent.QueryChanged("Signal"))
        settle()
        assertSame(SearchBody.CheckingAccess, vm.state.value.body)
        assertTrue(repository.calls.isEmpty())
        send(vm, SearchIntent.AccessChecked(true))
        assertSame(SearchBody.Loading, vm.state.value.body)
        settle()
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `known denied access gates input edits without repository calls or permission effects`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        send(vm, SearchIntent.AccessChecked(false))
        send(vm, SearchIntent.QueryChanged("Signal"))
        send(vm, SearchIntent.QueryChanged("Other"))
        send(vm, SearchIntent.LocaleChanged("en"))
        send(vm, SearchIntent.Retry)
        settle()
        assertSame(SearchBody.AccessRequired, vm.state.value.body)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `typing waits 250 milliseconds after the last canonical query change`() = runTest(dispatcher) {
        val vm = readyForInput()
        send(vm, SearchIntent.QueryChanged("s"))
        advanceTimeBy(150)
        send(vm, SearchIntent.QueryChanged("si"))
        advanceTimeBy(150)
        send(vm, SearchIntent.QueryChanged("sig"))
        advanceTimeBy(249)
        runCurrent()
        assertTrue(repository.calls.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals("sig", repository.calls.single().text)
    }

    @Test
    fun `equivalent spelling updates raw text without moving the debounce deadline`() = runTest(dispatcher) {
        val vm = readyForInput()
        send(vm, SearchIntent.QueryChanged("Ёлка"))
        advanceTimeBy(200)
        send(vm, SearchIntent.QueryChanged("  ЕЛКА\u00A0"))
        advanceTimeBy(50)
        runCurrent()
        assertEquals("  ЕЛКА\u00A0", vm.state.value.rawQuery)
        assertEquals("елка", repository.calls.single().text)
    }

    @Test
    fun `equivalent spelling preserves visible results and the live observer`() = runTest(dispatcher) {
        val vm = search("Signal")
        reply(AppResult.Success(results))
        val before = ready(vm)
        send(vm, SearchIntent.QueryChanged(" SIGNAL  "))
        settle()
        assertSame(before, vm.state.value.body)
        assertEquals(" SIGNAL  ", vm.state.value.rawQuery)
        assertEquals(1, repository.subscriptions.size)
    }

    @Test
    fun `new input immediately clears old results and rejects a queued old answer`() = runTest(dispatcher) {
        val vm = search("first")
        reply(AppResult.Success(results))
        val old = repository.subscriptions.single()
        vm.onIntent(SearchIntent.QueryChanged("second"))
        old.respond(AppResult.Success(emptyResults))
        runCurrent()
        assertSame(SearchBody.Loading, vm.state.value.body)
        assertTrue(old.closed)
        assertEquals(1, repository.calls.size)
        settle()
        assertEquals("second", repository.calls.last().text)
    }

    @Test
    fun `A B A starts a new A only after old cleanup and discards the intervening query`() = runTest(dispatcher) {
        val vm = search("A")
        reply(AppResult.Success(results))
        val old = repository.subscriptions.single()
        old.cleanupGate = CompletableDeferred()
        send(vm, SearchIntent.QueryChanged("B"))
        send(vm, SearchIntent.QueryChanged("A"))
        settle()
        assertTrue(old.cleanupStarted)
        assertFalse(old.closed)
        assertEquals(1, repository.calls.size)
        assertSame(SearchBody.Loading, vm.state.value.body)
        repository.releaseCleanup()
        runCurrent()
        settle()
        assertEquals(listOf("a", "a"), repository.calls.map { it.text })
        reply(AppResult.Success(emptyResults))
        assertTrue(ready(vm).isEmpty)
        assertEquals(1, repository.maxActiveCount)
    }

    @Test
    fun `clearing during debounce is immediate and never calls the repository`() = runTest(dispatcher) {
        val vm = readyForInput()
        send(vm, SearchIntent.QueryChanged("signal"))
        advanceTimeBy(200)
        send(vm, SearchIntent.ClearQuery)
        assertSame(SearchBody.Idle, vm.state.value.body)
        settle()
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `whitespace replaces active results with idle even while cleanup is delayed`() = runTest(dispatcher) {
        val vm = search("signal")
        reply(AppResult.Success(results))
        val old = repository.subscriptions.single()
        old.cleanupGate = CompletableDeferred()
        send(vm, SearchIntent.QueryChanged(" \n "))
        assertSame(SearchBody.Idle, vm.state.value.body)
        assertTrue(old.cleanupStarted)
        repository.releaseCleanup()
        runCurrent()
        settle()
        assertTrue(old.closed)
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `success publishes all groups and empty updates mean no matches without another search`() = runTest(dispatcher) {
        val vm = search()
        reply(AppResult.Success(results))
        assertEquals(SearchBody.Ready(results), vm.state.value.body)
        assertFalse(ready(vm).isEmpty)
        reply(AppResult.Success(emptyResults))
        assertTrue(ready(vm).isEmpty)
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `initial read failure is localized and retry restarts a completed flow once`() = runTest(dispatcher) {
        repository.initialResult = AppResult.Failure(DataError.Io, IllegalArgumentException("private metadata"))
        repository.completeAfterInitial = true
        val vm = search()
        assertEquals(SearchBody.Failed(loadError), vm.state.value.body)
        assertTrue(repository.subscriptions.single().closed)
        repository.initialResult = AppResult.Success(results)
        repository.completeAfterInitial = false
        send(vm, SearchIntent.Retry)
        send(vm, SearchIntent.Retry)
        assertSame(SearchBody.Loading, vm.state.value.body)
        settle()
        assertEquals(SearchBody.Ready(results), vm.state.value.body)
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun `observer IO failure preserves same-query results and a later update recovers`() = runTest(dispatcher) {
        val vm = search()
        reply(AppResult.Success(results))
        reply(AppResult.Failure(DataError.Io))
        assertEquals(SearchBody.Ready(results, SearchRefresh.Failed(refreshError)), vm.state.value.body)
        reply(AppResult.Success(emptyResults))
        assertEquals(SearchBody.Ready(emptyResults), vm.state.value.body)
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `retry keeps an empty successful snapshot with an explicit refresh status`() = runTest(dispatcher) {
        val vm = search()
        reply(AppResult.Success(emptyResults))
        send(vm, SearchIntent.Retry)
        assertEquals(1, repository.calls.size)
        reply(AppResult.Failure(DataError.Io))
        send(vm, SearchIntent.Retry)
        send(vm, SearchIntent.Retry)
        assertEquals(SearchBody.Ready(emptyResults, SearchRefresh.Running), vm.state.value.body)
        settle()
        assertEquals(2, repository.calls.size)
        reply(AppResult.Success(results))
        assertEquals(SearchBody.Ready(results), vm.state.value.body)
    }

    @Test
    fun `a failure for a new query never retains the previous query results`() = runTest(dispatcher) {
        val vm = search("A")
        reply(AppResult.Success(results))
        send(vm, SearchIntent.QueryChanged("B"))
        settle()
        reply(AppResult.Failure(DataError.Io))
        assertEquals(SearchBody.Failed(loadError), vm.state.value.body)
        assertEquals("B", vm.state.value.rawQuery)
    }

    @Test
    fun `platform revocation clears every group and cancels the observer`() = runTest(dispatcher) {
        val vm = search()
        reply(AppResult.Success(results))
        send(vm, SearchIntent.AccessChecked(false))
        assertSame(SearchBody.AccessRequired, vm.state.value.body)
        assertNull(vm.state.value.request)
        assertEquals(0, repository.activeCount)
        send(vm, SearchIntent.QueryChanged("new"))
        settle()
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `revocation during debounce prevents any provider observation`() = runTest(dispatcher) {
        val vm = readyForInput()
        send(vm, SearchIntent.QueryChanged("signal"))
        advanceTimeBy(200)
        send(vm, SearchIntent.AccessChecked(false))
        settle()
        assertSame(SearchBody.AccessRequired, vm.state.value.body)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `provider denial clears results and gates later edits until a fresh check`() = runTest(dispatcher) {
        val vm = search()
        reply(AppResult.Success(results))
        reply(AppResult.Failure(DataError.AccessDenied))
        assertSame(SearchBody.AccessRequired, vm.state.value.body)
        assertTrue(repository.subscriptions.single().closed)
        send(vm, SearchIntent.QueryChanged(" SIGNAL "))
        send(vm, SearchIntent.Retry)
        send(vm, SearchIntent.ClearQuery)
        send(vm, SearchIntent.QueryChanged("Another"))
        settle()
        assertSame(SearchBody.AccessRequired, vm.state.value.body)
        assertEquals(1, repository.calls.size)
        send(vm, SearchIntent.AccessChecked(true))
        settle()
        assertEquals("another", repository.calls.last().text)
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun `fresh grant after provider denial can retry an unchanged query`() = runTest(dispatcher) {
        val vm = search()
        reply(AppResult.Failure(DataError.AccessDenied))
        send(vm, SearchIntent.AccessChecked(true))
        settle()
        reply(AppResult.Success(results))
        assertEquals(SearchBody.Ready(results), vm.state.value.body)
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun `returning with the same grant restarts observation and preserves results while updating`() = runTest(dispatcher) {
        val vm = search()
        reply(AppResult.Success(results))
        send(vm, SearchIntent.AccessChecked(true))
        assertEquals(SearchBody.Ready(results, SearchRefresh.Running), vm.state.value.body)
        settle()
        assertEquals(2, repository.calls.size)
        assertEquals(1, repository.maxActiveCount)
        reply(AppResult.Success(emptyResults))
        assertEquals(SearchBody.Ready(emptyResults), vm.state.value.body)
    }

    @Test
    fun `late permission or settings callback after clearing does not start a search`() = runTest(dispatcher) {
        val vm = search()
        send(vm, SearchIntent.AccessChecked(false))
        send(vm, SearchIntent.ClearQuery)
        for (granted in listOf(true, false, true)) {
            send(vm, SearchIntent.AccessChecked(granted))
            settle()
            assertSame(SearchBody.Idle, vm.state.value.body)
        }
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `locale changes revoke old ordering and identical locale does not restart`() = runTest(dispatcher) {
        val vm = search("Ёлка")
        reply(AppResult.Success(results))
        send(vm, SearchIntent.LocaleChanged("en-US"))
        assertSame(SearchBody.Loading, vm.state.value.body)
        settle()
        assertEquals("елка", repository.calls.last().text)
        assertEquals("en-US", repository.calls.last().localeTag)
        reply(AppResult.Success(results))
        send(vm, SearchIntent.LocaleChanged("en-US"))
        settle()
        assertEquals(2, repository.calls.size)
        assertEquals(SearchBody.Ready(results), vm.state.value.body)
    }

    @Test
    fun `published state owns collections including nested matching artist names`() = runTest(dispatcher) {
        val vm = search()
        val names = mutableListOf("Nullwave")
        val tracks = results.tracks.toMutableList()
        val albums = mutableListOf(results.albums.single().copy(matchingArtistNames = names))
        val artists = results.artists.toMutableList()
        reply(AppResult.Success(LibrarySearchResults(tracks, albums, artists)))
        names.clear(); tracks.clear(); albums.clear(); artists.clear()
        assertEquals(results, ready(vm).results)
    }

    @Test
    fun `restoration saves only raw input and waits for fresh locale and access`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = createVm(handle)
        bind(vm)
        send(vm, SearchIntent.AccessChecked(true))
        send(vm, SearchIntent.QueryChanged("  Ёлка  "))
        settle()
        reply(AppResult.Success(results))
        send(vm, SearchIntent.AccessChecked(false))
        val saved = handle.keys().associateWith { handle.get<Any?>(it) }
        assertEquals(mapOf("search.query" to "  Ёлка  "), saved)
        store.clear()
        runCurrent()
        val restored = createVm(SavedStateHandle(saved), localeTag = "en-US")
        assertEquals("  Ёлка  ", restored.state.value.rawQuery)
        assertEquals("en-US", restored.state.value.query?.localeTag)
        assertSame(SearchAccess.Unchecked, restored.state.value.access)
        assertSame(SearchBody.CheckingAccess, restored.state.value.body)
        bind(restored)
        settle()
        assertEquals(1, repository.calls.size)
        send(restored, SearchIntent.AccessChecked(true))
        settle()
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun `malformed saved text falls back to idle and clearing persists empty input`() = runTest(dispatcher) {
        assertSame(SearchBody.Idle, createVm(SavedStateHandle(mapOf("search.query" to 123))).state.value.body)
        val handle = SavedStateHandle(mapOf("search.query" to "  saved  "))
        val vm = createVm(handle)
        send(vm, SearchIntent.ClearQuery)
        assertEquals("", handle.get<String>("search.query"))
        val restored = createVm(SavedStateHandle(mapOf("search.query" to handle.get<String>("search.query"))))
        assertSame(SearchBody.Idle, restored.state.value.body)
    }

    @Test
    fun `last UI unbind cancels pending debounce and active observations without grace`() = runTest(dispatcher) {
        val vm = createVm()
        var binding = bind(vm)
        send(vm, SearchIntent.AccessChecked(true))
        send(vm, SearchIntent.QueryChanged("signal"))
        advanceTimeBy(200)
        binding.cancel()
        runCurrent()
        settle()
        assertTrue(repository.calls.isEmpty())
        binding = bind(vm)
        settle()
        assertEquals(1, repository.activeCount)
        binding.cancel()
        runCurrent()
        assertEquals(0, repository.activeCount)
        bind(vm)
        settle()
        assertEquals(2, repository.calls.size)
        assertEquals(1, repository.maxActiveCount)
    }

    @Test
    fun `multiple UI bindings share one observation until the last unbind`() = runTest(dispatcher) {
        val vm = createVm()
        val first = bind(vm)
        val second = bind(vm)
        send(vm, SearchIntent.AccessChecked(true))
        send(vm, SearchIntent.QueryChanged("signal"))
        settle()
        first.cancel()
        runCurrent()
        assertEquals(1, repository.activeCount)
        second.cancel()
        runCurrent()
        assertEquals(0, repository.activeCount)
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `cleared ViewModel cannot restart search or persist later input`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = createVm(handle)
        bind(vm)
        send(vm, SearchIntent.AccessChecked(true))
        send(vm, SearchIntent.QueryChanged("signal"))
        settle()
        store.clear()
        runCurrent()
        send(vm, SearchIntent.QueryChanged("after clear"))
        bind(vm)
        settle()
        assertEquals(0, repository.activeCount)
        assertEquals(1, repository.calls.size)
        assertEquals("signal", handle.get<String>("search.query"))
    }

    @Test
    fun `reentrant input changes persist the final text`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val vm = createVm(handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {
                if (it.rawQuery == "first") vm.onIntent(SearchIntent.QueryChanged("second"))
            }
        }
        send(vm, SearchIntent.QueryChanged("first"))
        assertEquals("second", vm.state.value.rawQuery)
        assertEquals("second", handle.get<String>("search.query"))
    }

    @Test
    fun `unexpected exceptions propagate with cleanup instead of becoming UI errors`() {
        val failure = IllegalArgumentException("broken provider")
        repository.failureOnCollect = failure
        val thrown = assertThrows(IllegalArgumentException::class.java) { runTest(dispatcher) { search() } }
        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === failure })
        assertEquals(0, repository.activeCount)
    }

    @Test
    fun `unsupported read errors retain their cause and fail the repository contract`() {
        val cause = IllegalStateException("unsupported lookup")
        repository.initialResult = AppResult.Failure(DataError.NotFound, cause)
        val thrown = assertThrows(IllegalStateException::class.java) { runTest(dispatcher) { search() } }
        assertTrue(thrown.message.orEmpty().contains("NotFound"))
        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === cause })
    }

    private fun createVm(handle: SavedStateHandle = SavedStateHandle(), localeTag: String = "ru-RU"): SearchViewModel =
        SearchViewModel(repository, handle, localeTag).also { store.put("search-${nextKey++}", it) }

    private fun TestScope.bind(vm: SearchViewModel): Job =
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }.also { runCurrent() }

    private fun TestScope.send(vm: SearchViewModel, intent: SearchIntent) {
        vm.onIntent(intent)
        runCurrent()
    }

    private fun TestScope.settle() {
        advanceTimeBy(250)
        runCurrent()
    }

    private fun TestScope.readyForInput(): SearchViewModel = createVm().also {
        bind(it)
        send(it, SearchIntent.AccessChecked(true))
    }

    private fun TestScope.search(text: String = "signal"): SearchViewModel = readyForInput().also {
        send(it, SearchIntent.QueryChanged(text))
        settle()
    }

    private fun TestScope.reply(result: SearchResult) {
        repository.subscriptions.last().respond(result)
        runCurrent()
    }

    private fun ready(vm: SearchViewModel): SearchBody.Ready = vm.state.value.body as SearchBody.Ready

    private val loadError = UiText.Resource(R.string.search_load_failed)
    private val refreshError = UiText.Resource(R.string.search_refresh_failed)
    private val emptyResults = LibrarySearchResults(emptyList(), emptyList(), emptyList())
    private val results = LibrarySearchResults(
        tracks = listOf(LibraryTrackSearchHit(
            LibraryTrack(TrackId("track:1"), "Signal", "Nullwave", "Transmission", 30_000), "Signal.m4a",
        )),
        albums = listOf(LibraryAlbumSearchHit(
            LibraryAlbum(AlbumId("album:1"), "Transmission", AlbumArtistCredit.Various, 12), listOf("Nullwave"),
        )),
        artists = listOf(LibraryArtist(ArtistId("artist:1"), "Nullwave", trackCount = 12, albumCount = 3)),
    )
}
