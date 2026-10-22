package com.zaus.nullwave.feature.library.di

import android.content.Context
import coil3.ImageLoader
import com.zaus.nullwave.core.di.dispatchers.IoDispatcher
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.data.source.AndroidMediaStoreAudioSource
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioSource
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

    @Provides
    private fun audioSource(
        @ForScope(AppScope::class) context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): MediaStoreAudioSource = AndroidMediaStoreAudioSource(context, io)

    /** Read for each VM creation; an app-scoped cached locale would outlive configuration changes. */
    @Provides
    @LibraryInitialLocale
    fun initialLocale(@ForScope(AppScope::class) context: Context): String =
        context.resources.configuration.locales[0].toLanguageTag()
}
