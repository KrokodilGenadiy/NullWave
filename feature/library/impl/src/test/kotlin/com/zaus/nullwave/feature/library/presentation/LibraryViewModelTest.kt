package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.access.AccessAction
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort
import com.zaus.nullwave.feature.library.api.model.track.TrackId
import com.zaus.nullwave.feature.library.impl.R
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeLibraryRepository()
    private var nextKey = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        repository.releaseCleanup()
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `state reads and intents without UI binding do not read the catalog`() = runTest(dispatcher) {
        val vm = createVm()
        assertEquals(LibraryState(LibraryTrackQuery("ru")), vm.state.value)
        send(vm, LibraryIntent.Retry)
        send(vm, LibraryIntent.Refresh)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        send(vm, LibraryIntent.SortChanged(LibraryTrackSort.Artist, SortDirection.Descending))
        assertTrue(repository.tracks.subscriptions.isEmpty())

        bind(vm)
        assertEquals(1, repository.tracks.subscriptions.size)
        assertEquals(vm.state.value.query, repository.tracks.subscriptions.single().query)
    }

    @Test
    fun `first success and observer updates replace the snapshot including a successful empty list`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        assertSame(LibraryBody.Loading, vm.state.value.body)

        reply(AppResult.Success(listOf(track)))
        assertEquals(LibraryBody.Ready(listOf(track)), vm.state.value.body)
        reply(AppResult.Success(emptyList()))
        assertEquals(LibraryBody.Ready(emptyList<LibraryTrack>()), vm.state.value.body)
        assertEquals(1, repository.tracks.subscriptions.size)
    }

    @Test
    fun `published state owns a copy of the returned list`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        val mutableResult = mutableListOf(track, otherTrack)
        reply(AppResult.Success(mutableResult))
        mutableResult.clear()
        assertEquals(listOf(track, otherTrack), ready(vm).items)
    }

    @Test
    fun `initial denied state is distinct from empty and retry cannot request access`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Failure(DataError.AccessDenied))
        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.state.value.body)
        assertTrue(repository.tracks.subscriptions.single().closed)

        send(vm, LibraryIntent.Retry)
        send(vm, LibraryIntent.Refresh)
        assertEquals(1, repository.tracks.subscriptions.size)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        assertSame(LibraryBody.Loading, vm.state.value.body)
        assertEquals(2, repository.tracks.subscriptions.size)
        reply(AppResult.Success(listOf(track)))
        assertEquals(LibraryBody.Ready(listOf(track)), vm.state.value.body)
    }

    @Test
    fun `initial read failure maps to resource text and retry recovers without duplicate reads`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Failure(DataError.Io, IllegalStateException("private provider detail")))
        assertEquals(LibraryBody.Failed(loadError), vm.state.value.body)

        send(vm, LibraryIntent.Retry)
        assertSame(LibraryBody.Loading, vm.state.value.body)
        send(vm, LibraryIntent.Retry)
        send(vm, LibraryIntent.Refresh)
        assertEquals(2, repository.tracks.subscriptions.size)
        reply(AppResult.Success(listOf(track)))
        assertEquals(LibraryBody.Ready(listOf(track)), vm.state.value.body)
    }

    @Test
    fun `retry recollects a flow that completed after an observation setup failure`() = runTest(dispatcher) {
        repository.tracks.initialResult = AppResult.Failure(DataError.Io)
        repository.tracks.completeAfterInitial = true
        val vm = createVm()
        bind(vm)
        assertEquals(LibraryBody.Failed(loadError), vm.state.value.body)
        assertTrue(repository.tracks.subscriptions.single().closed)

        repository.tracks.initialResult = AppResult.Success(listOf(track))
        repository.tracks.completeAfterInitial = false
        send(vm, LibraryIntent.Retry)
        assertEquals(LibraryBody.Ready(listOf(track)), vm.state.value.body)
        assertEquals(2, repository.tracks.subscriptions.size)
        assertEquals(1, repository.activeCount)
    }

    @Test
    fun `refresh keeps tracks and its inline failure can be retried`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Success(listOf(track)))
        send(vm, LibraryIntent.Retry)
        assertEquals(1, repository.tracks.subscriptions.size)
        send(vm, LibraryIntent.Refresh)
        assertEquals(LibraryBody.Ready(listOf(track), RefreshState.Running), vm.state.value.body)

        send(vm, LibraryIntent.Refresh)
        send(vm, LibraryIntent.Retry)
        assertEquals(2, repository.tracks.subscriptions.size)
        reply(AppResult.Failure(DataError.Io))
        assertEquals(LibraryBody.Ready(listOf(track), RefreshState.Failed(refreshError)), vm.state.value.body)
        send(vm, LibraryIntent.Retry)
        assertEquals(LibraryBody.Ready(listOf(track), RefreshState.Running), vm.state.value.body)
        reply(AppResult.Success(listOf(otherTrack)))
        assertEquals(LibraryBody.Ready(listOf(otherTrack)), vm.state.value.body)
        assertEquals(1, repository.maxActiveCount)
    }

    @Test
    fun `empty snapshot survives a failed refresh and refresh itself can recover`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Success(emptyList()))
        send(vm, LibraryIntent.Refresh)
        assertEquals(LibraryBody.Ready(emptyList<LibraryTrack>(), RefreshState.Running), vm.state.value.body)
        reply(AppResult.Failure(DataError.Io))
        assertEquals(LibraryBody.Ready(emptyList<LibraryTrack>(), RefreshState.Failed(refreshError)), vm.state.value.body)
        send(vm, LibraryIntent.Refresh)
        reply(AppResult.Success(listOf(track)))
        assertEquals(LibraryBody.Ready(listOf(track)), vm.state.value.body)
    }

    @Test
    fun `observer failure preserves data and later observation recovers without user retry`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Success(listOf(track)))
        reply(AppResult.Failure(DataError.Io))
        assertEquals(LibraryBody.Ready(listOf(track), RefreshState.Failed(refreshError)), vm.state.value.body)
        reply(AppResult.Success(listOf(otherTrack)))
        assertEquals(LibraryBody.Ready(listOf(otherTrack)), vm.state.value.body)
        assertEquals(1, repository.tracks.subscriptions.size)
    }

    @Test
    fun `repository access denial removes previously visible tracks`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Success(listOf(track)))
        reply(AppResult.Failure(DataError.AccessDenied))
        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), vm.state.value.body)
    }

    @Test
    fun `fresh denied checks retain their recovery hints when repository confirms denial`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Success(listOf(track)))
        for (action in AccessAction.entries) {
            send(vm, LibraryIntent.AccessChecked(LibraryAccess.Required(action)))
            assertEquals(LibraryBody.AccessRequired(action), vm.state.value.body)
            reply(AppResult.Failure(DataError.AccessDenied))
            assertEquals(LibraryBody.AccessRequired(action), vm.state.value.body)
        }
    }

    @Test
    fun `a fresh grant invalidates even an identical query or an already running first read`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        val first = repository.tracks.subscriptions.single()
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        assertTrue(first.closed)
        assertSame(LibraryBody.Loading, vm.state.value.body)
        reply(AppResult.Success(listOf(track)))

        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        assertEquals(LibraryBody.Ready(listOf(track), RefreshState.Running), vm.state.value.body)
        assertEquals(3, repository.tracks.subscriptions.size)
        assertTrue(repository.tracks.subscriptions.all { it.query == LibraryTrackQuery("ru") })
        reply(AppResult.Success(emptyList()))
        assertEquals(LibraryBody.Ready(emptyList<LibraryTrack>()), vm.state.value.body)
    }

    @Test
    fun `every query field restarts the source and old ordered data is cleared`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Success(listOf(track)))
        val cases = listOf(
            LibraryIntent.SortChanged(LibraryTrackSort.Artist, SortDirection.Ascending) to
                LibraryTrackQuery("ru", LibraryTrackSort.Artist),
            LibraryIntent.SortChanged(LibraryTrackSort.Artist, SortDirection.Descending) to
                LibraryTrackQuery("ru", LibraryTrackSort.Artist, SortDirection.Descending),
            LibraryIntent.LocaleChanged("en-US") to
                LibraryTrackQuery("en-US", LibraryTrackSort.Artist, SortDirection.Descending),
        )
        for ((intent, expected) in cases) {
            send(vm, intent)
            assertEquals(expected, repository.tracks.subscriptions.last().query)
            assertSame(LibraryBody.Loading, vm.state.value.body)
            reply(AppResult.Success(listOf(otherTrack)))
        }
        assertEquals(4, repository.tracks.subscriptions.size)
    }

    @Test
    fun `equal query inputs preserve ready data without requery`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Success(listOf(track)))
        val before = vm.state.value
        send(vm, LibraryIntent.SortChanged(LibraryTrackSort.Title, SortDirection.Ascending))
        send(vm, LibraryIntent.LocaleChanged("ru"))
        assertSame(before, vm.state.value)
        assertEquals(1, repository.tracks.subscriptions.size)
    }

    @Test
    fun `query changes keep the access gate and never reuse another query snapshot on failure`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Required(AccessAction.Request)))
        send(vm, LibraryIntent.SortChanged(LibraryTrackSort.Artist, SortDirection.Descending))
        assertEquals(LibraryBody.AccessRequired(AccessAction.Request), vm.state.value.body)

        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        reply(AppResult.Success(listOf(track)))
        send(vm, LibraryIntent.LocaleChanged("en"))
        reply(AppResult.Failure(DataError.Io))
        assertEquals(LibraryBody.Failed(loadError), vm.state.value.body)
    }

    @Test
    fun `late success during denied access cleanup cannot restore protected tracks`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Success(listOf(track)))
        val previous = repository.tracks.subscriptions.single()
        previous.delayCleanupWith(AppResult.Success(listOf(otherTrack)))
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Required(AccessAction.SettingsSuggested)))
        assertTrue(previous.cleanupStarted)
        assertFalse(previous.closed)
        assertEquals(1, repository.tracks.subscriptions.size)

        previous.cleanupGate!!.complete(Unit)
        runCurrent()
        assertTrue(previous.lateDeliveryAttempted)
        assertEquals(LibraryBody.AccessRequired(AccessAction.SettingsSuggested), vm.state.value.body)
        assertEquals(2, repository.tracks.subscriptions.size)
        assertEquals(1, repository.maxActiveCount)
    }

    @Test
    fun `retry ignores late result from the failed subscription while waiting for cleanup`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Failure(DataError.Io))
        val previous = repository.tracks.subscriptions.single()
        previous.delayCleanupWith(AppResult.Success(listOf(track)))
        send(vm, LibraryIntent.Retry)
        assertSame(LibraryBody.Loading, vm.state.value.body)
        previous.cleanupGate!!.complete(Unit)
        runCurrent()
        assertTrue(previous.lateDeliveryAttempted)
        assertSame(LibraryBody.Loading, vm.state.value.body)
        reply(AppResult.Success(emptyList()))
        assertEquals(LibraryBody.Ready(emptyList<LibraryTrack>()), vm.state.value.body)
    }

    @Test
    fun `A to B to A rejects the original A even though its query equals the current one`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        val original = repository.tracks.subscriptions.single()
        original.delayCleanupWith(AppResult.Success(listOf(track)))
        send(vm, LibraryIntent.SortChanged(LibraryTrackSort.Artist, SortDirection.Ascending))
        send(vm, LibraryIntent.SortChanged(LibraryTrackSort.Title, SortDirection.Ascending))
        original.cleanupGate!!.complete(Unit)
        runCurrent()

        assertTrue(original.lateDeliveryAttempted)
        assertSame(LibraryBody.Loading, vm.state.value.body)
        assertEquals(listOf(LibraryTrackQuery("ru"), LibraryTrackQuery("ru")), repository.tracks.subscriptions.map { it.query })
        reply(AppResult.Success(listOf(otherTrack)))
        assertEquals(LibraryBody.Ready(listOf(otherTrack)), vm.state.value.body)
    }

    @Test
    fun `clear releases the subscription and blocks late results and subsequent intents`() = runTest(dispatcher) {
        val vm = createVm()
        bind(vm)
        reply(AppResult.Success(listOf(track)))
        val previous = repository.tracks.subscriptions.single()
        previous.delayCleanupWith(AppResult.Failure(DataError.AccessDenied))
        val before = vm.state.value
        store.clear()
        runCurrent()
        previous.cleanupGate!!.complete(Unit)
        runCurrent()
        send(vm, LibraryIntent.Refresh)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Required(AccessAction.Request)))

        assertTrue(previous.closed)
        assertTrue(previous.lateDeliveryAttempted)
        assertEquals(before, vm.state.value)
        assertEquals(0, repository.activeCount)
        assertEquals(1, repository.tracks.subscriptions.size)
    }

    @Test
    fun `short absence shares the source but returning after grace reads a fresh snapshot`() = runTest(dispatcher) {
        val vm = createVm()
        val firstBinding = bind(vm)
        reply(AppResult.Success(listOf(track)))
        firstBinding.cancel()
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, repository.activeCount)
        val secondBinding = bind(vm)
        assertEquals(1, repository.tracks.subscriptions.size)

        secondBinding.cancel()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, repository.activeCount)
        assertEquals(LibraryBody.Ready(listOf(track)), vm.state.value.body)
        bind(vm)
        assertEquals(2, repository.tracks.subscriptions.size)
        reply(AppResult.Success(listOf(otherTrack)))
        assertEquals(LibraryBody.Ready(listOf(otherTrack)), vm.state.value.body)
    }

    @Test
    fun `refresh pending without binding completes on the next collection`() = runTest(dispatcher) {
        val vm = createVm()
        val binding = bind(vm)
        reply(AppResult.Success(listOf(track)))
        binding.cancel()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        send(vm, LibraryIntent.Refresh)
        assertEquals(LibraryBody.Ready(listOf(track), RefreshState.Running), vm.state.value.body)
        assertEquals(1, repository.tracks.subscriptions.size)

        bind(vm)
        reply(AppResult.Success(emptyList()))
        assertEquals(LibraryBody.Ready(emptyList<LibraryTrack>()), vm.state.value.body)
    }

    @Test
    fun `restoration keeps only sort and direction and uses the new configuration locale`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = createVm(handle)
        bind(vm)
        reply(AppResult.Success(listOf(track)))
        send(vm, LibraryIntent.SortChanged(LibraryTrackSort.RecentlyAdded, SortDirection.Descending))
        send(vm, LibraryIntent.LocaleChanged("de"))
        reply(AppResult.Failure(DataError.Io))
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Required(AccessAction.SettingsSuggested)))
        val saved = handle.keys().associateWith { handle.get<Any?>(it) }
        assertEquals(setOf("library.inputs"), saved.keys)
        assertTrue(saved.getValue("library.inputs") is String)
        store.clear()
        runCurrent()

        val restored = createVm(SavedStateHandle(saved), localeTag = "en-US")
        val expected = LibraryTrackQuery("en-US", LibraryTrackSort.RecentlyAdded, SortDirection.Descending)
        assertEquals(LibraryState(expected), restored.state.value)
        bind(restored)
        assertEquals(expected, repository.tracks.subscriptions.last().query)
        reply(AppResult.Failure(DataError.AccessDenied))
        assertEquals(LibraryBody.AccessRequired(AccessAction.CheckAgain), restored.state.value.body)
    }

    @Test
    fun `unknown or malformed saved inputs fall back independently`() = runTest(dispatcher) {
        val malformed = createVm(SavedStateHandle(mapOf("library.sort" to 123, "library.direction" to "Removed")))
        assertEquals(LibraryTrackQuery("ru"), malformed.state.value.query)
        val partial = createVm(SavedStateHandle(mapOf("library.sort" to "Artist", "library.direction" to false)))
        assertEquals(LibraryTrackQuery("ru", LibraryTrackSort.Artist), partial.state.value.query)
    }

    @Test
    fun `versioned inputs restore valid enum fields and reject unsupported versions without legacy fallback`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("library.inputs" to
            """{"version":1,"payload":{"tab":"Artists","sort":"Removed","direction":"Descending","albumDirection":"Removed","artistDirection":"Descending"}}"""))
        val vm = createVm(handle, localeTag = "sv-SE")
        assertEquals(LibraryTab.Artists, vm.state.value.selectedTab)
        assertEquals(LibraryTrackQuery("sv-SE", LibraryTrackSort.Title, SortDirection.Descending), vm.state.value.query)
        assertEquals(SortDirection.Ascending, vm.state.value.albumQuery.direction)
        assertEquals(SortDirection.Descending, vm.state.value.artistQuery.direction)
        assertSame(LibraryBody.Loading, vm.state.value.selectedBody)
        for (raw in listOf<Any>(42, "broken", """{"version":99,"payload":{"tab":"Albums"}}""")) {
            val restored = createVm(SavedStateHandle(mapOf("library.inputs" to raw, "library.tab" to "Artists")))
            assertEquals(LibraryTab.Songs, restored.state.value.selectedTab)
        }
    }

    @Test
    fun `immediate cold results are never overwritten by loading after restart`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        repository.tracks.initialResult = AppResult.Success(listOf(track))
        val vm = createVm()
        bind(vm)
        assertEquals(LibraryBody.Ready(listOf(track)), vm.state.value.body)

        repository.tracks.initialResult = AppResult.Failure(DataError.Io)
        send(vm, LibraryIntent.Refresh)
        assertEquals(LibraryBody.Ready(listOf(track), RefreshState.Failed(refreshError)), vm.state.value.body)
        repository.tracks.initialResult = AppResult.Success(emptyList())
        send(vm, LibraryIntent.Retry)
        assertEquals(LibraryBody.Ready(emptyList<LibraryTrack>()), vm.state.value.body)

        repository.tracks.initialResult = AppResult.Failure(DataError.AccessDenied)
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Required(AccessAction.Request)))
        assertEquals(LibraryBody.AccessRequired(AccessAction.Request), vm.state.value.body)
        repository.tracks.initialResult = AppResult.Success(listOf(otherTrack))
        send(vm, LibraryIntent.AccessChecked(LibraryAccess.Granted))
        assertEquals(LibraryBody.Ready(listOf(otherTrack)), vm.state.value.body)
        send(vm, LibraryIntent.SortChanged(LibraryTrackSort.Artist, SortDirection.Descending))
        assertEquals(LibraryBody.Ready(listOf(otherTrack)), vm.state.value.body)
    }

    @Test
    fun `reentrant sort selection persists the final state rather than the outer intent`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val vm = createVm(handle)
        val ui = vm.uiApi()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {
                if (it.query.sort == LibraryTrackSort.Artist) {
                    ui.onIntent(LibraryIntent.SortChanged(LibraryTrackSort.RecentlyAdded, SortDirection.Descending))
                }
            }
        }
        send(vm, LibraryIntent.SortChanged(LibraryTrackSort.Artist, SortDirection.Ascending))
        assertEquals(LibraryTrackSort.RecentlyAdded, vm.state.value.query.sort)
        val saved = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = createVm(SavedStateHandle(saved))
        assertEquals(vm.state.value.query, restored.state.value.query)
    }

    @Test
    fun `unexpected repository exceptions propagate instead of becoming a retryable UI failure`() {
        val failure = IllegalArgumentException("bad projection")
        repository.tracks.failureOnCollect = failure
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            runTest(dispatcher) {
                val vm = createVm()
                bind(vm)
                assertSame(LibraryBody.Loading, vm.state.value.body)
            }
        }
        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === failure })
        assertEquals(0, repository.activeCount)
    }

    @Test
    fun `unsupported read error codes fail the repository contract and retain the cause`() {
        val cause = IllegalStateException("unexpected lookup failure")
        repository.tracks.initialResult = AppResult.Failure(DataError.NotFound, cause)
        val thrown = assertThrows(IllegalStateException::class.java) {
            runTest(dispatcher) { bind(createVm()) }
        }
        assertTrue(thrown.message.orEmpty().contains("NotFound"))
        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === cause })
    }

    private fun createVm(handle: SavedStateHandle = SavedStateHandle(), localeTag: String = "ru"): LibraryViewModel =
        LibraryViewModel(repository, handle, localeTag).also { store.put("library-${nextKey++}", it) }

    private fun TestScope.bind(vm: LibraryViewModel): Job =
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }.also { runCurrent() }

    private suspend fun TestScope.send(vm: LibraryViewModel, intent: LibraryIntent) {
        vm.uiApi().onIntent(intent)
        runCurrent()
    }

    private fun TestScope.reply(result: LibraryResult) {
        repository.tracks.subscriptions.last().respond(result)
        runCurrent()
    }

    private fun ready(vm: LibraryViewModel): LibraryBody.Ready<LibraryTrack> = vm.state.value.body as LibraryBody.Ready<LibraryTrack>

    private val loadError = UiText.Resource(R.string.library_load_failed)
    private val refreshError = UiText.Resource(R.string.library_refresh_failed)
    private val track = LibraryTrack(TrackId("primary:1"), "Low Orbit", "Nullwave", null, 180_000)
    private val otherTrack = LibraryTrack(TrackId("sd-card:1"), "Сигнал", null, null, null)
}
