package com.zaus.nullwave.core.playback.api.model

/** Client connectivity. Disconnection does not imply that the service has stopped playing. */
sealed interface PlaybackConnection {
    data object Disconnected : PlaybackConnection
    data object Connecting : PlaybackConnection
    data class Connected(val snapshot: PlaybackSnapshot) : PlaybackConnection
    data class Unavailable(val reason: PlaybackConnectionError) : PlaybackConnection
}

enum class PlaybackConnectionError { ConnectionFailed, ServiceDisconnected, StartNotAllowed }
