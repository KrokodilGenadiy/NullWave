package com.zaus.nullwave.core.playback.media3

import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred

/** Same-UID, same-process handoff: Binder carries a small token, not a large queue or Throwable. */
internal class PlaybackCommandTickets(
    private val clock: PlaybackClock,
    private val capacity: Int = 32,
    private val timeoutMs: Long = 30_000,
) {
    private val pending = mutableMapOf<String, Ticket>()

    fun stage(command: PlaybackCommand): Ticket? {
        prune()
        if (pending.size >= capacity) return null
        return Ticket(UUID.randomUUID().toString(), command, clock.nowMs()).also { pending[it.id] = it }
    }

    fun take(id: String): Ticket? {
        prune()
        return pending.remove(id)
    }

    fun discard(id: String) { pending.remove(id) }

    fun expire(id: String) {
        pending.remove(id)?.result?.complete(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable))
    }

    private fun prune() {
        val now = clock.nowMs()
        pending.values.filter { now - it.createdAtMs >= timeoutMs }.forEach { expire(it.id) }
    }

    class Ticket internal constructor(val id: String, val command: PlaybackCommand, internal val createdAtMs: Long) {
        // UI cancellation never cancels the service's accepted operation.
        val result = CompletableDeferred<EmptyResult<PlaybackCommandError>>()
    }
}
