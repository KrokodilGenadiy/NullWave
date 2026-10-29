package com.zaus.nullwave.core.playback.media3.session

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCommandTicketsTest {
    @Test
    fun `private command handoff is one-shot and preserves captured commands`() {
        val store = PlaybackCommandTickets(PlaybackClock { 0 })
        val target = PlaybackItemToken(PlaybackSessionId("session"), QueueEntryId("entry"), 0)
        val commands = listOf(
            replace("a", "a", "b"),
            PlaybackCommand.SeekTo(target, Long.MAX_VALUE),
            PlaybackCommand.Retry(target, PlaybackProblemId("problem")),
            PlaybackCommand.StopAndClear,
            PlaybackCommand.SetPlayRequested(false),
            PlaybackCommand.Next,
            PlaybackCommand.Previous,
            PlaybackCommand.SetRepeat(RepeatMode.All),
            PlaybackCommand.SetShuffle(true),
        )
        val tickets = commands.map { store.stage(it)!! }

        assertEquals(tickets.size, tickets.map { it.id }.toSet().size)
        for ((index, ticket) in tickets.withIndex()) {
            assertSame(commands[index], ticket.command)
            assertSame(ticket, store.take(ticket.id))
            assertNull(store.take(ticket.id))
        }
    }

    @Test
    fun `unknown ticket cannot consume another staged command`() {
        val store = PlaybackCommandTickets(PlaybackClock { 0 })
        val ticket = store.stage(PlaybackCommand.Next)!!
        assertNull(store.take("unknown"))
        assertSame(ticket, store.take(ticket.id))
    }

    @Test
    fun `capacity rejects excess handoffs and consuming or discarding releases the slot`() {
        val store = PlaybackCommandTickets(PlaybackClock { 0 }, capacity = 1)
        val first = store.stage(replace("a"))!!
        assertNull(store.stage(replace("b")))

        assertSame(first, store.take(first.id))
        val second = store.stage(replace("b"))!!
        assertNotEquals(first.id, second.id)
        store.discard(second.id)

        assertNull(store.take(second.id))
        assertTrue(store.stage(replace("c")) != null)
    }

    @Test
    fun `deadline expires an unconsumed command before it can be accepted`() = runTest {
        var now = 100L
        val store = PlaybackCommandTickets(PlaybackClock { now }, capacity = 1, timeoutMs = 10)
        val expired = store.stage(replace("expired"))!!
        now = 109
        assertFalse(expired.result.isCompleted)
        assertNull(store.stage(replace("too-early")))

        now = 110
        val replacement = store.stage(replace("new"))!!
        assertEquals(AppResult.Failure(PlaybackCommandError.ConnectionUnavailable), expired.result.await())
        assertNull(store.take(expired.id))
        assertSame(replacement, store.take(replacement.id))
    }

    @Test
    fun `expiry cannot cancel a command already handed to the service`() = runTest {
        val store = PlaybackCommandTickets(PlaybackClock { 0 })
        val staged = store.stage(replace("accepted"))!!
        val accepted = store.take(staged.id)!!

        store.expire(staged.id)
        store.discard(staged.id)
        assertFalse(accepted.result.isCompleted)
        accepted.result.complete(AppResult.Success(Unit))
        assertEquals(AppResult.Success(Unit), staged.result.await())
    }

    @Test
    fun `same-process reply preserves original diagnostics including explicit null message`() = runTest {
        val store = PlaybackCommandTickets(PlaybackClock { 0 })
        val cause = IOException("provider's original message")
        for (message in listOf("captured diagnostic", null)) {
            val staged = store.stage(replace("failed"))!!
            val accepted = store.take(staged.id)!!
            val failure = AppResult.Failure(
                PlaybackCommandError.SourceFailure(TrackId("failed"), PlaybackSourceError.AccessDenied),
                cause,
                message,
            )
            accepted.result.complete(failure)

            assertSame(failure, staged.result.await())
            assertSame(cause, (staged.result.await() as AppResult.Failure).cause)
            assertEquals(message, (staged.result.await() as AppResult.Failure).originalMessage)
        }
    }

    private fun replace(vararg ids: String) = PlaybackCommand.ReplaceQueue(
        PlaybackQueueRequest.create(ids.map(::TrackId), startIndex = 0),
    )
}
