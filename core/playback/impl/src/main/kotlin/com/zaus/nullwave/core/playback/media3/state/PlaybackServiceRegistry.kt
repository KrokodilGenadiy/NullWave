package com.zaus.nullwave.core.playback.media3.state

import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-confined, same-process service publications. The service is the sole writer, never a UI cache. */
internal class PlaybackServiceRegistry {
    private val mutableSnapshot = MutableStateFlow<PlaybackSnapshot?>(null)
    private val mutableAnchor = MutableStateFlow<PlaybackProgress?>(null)
    private val mutableFailure = MutableStateFlow<PlaybackConnectionError?>(null)
    val snapshot = mutableSnapshot.asStateFlow()
    val anchor = mutableAnchor.asStateFlow()
    val failure = mutableFailure.asStateFlow()
    private var session: PlaybackSessionId? = null
    private var sampler: (() -> PlaybackProgress?)? = null
    private var publication: Any? = null

    fun attach(id: PlaybackSessionId, sample: () -> PlaybackProgress?) {
        session = id
        sampler = sample
        val change = Any().also { publication = it }
        mutableSnapshot.value = null
        if (publication !== change) return
        mutableAnchor.value = null
        if (publication === change) mutableFailure.value = null
    }

    fun publish(value: PlaybackSnapshot, progress: PlaybackProgress?) {
        if (session != value.sessionId) return
        val change = Any().also { publication = it }
        mutableSnapshot.value = value
        // An immediate observer can trigger a newer service event before the first setter returns.
        if (publication === change) mutableAnchor.value = progress
    }

    fun sampleProgress(): PlaybackProgress? = sampler?.invoke()

    fun startNotAllowed(id: PlaybackSessionId) {
        if (session == id) mutableFailure.value = PlaybackConnectionError.StartNotAllowed
    }

    /** Current service attachment, which may precede its first snapshot. Only the service writes it. */
    val attachedSessionId: PlaybackSessionId? get() = session

    fun clearConnectionError() { mutableFailure.value = null }

    fun detach(id: PlaybackSessionId) {
        if (session != id) return
        session = null
        sampler = null
        val change = Any().also { publication = it }
        mutableSnapshot.value = null
        if (publication !== change) return
        mutableAnchor.value = null
        if (publication === change) mutableFailure.value = null
    }
}
