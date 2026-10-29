package com.zaus.nullwave.di

import android.content.Context
import com.zaus.nullwave.core.di.dispatchers.DefaultDispatcher
import com.zaus.nullwave.core.di.dispatchers.MainDispatcher
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.playback.media3.service.PlaybackServiceDependencies
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher

/** App composes the media source and backend without adding feature or UI dependencies to core playback. */
@BindingContainer
@ContributesTo(AppScope::class)
object PlaybackBindings {
    @Provides
    @SingleIn(AppScope::class)
    private fun dependencies(
        @ForScope(AppScope::class) context: Context,
        resolver: PlaybackSourceResolver,
        @DefaultDispatcher default: CoroutineDispatcher,
        @MainDispatcher main: CoroutineDispatcher,
    ): PlaybackServiceDependencies = PlaybackServiceDependencies.create(context, resolver, default, main)

    @Provides
    @SingleIn(AppScope::class)
    private fun controller(dependencies: PlaybackServiceDependencies): PlaybackController = dependencies.controller()
}
