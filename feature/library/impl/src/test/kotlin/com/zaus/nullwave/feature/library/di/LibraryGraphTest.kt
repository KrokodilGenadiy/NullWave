package com.zaus.nullwave.feature.library.di

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort
import com.zaus.nullwave.feature.library.api.repository.LibraryRepository
import com.zaus.nullwave.feature.library.data.repository.MediaStoreLibraryRepository
import com.zaus.nullwave.feature.library.data.source.audioRow
import com.zaus.nullwave.feature.library.data.source.FakeMediaStoreAudioSource
import com.zaus.nullwave.feature.library.presentation.LibraryBody
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlin.reflect.KClass
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryGraphTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `generated graph creates entry owned Library ViewModels with the repository and saved state`() = runTest(dispatcher) {
        val source = FakeMediaStoreAudioSource().apply { read = { listOf(audioRow()) } }
        val repository = MediaStoreLibraryRepository(source, dispatcher)
        val graph = createGraphFactory<LibraryTestGraph.Factory>().create(repository, "ru")
        val factory = graph.assistedFactories.getValue(LibraryViewModel::class).invoke() as LibraryViewModel.Factory
        val first = factory.create(SavedStateHandle(mapOf("library.sort" to "Artist")))
        val second = factory.create(SavedStateHandle())
        store.put("first", first)
        store.put("second", second)
        assertNotSame(first, second)
        assertEquals(LibraryTrackSort.Artist, first.state.value.query.sort)
        assertEquals(LibraryTrackSort.Title, second.state.value.query.sort)
        assertEquals("ru", first.state.value.query.localeTag)
        assertEquals(0, source.reads)

        backgroundScope.launch { first.withUiBinding { awaitCancellation() } }
        backgroundScope.launch { second.withUiBinding { awaitCancellation() } }
        runCurrent()
        assertEquals(2, source.activeObservers)
        assertEquals("Low Orbit", (first.state.value.body as LibraryBody.Ready).tracks.single().title)
        store.clear()
        runCurrent()
        assertEquals(0, source.activeObservers)
    }
}

@DependencyGraph(
    AppScope::class,
    excludes = [LibraryPlatformBindings::class, LibraryEntries::class],
)
internal interface LibraryTestGraph : ViewModelGraph {
    val assistedFactories: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides repository: LibraryRepository,
            @Provides @LibraryInitialLocale locale: String,
        ): LibraryTestGraph
    }
}
