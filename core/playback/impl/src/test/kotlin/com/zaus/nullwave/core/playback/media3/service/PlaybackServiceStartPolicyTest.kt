package com.zaus.nullwave.core.playback.media3.service

import com.zaus.nullwave.core.playback.media3.service.PlaybackServiceStartPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackServiceStartPolicyTest {
    @Test
    fun `matching active self start does not repeat a command or release foreground`() {
        assertEquals(Decision.Ignore, decide(isMediaButton = false, received = "current", ongoing = true))
    }

    @Test
    fun `self start delivered after its preparation failed finishes an idle service`() {
        assertEquals(Decision.FinishIdleStart, decide(isMediaButton = false, received = "current", ongoing = false))
    }

    @Test
    fun `old self start cannot revive an empty new service or stop its active replacement`() {
        assertEquals(Decision.FinishIdleStart, decide(isMediaButton = false, received = "previous", ongoing = false))
        assertEquals(Decision.Ignore, decide(isMediaButton = false, received = "previous", ongoing = true))
    }

    @Test
    fun `null restart has no queue to restore and does not stop existing playback`() {
        assertEquals(Decision.FinishIdleStart, decide(isMediaButton = false, received = null, ongoing = false))
        assertEquals(Decision.Ignore, decide(isMediaButton = false, received = null, ongoing = true))
    }

    @Test
    fun `current notification and unstamped system media buttons reach the native session`() {
        for (ongoing in listOf(false, true)) {
            assertEquals(Decision.DelegateMediaButton, decide(isMediaButton = true, received = "current", ongoing = ongoing))
            assertEquals(Decision.DelegateMediaButton, decide(isMediaButton = true, received = null, ongoing = ongoing))
        }
    }

    @Test
    fun `old or malformed notification cannot pause a newer service incarnation`() {
        for (old in listOf("previous", "")) {
            assertEquals(Decision.Ignore, decide(isMediaButton = true, received = old, ongoing = true))
            assertEquals(Decision.FinishIdleStart, decide(isMediaButton = true, received = old, ongoing = false))
        }
    }

    @Test
    fun `unknown plain startup cannot retain an empty service`() {
        assertEquals(Decision.FinishIdleStart, decide(isMediaButton = false, received = "current", ongoing = false))
        assertEquals(Decision.Ignore, decide(isMediaButton = false, received = "current", ongoing = true))
    }

    private fun decide(isMediaButton: Boolean, received: String?, ongoing: Boolean): Decision =
        PlaybackServiceStartPolicy.decide("current", isMediaButton, received, ongoing)
}
