package com.zaus.nullwave.feature.library.di

import com.zaus.nullwave.core.mvi.testing.MviTestMain
import com.zaus.nullwave.core.mvi.testing.bind
import com.zaus.nullwave.core.mvi.testing.send
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort
import com.zaus.nullwave.feature.library.api.repository.LibraryCatalogRepository
import com.zaus.nullwave.feature.library.api.repository.LibraryAlbumDetailsRepository
import com.zaus.nullwave.feature.library.api.repository.LibraryArtistDetailsRepository
import com.zaus.nullwave.feature.library.data.repository.MediaStoreLibraryRepository
import com.zaus.nullwave.feature.library.data.source.FakeMediaStoreAudioSource
import com.zaus.nullwave.feature.library.data.source.audioRow
import com.zaus.nullwave.feature.library.presentation.LibraryBody
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.library.presentation.LibraryTab
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import com.zaus.nullwave.feature.library.presentation.albumdetails.AlbumDetailsBody
import com.zaus.nullwave.feature.library.presentation.albumdetails.AlbumDetailsIntent
import com.zaus.nullwave.feature.library.presentation.albumdetails.AlbumDetailsViewModel
import com.zaus.nullwave.feature.library.presentation.artistdetails.ArtistDetailsBody
import com.zaus.nullwave.feature.library.presentation.artistdetails.ArtistDetailsIntent
import com.zaus.nullwave.feature.library.presentation.artistdetails.ArtistDetailsViewModel
import com.zaus.nullwave.feature.library.testing.IdlePlaybackController
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryGraphTest {
    private val mvi = MviTestMain()
    private val dispatcher = mvi.dispatcher
    private val store = mvi.store

    @Before
    fun setUp() = mvi.setUp()

    @After
    fun tearDown() {
        mvi.tearDown()
    }

    @Test
    fun `generated graph creates entry owned Library ViewModels with the repository and saved state`() = runTest(dispatcher) {
        val source = FakeMediaStoreAudioSource().apply { read = { listOf(audioRow()) } }
        val repository = MediaStoreLibraryRepository(source, dispatcher)
        val graph = createGraphFactory<LibraryTestGraph.Factory>().create(repository, repository, repository, "ru")
        val factory = graph.assistedFactories.getValue(LibraryViewModel::class).invoke() as LibraryViewModel.Factory
        val first = factory.create(SavedStateHandle(mapOf("library.sort" to "Artist")))
        val second = factory.create(SavedStateHandle())
        store.put("first", first)
        store.put("second", second)
        assertNotSame(first, second)
        assertEquals(LibraryTrackSort.Artist, first.snapshot.value.state.query.sort)
        assertEquals(LibraryTrackSort.Title, second.snapshot.value.state.query.sort)
        assertEquals("ru", first.snapshot.value.state.query.localeTag)
        assertEquals(0, source.reads)
        val firstUi = first.uiApi()

        backgroundScope.launch { first.withUiBinding { awaitCancellation() } }
        backgroundScope.launch { second.withUiBinding { awaitCancellation() } }
        runCurrent()
        assertEquals(2, source.activeObservers)
        assertEquals("Low Orbit", (first.snapshot.value.state.body as LibraryBody.Ready<MediaTrack>).items.single().title)
        firstUi.onIntent(LibraryIntent.TabSelected(LibraryTab.Albums))
        runCurrent()
        assertEquals("Signals", (first.snapshot.value.state.albums as LibraryBody.Ready<LibraryAlbum>).items.single().title)
        assertEquals(LibraryTab.Songs, second.snapshot.value.state.selectedTab)
        assertEquals(2, source.activeObservers)
        store.clear()
        runCurrent()
        assertEquals(0, source.activeObservers)
    }

    @Test
    fun `assisted factory observes the album of each navigation entry`() = runTest(dispatcher) {
        val source = FakeMediaStoreAudioSource().apply {
            read = { listOf(audioRow().copy(albumId = 7), audioRow(id = 2).copy(albumId = 8)) }
        }
        val repository = MediaStoreLibraryRepository(source, dispatcher)
        val graph = createGraphFactory<LibraryTestGraph.Factory>().create(repository, repository, repository, "ru")
        val factory = graph.metroViewModelFactory.createManuallyAssistedFactory(AlbumDetailsViewModel.Factory::class)()
        for (id in listOf(AlbumId("external_primary:7"), AlbumId("external_primary:8"))) {
            val vm = mvi.own(factory.create(id))
            assertEquals("ru", vm.snapshot.value.state.localeTag)
            bind(vm)
            send(vm, AlbumDetailsIntent.AccessChecked(LibraryAccess.Granted))
            val body = vm.snapshot.value.state.body as AlbumDetailsBody.Ready
            assertEquals(id, body.details.album.id)
        }
        assertEquals(2, source.activeObservers)
    }

    @Test
    fun `assisted factory gives each artist entry its own identity and access state`() = runTest(dispatcher) {
        val source = FakeMediaStoreAudioSource().apply {
            read = { listOf(audioRow().copy(artistId = 7), audioRow(id = 2).copy(artistId = 8)) }
        }
        val repository = MediaStoreLibraryRepository(source, dispatcher)
        val graph = createGraphFactory<LibraryTestGraph.Factory>().create(repository, repository, repository, "ru")
        val factory = graph.metroViewModelFactory.createManuallyAssistedFactory(ArtistDetailsViewModel.Factory::class)()
        val first = mvi.own(factory.create(ArtistId("external_primary:7")))
        val second = mvi.own(factory.create(ArtistId("external_primary:8")))
        assertNotSame(first, second)
        bind(first)
        bind(second)
        assertEquals(0, source.reads)
        send(first, ArtistDetailsIntent.AccessChecked(LibraryAccess.Granted))
        assertEquals(ArtistDetailsBody.CheckingAccess, second.snapshot.value.state.body)
        send(second, ArtistDetailsIntent.AccessChecked(LibraryAccess.Granted))
        for ((vm, id) in listOf(first to "external_primary:7", second to "external_primary:8")) {
            assertEquals("ru", vm.snapshot.value.state.localeTag)
            assertEquals(ArtistId(id), (vm.snapshot.value.state.body as ArtistDetailsBody.Ready).details.artist.id)
        }
        assertEquals(2, source.activeObservers)
        store.clear()
        runCurrent()
        assertEquals(0, source.activeObservers)
    }
}

@DependencyGraph(
    AppScope::class,
    excludes = [LibraryPlatformBindings::class, LibraryRepositoryBindings::class, LibraryEntries::class],
)
internal interface LibraryTestGraph : ViewModelGraph {
    val assistedFactories: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>

    @Provides
    fun playback(): PlaybackController = IdlePlaybackController

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides repository: LibraryCatalogRepository,
            @Provides albumDetails: LibraryAlbumDetailsRepository,
            @Provides artistDetails: LibraryArtistDetailsRepository,
            @Provides @LibraryInitialLocale locale: String,
        ): LibraryTestGraph
    }
}
