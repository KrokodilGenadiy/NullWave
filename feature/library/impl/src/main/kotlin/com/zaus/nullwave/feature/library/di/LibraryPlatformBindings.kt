package com.zaus.nullwave.feature.library.di

import android.content.Context
import coil3.ImageLoader
import com.zaus.nullwave.core.di.dispatchers.IoDispatcher
import com.zaus.nullwave.feature.library.api.repository.LibraryCatalogRepository
import com.zaus.nullwave.feature.library.api.repository.LibraryRepository
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.data.repository.MediaStoreLibraryRepository
import com.zaus.nullwave.feature.library.data.source.AndroidMediaStoreAudioSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LibraryInitialLocale

@BindingContainer
@ContributesTo(AppScope::class)
object LibraryPlatformBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun artworkLoader(
        @ForScope(AppScope::class) context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): LibraryArtworkLoader = LibraryArtworkLoader(
        ImageLoader.Builder(context)
            .fetcherCoroutineContext(io)
            .decoderCoroutineContext(io)
            .diskCache(null)
            .build(),
    )

    // The app graph can access this provider without exposing the implementation or raw rows.
    @Provides
    @SingleIn(AppScope::class)
    fun catalogRepository(
        @ForScope(AppScope::class) context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): LibraryCatalogRepository = MediaStoreLibraryRepository(AndroidMediaStoreAudioSource(context, io), io)

    /** The narrow Songs contract is an alias of the same app-scoped catalog instance. */
    @Provides
    fun repository(catalog: LibraryCatalogRepository): LibraryRepository = catalog

    /** Read for each VM creation; an app-scoped cached locale would outlive configuration changes. */
    @Provides
    @LibraryInitialLocale
    fun initialLocale(@ForScope(AppScope::class) context: Context): String =
        context.resources.configuration.locales[0].toLanguageTag()
}
