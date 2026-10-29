package com.zaus.nullwave.core.playback.media3

import android.view.KeyEvent
import androidx.media3.common.Player
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackSuppression
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackNotificationActionTest {
    @Test
    fun `ended and native stopped playback offer explicit Play even with retained play intent`() {
        for (state in listOf(Player.STATE_ENDED, Player.STATE_IDLE)) {
            assertEquals(PlaybackNotificationAction.Play, PlaybackNotificationAction.playPause(state, playRequested = true))
        }
    }

    @Test
    fun `ready and buffering with play intent offer Pause rather than another play request`() {
        for (state in listOf(Player.STATE_READY, Player.STATE_BUFFERING)) {
            assertEquals(PlaybackNotificationAction.Pause, PlaybackNotificationAction.playPause(state, playRequested = true))
        }
    }

    @Test
    fun `transient suppression retains a Pause action to cancel automatic resume`() {
        val transport = PlaybackTransport(
            phase = PlaybackPhase.Ready, playRequested = true, suppression = PlaybackSuppression.AudioFocus,
        )
        assertFalse(transport.isPlaying)
        assertEquals(PlaybackNotificationAction.Pause, PlaybackNotificationAction.playPause(Player.STATE_READY, transport.playRequested))
    }

    @Test
    fun `paused playback offers Play in every phase`() {
        for (state in listOf(Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED)) {
            assertEquals(PlaybackNotificationAction.Play, PlaybackNotificationAction.playPause(state, playRequested = false))
        }
    }

    @Test
    fun `Play and Pause are explicit opposite native actions and only Play starts foreground`() {
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY, PlaybackNotificationAction.Play.keyCode)
        assertEquals(KeyEvent.KEYCODE_MEDIA_PAUSE, PlaybackNotificationAction.Pause.keyCode)
        assertNotEquals(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, PlaybackNotificationAction.Play.keyCode)
        assertNotEquals(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, PlaybackNotificationAction.Pause.keyCode)
        assertTrue(PlaybackNotificationAction.Play.startsForeground)
        assertFalse(PlaybackNotificationAction.Pause.startsForeground)
        assertFalse(PlaybackNotificationAction.Next.startsForeground)
        assertFalse(PlaybackNotificationAction.Previous.startsForeground)
        assertFalse(PlaybackNotificationAction.Stop.startsForeground)
    }
}
