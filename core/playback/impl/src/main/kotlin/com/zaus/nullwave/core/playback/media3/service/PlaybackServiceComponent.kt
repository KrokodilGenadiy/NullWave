package com.zaus.nullwave.core.playback.media3.service

import android.content.Context
import android.os.SystemClock
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.playback.media3.client.Media3PlaybackController
import com.zaus.nullwave.core.playback.media3.session.PlaybackCommandTickets
import com.zaus.nullwave.core.playback.media3.state.PlaybackServiceRegistry
import kotlinx.coroutines.CoroutineDispatcher

/** Implemented by the application's composition root; the core service never imports the app graph. */
interface PlaybackServiceComponent {
    val playbackServiceDependencies: PlaybackServiceDependencies
}

interface PlaybackServiceComponentOwner {
    val playbackServiceComponent: PlaybackServiceComponent
}

/** Public graph entry point with module-private construction and runtime dependencies. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackServiceDependencies internal constructor(
    internal val resolver: PlaybackSourceResolver,
    internal val default: CoroutineDispatcher,
    internal val main: CoroutineDispatcher,
    internal val registry: PlaybackServiceRegistry,
    internal val tickets: PlaybackCommandTickets,
    internal val clock: PlaybackClock,
    private val client: PlaybackController,
) {
    fun controller(): PlaybackController = client

    companion object {
        /** Called once by the app-scoped DI provider; creation does not connect or create a player. */
        fun create(
            context: Context,
            resolver: PlaybackSourceResolver,
            default: CoroutineDispatcher,
            main: CoroutineDispatcher,
        ): PlaybackServiceDependencies {
            val clock = PlaybackClock { SystemClock.elapsedRealtime() }
            val registry = PlaybackServiceRegistry()
            val tickets = PlaybackCommandTickets(clock)
            val applicationDispatcher = (main as? kotlinx.coroutines.MainCoroutineDispatcher)?.immediate ?: main
            val client = Media3PlaybackController(context.applicationContext, applicationDispatcher, registry, tickets)
            return PlaybackServiceDependencies(resolver, default, applicationDispatcher, registry, tickets, clock, client)
        }
    }
}
