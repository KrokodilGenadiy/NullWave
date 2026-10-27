package com.zaus.nullwave.feature.library.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Both group tabs exercise the real DSL, lifetime and reducers, not a mocked StateFlow. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
internal class LibraryTabsViewModelTest(private val tab: LibraryTab) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun tabs() = listOf(arrayOf(LibraryTab.Albums), arrayOf(LibraryTab.Artists))
    }

    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeLibraryRepository()
    private var nextKey = 0
    private val collection get() = when (tab) {
        LibraryTab.Albums -> repository.albums
        LibraryTab.Artists -> repository.artists
        LibraryTab.Songs -> error("Songs has its own regression suite")
    }
    private val album = LibraryAlbum(AlbumId("primary:8"), "Signals", AlbumArtistCredit.Named("Nullwave"), 2, null)
    private val artist = LibraryArtist(ArtistId("primary:4"), "Nullwave", 2, 1)
    private val track = MediaTrack(TrackId("primary:1"), "Low Orbit", "Nullwave", "Signals", 180_000)
    private val expectedItems get() = if (tab == LibraryTab.Albums) listOf(album) else listOf(artist)
    private val loadError = UiText.Resource(R.string.library_load_failed)
    private val refreshError = UiText.Resource(R.string.library_refresh_failed)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        repository.releaseCleanup()
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `selection before binding reads only the selected tab and repeated selection is a no-op`() = runTest(dispatcher) {
        val vm = createVm()
        send(vm, LibraryIntent.TabSelected(tab))
        send(vm, sortIntent(SortDirection.Descending))
        assertTrue(repository.requests.isEmpty())
        assertSame(LibraryBody.Loading, vm.snapshot.value.state.selectedBody)
        bind(vm)
        assertEquals(1, repository.requests.size)
        assertTrue(repository.tracks.subscriptions.isEmpty())
        assertEquals(1, collection.subscriptions.size)
        success()
        val ready = vm.snapshot.value.state.selectedBody
        send(vm, LibraryIntent.TabSelected(tab))
        assertEquals(ready, vm.snapshot.value.state.selectedBody)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun `group snapshots own their lists and empty success remains a ready state`() = runTest(dispatcher) {
        val vm = selectedVm()
        when (tab) {
            LibraryTab.Albums -> {
                val items = mutableListOf(album)
                repository.albums.subscriptions.last().respond(AppResult.Success(items))
                runCurrent()
                items.clear()
            }
            LibraryTab.Artists -> {
                val items = mutableListOf(artist)
                repository.artists.subscriptions.last().respond(AppResult.Success(items))
                runCurrent()
                items.clear()
            }
            else -> error("Unsupported case")
        }
        assertEquals(expectedItems, ready(vm).items)
        success(empty = true)
        assertTrue(ready(vm).items.isEmpty())
        assertEquals(RefreshState.Idle, ready(vm).refresh)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun `setup failure can finish the flow and retry creates one fresh subscription`() = runTest(dispatcher) {
        collection.initialResult = AppResult.Failure(DataError.Io)
        collection.completeAfterInitial = true
        val vm = selectedVm()
        assertEquals(LibraryBody.Failed(loadError), vm.snapshot.value.state.selectedBody)
        assertTrue(collection.subscriptions.single().closed)
        collection.initialResult = null
        collection.completeAfterInitial = false
        send(vm, LibraryIntent.Retry)
        assertSame(LibraryBody.Loading, vm.snapshot.value.state.selectedBody)
        send(vm, LibraryIntent.Retry)
        send(vm, LibraryIntent.Refresh)
        assertEquals(2, collection.subscriptions.size)
        success()
        assertEquals(expectedItems, ready(vm).items)
    }

    @Test
    fun `refresh retains data and empty snapshots while retry and observer recovery clear inline errors`() = runTest(dispatcher) {
        val vm = selectedVm()
        success()
        send(vm, LibraryIntent.Refresh)
        assertEquals(expectedItems, ready(vm).items)
        assertEquals(RefreshState.Running, ready(vm).refresh)
        send(vm, LibraryIntent.Refresh)
        send(vm, LibraryIntent.Retry)
        assertEquals(2, collection.subscriptions.size)
        failure(DataError.Io)
        assertEquals(expectedItems, ready(vm).items)
        assertEquals(RefreshState.Failed(refreshError), ready(vm).refresh)
        send(vm, LibraryIntent.Retry)
        success(empty = true)
        send(vm, LibraryIntent.Refresh)
        failure(DataError.Io)
        assertTrue(ready(vm).items.isEmpty())
        assertEquals(RefreshState.Failed(refreshError), ready(vm).refresh)
        success()
        assertEquals(expectedItems, ready(vm).items)
        assertEquals(RefreshState.Idle, ready(vm).refresh)
        assertEquals(4, collection.subscriptions.size)
        assertEquals(1, repository.maxActiveCount)
    }

    @Test
    fun `repository access denial clears every body and a fresh grant retries the selected tab`() = runTest(dispatcher) {
        val vm = selectedVm()
        success()
        failure(DataError.AccessDenied)
        assertAllGated(vm, AccessAction.CheckAgain)
        assertEquals(0, repository.activeCount)
        send(vm, LibraryIntent.Refresh)
        send(vm, LibraryIntent.Retry)
        assertEquals(1, collection.subscriptions.size)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        assertSame(LibraryBody.Loading, vm.snapshot.value.state.selectedBody)
        assertEquals(2, collection.subscriptions.size)
        success()
        assertEquals(expectedItems, ready(vm).items)
    }

    @Test
    fun `OS gate survives selection and sort changes until fresh access succeeds`() = runTest(dispatcher) {
        val vm = createVm()
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Required(AccessAction.SettingsSuggested)))
        send(vm, LibraryIntent.TabSelected(tab))
        send(vm, sortIntent(SortDirection.Descending))
        assertAllGated(vm, AccessAction.SettingsSuggested)
        assertTrue(repository.requests.isEmpty())
        collection.initialResult = AppResult.Failure(DataError.AccessDenied)
        bind(vm)
        assertAllGated(vm, AccessAction.SettingsSuggested)
        collection.initialResult = null
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        success()
        assertEquals(expectedItems, ready(vm).items)
    }

    @Test
    fun `sort inputs are independent and changing an inactive query does not restart the selected flow`() = runTest(dispatcher) {
        val vm = selectedVm()
        success()
        send(vm, LibraryIntent.SortChanged(LibraryTrackSort.RecentlyAdded, SortDirection.Descending))
        val otherSort = if (tab == LibraryTab.Albums) LibraryIntent.ArtistSortChanged(SortDirection.Descending)
            else LibraryIntent.AlbumSortChanged(SortDirection.Descending)
        send(vm, otherSort)
        assertEquals(1, repository.requests.size)
        assertEquals(expectedItems, ready(vm).items)
        send(vm, sortIntent(SortDirection.Descending))
        assertSame(LibraryBody.Loading, vm.snapshot.value.state.selectedBody)
        assertTrue(collection.subscriptions.first().closed)
        assertEquals(2, repository.requests.size)
        assertEquals(SortDirection.Descending, vm.snapshot.value.state.albumQuery.direction)
        assertEquals(SortDirection.Descending, vm.snapshot.value.state.artistQuery.direction)
        send(vm, sortIntent(SortDirection.Descending))
        assertEquals(2, repository.requests.size)
        send(vm, LibraryIntent.TabSelected(LibraryTab.Songs))
        assertEquals(LibraryTrackSort.RecentlyAdded, repository.tracks.subscriptions.single().query.sort)
        assertEquals(SortDirection.Descending, repository.tracks.subscriptions.single().query.direction)
        assertEquals(1, repository.maxActiveCount)
    }

    @Test
    fun `locale updates all queries but collects only the selected request`() = runTest(dispatcher) {
        val vm = selectedVm()
        success()
        send(vm, LibraryIntent.LocaleChanged("de-DE"))
        val state = vm.snapshot.value.state
        assertEquals("de-DE", state.query.localeTag)
        assertEquals("de-DE", state.albumQuery.localeTag)
        assertEquals("de-DE", state.artistQuery.localeTag)
        assertSame(LibraryBody.Loading, state.selectedBody)
        assertEquals(2, repository.requests.size)
        assertEquals(if (tab == LibraryTab.Albums) state.albumQuery else state.artistQuery,
            collection.subscriptions.last().query)
        send(vm, LibraryIntent.LocaleChanged("de-DE"))
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun `switching A to Songs to A fences late results and waits for old cleanup`() = runTest(dispatcher) {
        val vm = selectedVm()
        success()
        val old = collection.subscriptions.single()
        when (tab) {
            LibraryTab.Albums -> repository.albums.subscriptions.single().delayCleanupWith(AppResult.Success(listOf(album)))
            LibraryTab.Artists -> repository.artists.subscriptions.single().delayCleanupWith(AppResult.Success(listOf(artist)))
            else -> error("Unsupported case")
        }
        send(vm, LibraryIntent.TabSelected(LibraryTab.Songs))
        assertTrue(old.cleanupStarted)
        assertFalse(old.closed)
        assertTrue(repository.tracks.subscriptions.isEmpty())
        send(vm, LibraryIntent.TabSelected(tab))
        assertSame(LibraryBody.Loading, vm.snapshot.value.state.selectedBody)
        old.cleanupGate!!.complete(Unit)
        runCurrent()
        assertTrue(old.lateDeliveryAttempted)
        assertTrue(old.closed)
        assertEquals(2, collection.subscriptions.size)
        assertSame(LibraryBody.Loading, vm.snapshot.value.state.selectedBody)
        assertSame(LibraryBody.Loading, vm.snapshot.value.state.body)
        success(empty = true)
        assertTrue(ready(vm).items.isEmpty())
        assertEquals(1, repository.maxActiveCount)
    }

    @Test
    fun `binding stop and clear release the selected subscription and rebind reads fresh data`() = runTest(dispatcher) {
        val vm = createVm()
        send(vm, LibraryIntent.TabSelected(tab))
        val binding = bind(vm)
        success()
        binding.cancel()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, repository.activeCount)
        bind(vm)
        assertEquals(2, collection.subscriptions.size)
        success(empty = true)
        assertTrue(ready(vm).items.isEmpty())
        store.clear()
        runCurrent()
        assertEquals(0, repository.activeCount)
        val lastState = vm.snapshot.value.state
        send(vm, LibraryIntent.TabSelected(LibraryTab.Songs))
        assertEquals(lastState, vm.snapshot.value.state)
    }

    @Test
    fun `late access denial from the previous tab cannot gate the newly selected catalog`() = runTest(dispatcher) {
        val vm = selectedVm()
        success()
        val old = collection.subscriptions.single()
        old.delayCleanupWith(AppResult.Failure(DataError.AccessDenied))
        send(vm, LibraryIntent.TabSelected(LibraryTab.Songs))
        assertTrue(old.cleanupStarted)
        old.cleanupGate!!.complete(Unit)
        runCurrent()
        assertTrue(old.lateDeliveryAttempted)
        assertEquals(LibraryTab.Songs, vm.snapshot.value.state.selectedTab)
        assertSame(LibraryBody.Loading, vm.snapshot.value.state.selectedBody)
        repository.tracks.subscriptions.single().respond(AppResult.Success(listOf(track)))
        runCurrent()
        assertEquals(listOf(track), ready(vm).items)
        assertEquals(1, repository.maxActiveCount)
    }

    @Test
    fun `restoration keeps tab and all sorts but no snapshots access or stale locale`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = createVm(handle)
        send(vm, LibraryIntent.SortChanged(LibraryTrackSort.Artist, SortDirection.Descending))
        send(vm, LibraryIntent.AlbumSortChanged(SortDirection.Descending))
        send(vm, LibraryIntent.ArtistSortChanged(SortDirection.Descending))
        send(vm, LibraryIntent.TabSelected(tab))
        bind(vm)
        success()
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Required(AccessAction.SettingsSuggested)))
        val saved = handle.keys().associateWith { handle.get<Any?>(it) }
        assertEquals(setOf("library.inputs"), saved.keys)
        store.clear()
        runCurrent()
        val restored = createVm(SavedStateHandle(saved), "en-US")
        val state = restored.snapshot.value.state
        assertEquals(tab, state.selectedTab)
        assertSame(LibraryBody.Loading, state.selectedBody)
        assertEquals("en-US", state.query.localeTag)
        assertEquals("en-US", state.albumQuery.localeTag)
        assertEquals("en-US", state.artistQuery.localeTag)
        assertEquals(LibraryTrackSort.Artist, state.query.sort)
        assertEquals(SortDirection.Descending, state.query.direction)
        assertEquals(SortDirection.Descending, state.albumQuery.direction)
        assertEquals(SortDirection.Descending, state.artistQuery.direction)
        bind(restored)
        assertEquals(1, repository.activeCount)
        assertTrue(repository.tracks.subscriptions.isEmpty())
        failure(DataError.AccessDenied)
        assertAllGated(restored, AccessAction.CheckAgain)
    }

    @Test
    fun `malformed saved values fall back independently without discarding valid selections`() = runTest(dispatcher) {
        val vm = createVm(SavedStateHandle(mapOf(
            "library.tab" to tab.name, "library.albums.direction" to 123,
            "library.artists.direction" to "Descending", "library.sort" to false,
        )))
        assertEquals(tab, vm.snapshot.value.state.selectedTab)
        assertEquals(SortDirection.Ascending, vm.snapshot.value.state.albumQuery.direction)
        assertEquals(SortDirection.Descending, vm.snapshot.value.state.artistQuery.direction)
        assertEquals(LibraryTrackSort.Title, vm.snapshot.value.state.query.sort)
        val unknown = createVm(SavedStateHandle(mapOf("library.tab" to "Removed", "library.direction" to "Descending")))
        assertEquals(LibraryTab.Songs, unknown.snapshot.value.state.selectedTab)
        assertEquals(SortDirection.Descending, unknown.snapshot.value.state.query.direction)
    }

    @Test
    fun `synchronous group results win over loading during selection sort refresh and grant`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        repository.tracks.initialResult = AppResult.Success(listOf(track))
        repository.albums.initialResult = AppResult.Success(listOf(album))
        repository.artists.initialResult = AppResult.Success(listOf(artist))
        val vm = createVm()
        bind(vm)
        send(vm, LibraryIntent.TabSelected(tab))
        assertEquals(expectedItems, ready(vm).items)
        send(vm, sortIntent(SortDirection.Descending))
        assertEquals(expectedItems, ready(vm).items)
        send(vm, LibraryIntent.Refresh)
        assertEquals(RefreshState.Idle, ready(vm).refresh)
        collection.initialResult = AppResult.Failure(DataError.AccessDenied)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Required(AccessAction.Request)))
        assertAllGated(vm, AccessAction.Request)
        repository.albums.initialResult = AppResult.Success(listOf(album))
        repository.artists.initialResult = AppResult.Success(listOf(artist))
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        assertEquals(expectedItems, ready(vm).items)
        assertEquals(1, repository.maxActiveCount)
    }

    @Test
    fun `reentrant tab and sort changes persist the final choices`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val vm = createVm(handle)
        val ui = vm.uiApi()
        send(vm, LibraryIntent.TabSelected(tab))
        var redirected = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.snapshot.map { it.state }.distinctUntilChanged().collect { state ->
                val direction = if (tab == LibraryTab.Albums) state.albumQuery.direction else state.artistQuery.direction
                if (!redirected && direction == SortDirection.Descending) {
                    redirected = true
                    ui.onIntent(sortIntent(SortDirection.Ascending))
                    ui.onIntent(LibraryIntent.TabSelected(LibraryTab.Songs))
                }
            }
        }
        send(vm, sortIntent(SortDirection.Descending))
        val saved = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = createVm(SavedStateHandle(saved))
        assertTrue(redirected)
        assertEquals(LibraryTab.Songs, restored.snapshot.value.state.selectedTab)
        assertEquals(vm.snapshot.value.state.albumQuery, restored.snapshot.value.state.albumQuery)
        assertEquals(vm.snapshot.value.state.artistQuery, restored.snapshot.value.state.artistQuery)
        assertEquals(SortDirection.Ascending, restored.snapshot.value.state.albumQuery.direction)
        assertEquals(SortDirection.Ascending, restored.snapshot.value.state.artistQuery.direction)
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun `unsupported group errors propagate with diagnostics instead of becoming empty or retryable`() {
        val cause = IllegalArgumentException("unexpected group lookup")
        collection.initialResult = AppResult.Failure(DataError.NotFound, cause)
        val thrown = assertThrows(IllegalStateException::class.java) {
            runTest(dispatcher) { selectedVm() }
        }
        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === cause })
        assertEquals(0, repository.activeCount)
    }

    private fun createVm(handle: SavedStateHandle = SavedStateHandle(), locale: String = "ru"): LibraryViewModel =
        LibraryViewModel(repository, handle, locale).also { store.put("tabs-${nextKey++}", it) }

    private suspend fun TestScope.selectedVm(): LibraryViewModel = createVm().also {
        send(it, LibraryIntent.TabSelected(tab))
        bind(it)
    }

    private fun TestScope.bind(vm: LibraryViewModel): Job =
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }.also { runCurrent() }

    private suspend fun TestScope.send(vm: LibraryViewModel, intent: LibraryIntent) {
        vm.uiApi().onIntent(intent)
        runCurrent()
    }

    private fun TestScope.success(empty: Boolean = false) {
        when (tab) {
            LibraryTab.Albums -> repository.albums.subscriptions.last().respond(AppResult.Success(if (empty) emptyList() else listOf(album)))
            LibraryTab.Artists -> repository.artists.subscriptions.last().respond(AppResult.Success(if (empty) emptyList() else listOf(artist)))
            else -> error("Unsupported case")
        }
        runCurrent()
    }

    private fun TestScope.failure(error: DataError) {
        collection.subscriptions.last().respond(AppResult.Failure(error))
        runCurrent()
    }

    private fun sortIntent(direction: SortDirection): LibraryIntent =
        if (tab == LibraryTab.Albums) LibraryIntent.AlbumSortChanged(direction)
        else LibraryIntent.ArtistSortChanged(direction)

    private fun ready(vm: LibraryViewModel) = vm.snapshot.value.state.selectedBody as LibraryBody.Ready<*>

    private fun assertAllGated(vm: LibraryViewModel, action: AccessAction) {
        val gate = LibraryBody.AccessRequired(action)
        assertEquals(gate, vm.snapshot.value.state.body)
        assertEquals(gate, vm.snapshot.value.state.albums)
        assertEquals(gate, vm.snapshot.value.state.artists)
    }
}
