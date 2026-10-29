package com.zaus.nullwave.core.playback.media3.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackForegroundPolicyTest {
    @Test
    fun `preparation keeps an empty player foreground until its own lease finishes`() {
        val policy = PlaybackForegroundPolicy()
        val lease = policy.acquire()
        assertTrue(policy.needsForeground(playRequested = false, readyOrBuffering = false))
        lease.close()
        assertFalse(policy.needsForeground(playRequested = false, readyOrBuffering = false))
    }

    @Test
    fun `superseded request and repeated release cannot dismiss a newer preparation`() {
        val policy = PlaybackForegroundPolicy()
        val older = policy.acquire()
        val newer = policy.acquire()
        older.close()
        older.close()
        assertTrue(policy.preparing)
        assertTrue(policy.needsForeground(playRequested = false, readyOrBuffering = false))
        newer.close()
        assertFalse(policy.preparing)
    }

    @Test
    fun `installed playing or buffering queue keeps foreground after preparation finishes`() {
        val policy = PlaybackForegroundPolicy()
        val lease = policy.acquire()
        lease.close()
        assertTrue(policy.needsForeground(playRequested = true, readyOrBuffering = true))
    }

    @Test
    fun `pause stop end and idle do not retain foreground without preparation`() {
        val policy = PlaybackForegroundPolicy()
        assertFalse(policy.needsForeground(playRequested = false, readyOrBuffering = true))
        assertFalse(policy.needsForeground(playRequested = true, readyOrBuffering = false))
        assertFalse(policy.needsForeground(playRequested = false, readyOrBuffering = false))
    }

    @Test
    fun `close revokes all leases and late releases do not revive foreground`() {
        val policy = PlaybackForegroundPolicy()
        val lease = policy.acquire()
        policy.close()
        assertFalse(policy.preparing)
        assertFalse(policy.needsForeground(playRequested = true, readyOrBuffering = true))
        lease.close()
        assertFalse(policy.needsForeground(playRequested = true, readyOrBuffering = true))
    }
}
