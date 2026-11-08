package com.zaus.nullwave.feature.library.di

import com.zaus.nullwave.core.di.dispatchers.CoroutineDispatcherBindings
import com.zaus.nullwave.core.di.dispatchers.IoDispatcher
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import com.zaus.nullwave.feature.library.api.repository.LibraryCatalogRepository
import com.zaus.nullwave.feature.library.api.repository.LibraryRepository
import com.zaus.nullwave.feature.library.api.repository.LibrarySearchRepository
import com.zaus.nullwave.feature.library.data.source.audioRow
import com.zaus.nullwave.feature.library.data.source.FakeMediaStoreAudioSource
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioSource
import com.zaus.nullwave.feature.library.data.waveform.work.WaveformCatalogSync
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** Real production repository bindings; only the Android source and dispatcher are replaced. */
class LibraryRepositoryGraphTest {
    @Test
    fun `catalog tracks and search alias one lazy scoped implementation per graph`() = runTest {
        val source = FakeMediaStoreAudioSource().apply { read = { listOf(audioRow(title = "Needle")) } }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val factory = createGraphFactory<LibraryRepositoryTestGraph.Factory>()
        val first = factory.create(source, dispatcher)
        val second = factory.create(source, dispatcher)
        assertSame(first.catalog, first.tracks)
        assertSame(first.catalog, first.search)
        assertSame(first.search, first.search)
        assertNotSame(first.search, second.search)
        assertEquals(0, source.reads)
        assertEquals(0, source.registrations)
        val result = first.search.observeSearch(checkNotNull(LibrarySearchQuery.fromInput("needle", "en"))).first()
        assertEquals("Needle", (result as AppResult.Success).value.tracks.single().track.title)
        assertEquals(1, source.reads)
        assertEquals(1, source.closes)
    }
}

@DependencyGraph(
    AppScope::class,
    excludes = [
        LibraryPlatformBindings::class,
        LibraryEntries::class,
        CoroutineDispatcherBindings::class,
        LibraryViewModel.Factory::class,
    ],
)
internal interface LibraryRepositoryTestGraph {
    @Provides
    fun waveformSync(): WaveformCatalogSync = WaveformCatalogSync {}

    val catalog: LibraryCatalogRepository
    val tracks: LibraryRepository
    val search: LibrarySearchRepository

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides source: MediaStoreAudioSource,
            @Provides @IoDispatcher io: CoroutineDispatcher,
        ): LibraryRepositoryTestGraph
    }
}
