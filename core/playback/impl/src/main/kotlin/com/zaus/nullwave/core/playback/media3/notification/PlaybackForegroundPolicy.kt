package com.zaus.nullwave.core.playback.media3.notification

/** Main-confined preparation leases. A completed older request cannot release a newer request. */
internal class PlaybackForegroundPolicy : AutoCloseable {
    private val requests = mutableSetOf<Any>()
    private var closed = false

    val preparing: Boolean get() = requests.isNotEmpty()

    fun acquire(): AutoCloseable {
        check(!closed) { "The playback notification is closed." }
        val request = Any()
        requests += request
        return AutoCloseable { requests -= request }
    }

    fun needsForeground(playRequested: Boolean, readyOrBuffering: Boolean): Boolean =
        !closed && (preparing || (playRequested && readyOrBuffering))

    override fun close() {
        closed = true
        requests.clear()
    }
}
