package com.zaus.nullwave.feature.library.di

import android.content.Context
import coil3.ImageLoader
import com.zaus.nullwave.core.di.dispatchers.IoDispatcher
import com.zaus.nullwave.core.media.waveform.WaveformSource
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.data.source.AndroidMediaStoreAudioSource
import com.zaus.nullwave.feature.library.data.source.AndroidMediaStorePlaybackSource
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioSource
import com.zaus.nullwave.feature.library.data.source.MediaStorePlaybackSource
import com.zaus.nullwave.feature.library.data.waveform.MediaStoreWaveformSource
import com.zaus.nullwave.feature.library.data.waveform.work.AndroidWaveformWorkQueue
import com.zaus.nullwave.feature.library.data.waveform.work.WaveformCatalogSync
import com.zaus.nullwave.feature.library.data.waveform.work.WaveformWorkCoordinator
import com.zaus.nullwave.feature.library.data.waveform.work.WaveformWorkerComponent
import com.zaus.nullwave.feature.library.platform.hasAudioAccess
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LibraryInitialLocale

@BindingContainer
@ContributesTo(AppScope::class)
object LibraryPlatformBindings {
    @Provides
    @SingleIn(AppScope::class)
    private fun mediaStoreWaveforms(
        @ForScope(AppScope::class) context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): MediaStoreWaveformSource = MediaStoreWaveformSource(
        context.contentResolver, io, File(context.cacheDir, "waveforms"), context::hasAudioAccess,
    )

    @Provides
    private fun waveformSource(source: MediaStoreWaveformSource): WaveformSource = source

    @Provides
    @SingleIn(AppScope::class)
    private fun waveformPreparation(
        @ForScope(AppScope::class) context: Context,
        @IoDispatcher io: CoroutineDispatcher,
        source: MediaStoreWaveformSource,
    ): WaveformWorkCoordinator = WaveformWorkCoordinator(
        CoroutineScope(SupervisorJob() + io), AndroidWaveformWorkQueue(context), context::hasAudioAccess,
        source::hasPreparedRevision, source::prepareInBackground,
    )

    @Provides
    private fun waveformSync(preparation: WaveformWorkCoordinator): WaveformCatalogSync = preparation

    @Provides
    private fun waveformWorker(preparation: WaveformWorkCoordinator): WaveformWorkerComponent = preparation

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

    @Provides
    private fun playbackSource(
        @ForScope(AppScope::class) context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): MediaStorePlaybackSource = AndroidMediaStorePlaybackSource(context, io)

    /** Read for each VM creation; an app-scoped cached locale would outlive configuration changes. */
    @Provides
    @LibraryInitialLocale
    fun initialLocale(@ForScope(AppScope::class) context: Context): String =
        context.resources.configuration.locales[0].toLanguageTag()
}
