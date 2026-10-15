package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekSessionTest {
    @Test fun playbackUpdatesDoNotOverwriteScrubAndCompletionIsConsumedOnce() {
        val session = SeekSession()
        session.change(0.75f, 120_000L)
        assertEquals(90_000L, session.displayedPosition(12_000L, 120_000L))
        assertEquals(90_000L, session.displayedPosition(13_000L, 120_000L))
        assertEquals(90_000L, session.finish())
        assertNull(session.finish())
        assertEquals(14_000L, session.displayedPosition(14_000L, 120_000L))
    }

    @Test fun cancellationRestoresPlaybackWithoutCommitting() {
        val session = SeekSession()
        session.change(0.5f, 1000L)
        session.cancel()
        assertNull(session.finish())
        assertEquals(120L, session.displayedPosition(120L, 1000L))
    }

    @Test fun endpointsClampWithoutLongOverflow() {
        val session = SeekSession()
        assertEquals(0L, session.change(-0.2f, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, session.change(1.2f, Long.MAX_VALUE))
        session.cancel()
        assertEquals(0L, session.displayedPosition(-1L, null))
        assertEquals(100L, session.displayedPosition(200L, 100L))
        assertEquals(0L, session.displayedPosition(200L, 0L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDurationCannotCreateASeek() { SeekSession().change(0.5f, 0L) }

    @Test(expected = IllegalArgumentException::class)
    fun nanCannotCreateASeek() { SeekSession().change(Float.NaN, 1000L) }

    @Test fun cancelIsHandledBeforeForwardingBothInteractionPaths() = runBlocking<Unit> {
        val session = SeekSession()
        val delegate = object : MutableInteractionSource {
            override val interactions = MutableSharedFlow<Interaction>()
            override suspend fun emit(interaction: Interaction) { assertNull(session.finish()) }
            override fun tryEmit(interaction: Interaction): Boolean {
                assertNull(session.finish())
                return true
            }
        }
        val source = SeekInteractionSource(delegate, session::cancel)
        session.change(0.5f, 1000L)
        source.emit(DragInteraction.Cancel(DragInteraction.Start()))
        session.change(0.5f, 1000L)
        source.tryEmit(DragInteraction.Cancel(DragInteraction.Start()))
    }

    @Test fun normalDragStopPreservesTheCommit() = runBlocking {
        val session = SeekSession()
        val source = SeekInteractionSource(MutableInteractionSource(), session::cancel)
        session.change(0.25f, 1000L)
        source.emit(DragInteraction.Stop(DragInteraction.Start()))
        assertEquals(250L, session.finish())
    }

    @Test fun focusLossCancelsAnUnfinishedKeyboardChange() {
        val session = SeekSession()
        val source = SeekInteractionSource(MutableInteractionSource(), session::cancel)
        session.change(0.75f, 1000L)
        source.tryEmit(FocusInteraction.Unfocus(FocusInteraction.Focus()))
        assertNull(session.finish())
    }
}
