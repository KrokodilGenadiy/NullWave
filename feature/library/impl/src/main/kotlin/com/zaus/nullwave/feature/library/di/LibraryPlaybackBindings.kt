package com.zaus.nullwave.feature.library.di

import com.zaus.nullwave.core.di.dispatchers.IoDispatcher
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.feature.library.data.playback.MediaStorePlaybackSourceResolver
import com.zaus.nullwave.feature.library.data.source.MediaStorePlaybackSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher

/** Core owns the source port; Library contributes the MediaStore implementation to the app graph. */
@BindingContainer
@ContributesTo(AppScope::class)
object LibraryPlaybackBindings {
    @Provides
    @SingleIn(AppScope::class)
    private fun playbackSourceResolver(
        source: MediaStorePlaybackSource,
        @IoDispatcher io: CoroutineDispatcher,
    ): PlaybackSourceResolver = MediaStorePlaybackSourceResolver(source, io)
}
