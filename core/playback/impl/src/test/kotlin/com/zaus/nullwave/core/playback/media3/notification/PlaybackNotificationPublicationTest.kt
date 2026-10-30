package com.zaus.nullwave.core.playback.media3.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackNotificationPublicationTest {
    @Test
    fun `deferred foreground replay retains the new Play action and metadata after demotion`() {
        val platform = Platform(wasForeground = true)
        val paused = Notification("New track", "Play", ongoing = false)

        publishRetainedNotification(paused, true, platform::updateForeground, platform::notify, platform::detach)
        platform.deliverNotifications()

        assertFalse(platform.isForeground)
        assertEquals(paused, platform.posted)
        assertFalse(platform.posted.foregroundFlag)
        assertFalse(platform.posted.ongoing)
        assertEquals(1, platform.foregroundUpdates)
    }

    @Test
    fun `ordinary paused metadata updates do not promote a background service`() {
        val platform = Platform(wasForeground = false)
        val paused = Notification("Updated metadata", "Play", ongoing = false)

        publishRetainedNotification(paused, false, platform::updateForeground, platform::notify, platform::detach)
        platform.deliverNotifications()

        assertEquals(0, platform.foregroundUpdates)
        assertFalse(platform.isForeground)
        assertEquals(paused, platform.posted)
    }

    @Test
    fun `failed foreground refresh propagates without publishing or detaching`() {
        val platform = Platform(wasForeground = true)
        val original = SecurityException("Invalid foreground notification configuration")
        val before = platform.posted

        val thrown = assertThrows(SecurityException::class.java) {
            publishRetainedNotification(
                Notification("New track", "Play", ongoing = false), true,
                updateForeground = { throw original },
                notify = platform::notify,
                detach = platform::detach,
            )
        }
        platform.deliverNotifications()

        assertSame(original, thrown)
        assertTrue(platform.isForeground)
        assertEquals(before, platform.posted)
    }

    @Test
    fun `failed ordinary publication propagates without claiming successful demotion`() {
        val platform = Platform(wasForeground = true)
        val original = IllegalStateException("Notification manager rejected the update")
        val paused = Notification("New track", "Play", ongoing = false)

        val thrown = assertThrows(IllegalStateException::class.java) {
            publishRetainedNotification(
                paused, true,
                updateForeground = platform::updateForeground,
                notify = { throw original },
                detach = platform::detach,
            )
        }
        platform.deliverNotifications()

        assertSame(original, thrown)
        assertTrue(platform.isForeground)
        assertEquals(paused.copy(foregroundFlag = true), platform.posted)
    }

    private data class Notification(
        val title: String,
        val action: String,
        val ongoing: Boolean,
        val foregroundFlag: Boolean = false,
    )

    /**
     * AMS keeps a foreground payload separately from the notification last supplied to NMS.
     * Detach can enqueue that saved payload again after the app's ordinary notify update.
     */
    private class Platform(wasForeground: Boolean) {
        private val appNotifications = ArrayDeque<() -> Unit>()
        private val systemNotifications = ArrayDeque<() -> Unit>()
        private var foregroundPayload: Notification? = if (wasForeground) {
            Notification("Old track", "Pause", ongoing = true, foregroundFlag = true)
        } else null
        var posted = foregroundPayload ?: Notification("Old track", "Play", ongoing = false)
            private set
        var isForeground = wasForeground
            private set
        var foregroundUpdates = 0
            private set

        fun updateForeground(notification: Notification) {
            check(isForeground) { "An ordinary paused update must not promote the service." }
            foregroundUpdates++
            val saved = notification.copy(foregroundFlag = true)
            foregroundPayload = saved
            systemNotifications.addLast { posted = saved }
        }

        fun notify(notification: Notification) {
            appNotifications.addLast { posted = notification }
        }

        fun detach() {
            isForeground = false
            foregroundPayload?.let { saved ->
                systemNotifications.addLast { posted = saved.copy(foregroundFlag = false) }
            }
            foregroundPayload = null
        }

        fun deliverNotifications() {
            // Model the losing interleaving: NMS has applied the app's update before AMS sends
            // its deferred foreground payload. Reordering app calls alone cannot prevent this.
            while (appNotifications.isNotEmpty()) appNotifications.removeFirst().invoke()
            while (systemNotifications.isNotEmpty()) systemNotifications.removeFirst().invoke()
        }
    }
}
