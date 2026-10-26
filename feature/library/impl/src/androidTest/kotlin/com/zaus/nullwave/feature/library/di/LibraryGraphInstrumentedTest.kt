package com.zaus.nullwave.feature.library.di

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.di.dispatchers.IoDispatcher
import com.zaus.nullwave.feature.library.api.repository.LibraryCatalogRepository
import com.zaus.nullwave.feature.library.api.repository.LibraryRepository
import com.zaus.nullwave.feature.library.api.repository.LibrarySearchRepository
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.data.repository.MediaStoreLibraryRepository
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import java.util.Locale
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises production providers; does not subscribe to or request access to device media. */
@RunWith(AndroidJUnit4::class)
class LibraryGraphInstrumentedTest {
    @Test
    fun repositoryIsScopedToItsGraphAndLibraryFactoryIsRegistered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val factory = createGraphFactory<LibraryPlatformTestGraph.Factory>()
        val first = factory.create(context)
        val second = factory.create(context)
        assertTrue(first.repository is MediaStoreLibraryRepository)
        assertSame(Dispatchers.IO, first.io)
        assertSame(first.repository, first.repository)
        assertSame(first.catalogRepository, first.repository)
        assertSame(first.catalogRepository, first.catalogRepository)
        assertSame(first.catalogRepository, first.searchRepository)
        assertNotSame(first.searchRepository, second.searchRepository)
        assertNotSame(first.repository, second.repository)
        assertNotSame(first.catalogRepository, second.catalogRepository)
        assertTrue(first.assistedFactories.containsKey(LibraryViewModel::class))
        try {
            assertSame(first.artworkLoader, first.artworkLoader)
            assertNotSame(first.artworkLoader, second.artworkLoader)
        } finally {
            first.artworkLoader.imageLoader.shutdown()
            second.artworkLoader.imageLoader.shutdown()
        }
    }

    @Test
    fun initialLocaleUsesCurrentContextConfiguration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("sv-SE"))
        }
        val graph = createGraphFactory<LibraryPlatformTestGraph.Factory>()
            .create(context.createConfigurationContext(configuration))
        val factory = graph.assistedFactories.getValue(LibraryViewModel::class).invoke() as LibraryViewModel.Factory
        val store = ViewModelStore()
        try {
            val viewModel = factory.create(SavedStateHandle())
            store.put("library", viewModel)
            assertEquals("sv-SE", viewModel.snapshot.value.state.query.localeTag)
        } finally {
            store.clear()
        }
    }
}

@DependencyGraph(AppScope::class, excludes = [LibraryEntries::class])
internal interface LibraryPlatformTestGraph : ViewModelGraph {
    @IoDispatcher val io: CoroutineDispatcher
    val repository: LibraryRepository
    val catalogRepository: LibraryCatalogRepository
    val searchRepository: LibrarySearchRepository
    val artworkLoader: LibraryArtworkLoader
    val assistedFactories: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides @ForScope(AppScope::class) context: Context): LibraryPlatformTestGraph
    }
}
