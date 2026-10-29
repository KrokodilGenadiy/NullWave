package com.zaus.nullwave.core.playback.media3.testing

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.widget.TextView
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.playback.media3.PlaybackServiceComponent
import com.zaus.nullwave.core.playback.media3.PlaybackServiceComponentOwner
import com.zaus.nullwave.core.playback.media3.PlaybackServiceDependencies
import com.zaus.nullwave.core.result.AppResult
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers

class PlaybackTestApplication : Application(), PlaybackServiceComponentOwner {
    val tracks = ConcurrentHashMap<TrackId, ResolvedPlaybackTrack>()
    @Volatile var read: (suspend (TrackId) -> AppResult<ResolvedPlaybackTrack, PlaybackSourceError>)? = null
    private val source = object : PlaybackSourceResolver {
        override suspend fun resolveTrack(id: TrackId): AppResult<ResolvedPlaybackTrack, PlaybackSourceError> =
            read?.invoke(id) ?: tracks[id]?.let { AppResult.Success(it) } ?: AppResult.Failure(PlaybackSourceError.NotFound)
    }
    val dependencies by lazy { PlaybackServiceDependencies.create(this, source, Dispatchers.Default, Dispatchers.Main.immediate) }
    override val playbackServiceComponent = object : PlaybackServiceComponent {
        override val playbackServiceDependencies: PlaybackServiceDependencies get() = dependencies
    }
}

/** Foreground host lets the instrumentation test exercise real media-playback FGS rules. */
class PlaybackTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "NullWave playback integration test" })
    }
}
