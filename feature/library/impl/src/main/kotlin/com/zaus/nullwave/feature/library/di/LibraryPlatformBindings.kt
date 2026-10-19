package com.zaus.nullwave.feature.library.di

import android.content.Context
import com.zaus.nullwave.feature.library.api.repository.LibraryRepository
import com.zaus.nullwave.feature.library.data.AndroidMediaStoreAudioSource
import com.zaus.nullwave.feature.library.data.MediaStoreLibraryRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LibraryIo

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LibraryInitialLocale

@BindingContainer
@ContributesTo(AppScope::class)
object LibraryPlatformBindings {
    // The app graph can access this provider without exposing the implementation or raw rows.
    @Provides
    @SingleIn(AppScope::class)
    fun repository(
        @ForScope(AppScope::class) context: Context,
        @LibraryIo io: CoroutineDispatcher,
    ): LibraryRepository = MediaStoreLibraryRepository(AndroidMediaStoreAudioSource(context, io), io)

    @Provides
    @LibraryIo
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /** Read for each VM creation; an app-scoped cached locale would outlive configuration changes. */
    @Provides
    @LibraryInitialLocale
    fun initialLocale(@ForScope(AppScope::class) context: Context): String =
        context.resources.configuration.locales[0].toLanguageTag()
}
