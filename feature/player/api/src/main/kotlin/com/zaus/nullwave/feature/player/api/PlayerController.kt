package com.zaus.nullwave.feature.player.api

import kotlinx.coroutines.flow.StateFlow

/**
 * What the rest of the app is allowed to do to playback.
 *
 * This is the whole point of the api/impl split: `:feature:library:impl` calls these methods
 * without ever seeing `:feature:player:impl`. Metro binds the interface to the implementation at
 * the graph, so neither module knows about the other.
 *
 * Deliberately small. Anything the player needs but nobody else calls - the equalizer session, the
 * MediaSession, the queue internals - stays in the impl module.
 */
interface PlayerController {

    /** Null when nothing is loaded. */
    val nowPlayingId: StateFlow<Long?>

    val isPlaying: StateFlow<Boolean>

    /** Replace the queue with [trackIds] and start at [startIndex]. */
    fun play(trackIds: List<Long>, startIndex: Int = 0)

    fun togglePlayPause()

    fun next()

    fun previous()
}
