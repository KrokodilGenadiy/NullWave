package com.zaus.nullwave.feature.library.di

import com.zaus.nullwave.core.di.dispatchers.CoroutineDispatcherBindings
import com.zaus.nullwave.core.di.dispatchers.IoDispatcher
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.library.data.source.MediaStorePlaybackData
import com.zaus.nullwave.feature.library.data.source.MediaStorePlaybackSource
import com.zaus.nullwave.feature.library.data.source.MediaStoreTrackAddress
import com.zaus.nullwave.feature.library.data.source.audioRow
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** The real contributed binding is used; only the Android boundary and dispatcher are substituted. */
class LibraryPlaybackGraphTest {
    @Test
    fun `resolver is scoped per graph and creating it does not read the provider`() = runTest {
        var reads = 0
        val row = audioRow()
        val source = object : MediaStorePlaybackSource {
            override fun hasReadAccess() = true
            override suspend fun readTrack(address: MediaStoreTrackAddress): MediaStorePlaybackData {
                assertEquals(MediaStoreTrackAddress("external_primary", 1), address)
                reads++
                return MediaStorePlaybackData(
                    row, listOf(row), "content://media/external_primary/audio/media/1",
                )
            }
        }
        val factory = createGraphFactory<LibraryPlaybackTestGraph.Factory>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = factory.create(source, dispatcher)
        val second = factory.create(source, dispatcher)
        assertSame(first.resolver, first.resolver)
        assertNotSame(first.resolver, second.resolver)
        assertEquals(0, reads)

        val resolved = first.resolver.resolveTrack(TrackId("external_primary:1")) as AppResult.Success
        assertEquals(TrackId("external_primary:1"), resolved.value.track.id)
        assertEquals("Low Orbit", resolved.value.track.title)
        assertEquals("content://media/external_primary/audio/media/1", resolved.value.resourceUri)
        assertEquals(1, reads)
    }
}

@DependencyGraph(
    AppScope::class,
    excludes = [
        LibraryPlatformBindings::class,
        LibraryRepositoryBindings::class,
        LibraryEntries::class,
        CoroutineDispatcherBindings::class,
        LibraryViewModel.Factory::class,
    ],
)
internal interface LibraryPlaybackTestGraph {
    val resolver: PlaybackSourceResolver

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides source: MediaStorePlaybackSource,
            @Provides @IoDispatcher io: CoroutineDispatcher,
        ): LibraryPlaybackTestGraph
    }
}
