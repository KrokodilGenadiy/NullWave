package com.zaus.nullwave.feature.search.di

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults
import com.zaus.nullwave.feature.library.api.repository.LibrarySearchRepository
import com.zaus.nullwave.feature.search.presentation.FakeSearchRepository
import com.zaus.nullwave.feature.search.presentation.SearchAccess
import com.zaus.nullwave.feature.search.presentation.SearchBody
import com.zaus.nullwave.feature.search.presentation.SearchIntent
import com.zaus.nullwave.feature.search.presentation.SearchViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchGraphTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `generated factory restores raw input and creates independently owned instances`() = runTest(dispatcher) {
        val repository = FakeSearchRepository().apply {
            initialResult = AppResult.Success(LibrarySearchResults(emptyList(), emptyList(), emptyList()))
        }
        val graph = createGraphFactory<SearchTestGraph.Factory>().create(repository, "ru")
        val factory = graph.factories.getValue(SearchViewModel::class).invoke() as SearchViewModel.Factory
        val first = factory.create(SavedStateHandle(mapOf("search.query" to "  ЁЛКА  ")))
        val second = factory.create(SavedStateHandle())
        store.put("first", first)
        store.put("second", second)
        assertNotSame(first, second)
        assertEquals("  ЁЛКА  ", first.snapshot.value.state.rawQuery)
        assertEquals("елка", first.snapshot.value.state.query?.text)
        assertEquals("ru", first.snapshot.value.state.localeTag)
        assertEquals(SearchAccess.Unchecked, first.snapshot.value.state.access)
        assertEquals("", second.snapshot.value.state.rawQuery)
        assertEquals(0, repository.calls.size)
        val firstUi = first.uiApi()

        backgroundScope.launch { first.withUiBinding { awaitCancellation() } }
        firstUi.onIntent(SearchIntent.AccessChecked(true))
        advanceTimeBy(250)
        runCurrent()
        assertTrue(first.snapshot.value.state.body is SearchBody.Ready)
        assertEquals(1, repository.activeCount)
        assertEquals(1, repository.calls.size)
        store.clear()
        runCurrent()
        assertEquals(0, repository.activeCount)
    }
}

@DependencyGraph(AppScope::class, excludes = [SearchPlatformBindings::class])
internal interface SearchTestGraph : ViewModelGraph {
    val factories: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides repository: LibrarySearchRepository,
            @Provides @SearchInitialLocale locale: String,
        ): SearchTestGraph
    }
}
