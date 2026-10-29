package com.zaus.nullwave.core.playback.media3

internal object PlaybackSessionProtocol {
    const val EXECUTE_PRIVATE = "com.zaus.nullwave.playback.EXECUTE_PRIVATE"
    const val TICKET_ID = "ticket_id"
    const val SESSION_ID = "playback_session_id"
    const val START_SERVICE = "com.zaus.nullwave.playback.START_SERVICE"
    const val START_INSTANCE_ID = "playback_start_instance_id"
    const val CONNECTION_TIMEOUT_MS = 5_000L
    const val COMMAND_TIMEOUT_MS = 30_000L
    const val PROGRESS_INTERVAL_MS = 250L
}
