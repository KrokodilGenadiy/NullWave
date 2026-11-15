package com.zaus.nullwave.feature.library.di

import com.zaus.nullwave.core.di.dispatchers.IoDispatcher
import com.zaus.nullwave.feature.library.api.repository.LibraryAlbumDetailsRepository
import com.zaus.nullwave.feature.library.api.repository.LibraryCatalogRepository
import com.zaus.nullwave.feature.library.api.repository.LibraryRepository
import com.zaus.nullwave.feature.library.api.repository.LibrarySearchRepository
import com.zaus.nullwave.feature.library.data.repository.MediaStoreLibraryRepository
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioSource
import com.zaus.nullwave.feature.library.data.waveform.work.WaveformCatalogSync
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher

/** One scoped implementation; subscriptions still belong to collectors. */
@BindingContainer
@ContributesTo(AppScope::class)
object LibraryRepositoryBindings {
    @Provides
    @SingleIn(AppScope::class)
    private fun mediaStoreRepository(
        source: MediaStoreAudioSource,
        @IoDispatcher io: CoroutineDispatcher,
        waveformSync: WaveformCatalogSync,
    ): MediaStoreLibraryRepository = MediaStoreLibraryRepository(source, io, waveformSync)

    @Provides
    private fun catalog(repository: MediaStoreLibraryRepository): LibraryCatalogRepository = repository

    @Provides
    private fun albumDetails(repository: MediaStoreLibraryRepository): LibraryAlbumDetailsRepository = repository

    @Provides
    private fun tracks(repository: MediaStoreLibraryRepository): LibraryRepository = repository

    @Provides
    private fun search(repository: MediaStoreLibraryRepository): LibrarySearchRepository = repository
}
