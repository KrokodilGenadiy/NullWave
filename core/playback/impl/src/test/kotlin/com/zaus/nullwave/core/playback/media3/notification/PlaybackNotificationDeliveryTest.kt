package com.zaus.nullwave.core.playback.media3.notification

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackNotificationDeliveryTest {
    @Test
    fun `confirmation repairs a silently dropped demotion without promoting the paused service`() = runTest {
        val old = Notification("previous-session", 3, "Old track", "Pause", ongoing = true)
        val paused = Notification("current-session", 8, "New track", "Play", ongoing = false)
        val platform = Platform(old)
        val updates = PlaybackNotificationUpdates(backgroundScope)
        val repairs = mutableListOf<Long>()
        val unconfirmed = mutableListOf<Long>()
        var readbacks = 0

        fun publish() {
            publishRetainedNotification(
                notification = paused,
                wasForeground = platform.isForeground,
                updateForeground = platform::updateForeground,
                notify = platform::notify,
                detach = platform::detach,
            )
            platform.deliverNotifications()
        }

        // All first delivery paths are silently dropped: refreshing AMS's saved payload alone
        // cannot acknowledge delivery, and NMS still exposes an old session's Pause notification.
        publish()
        assertEquals(old, platform.posted)
        assertFalse(platform.isForeground)
        assertEquals(1, platform.foregroundRefreshes)

        updates.confirm(
            version = paused.version,
            isCurrent = { it == paused.version },
            isPublished = { version ->
                readbacks++
                platform.posted == paused && platform.posted.version == version
            },
            republish = { version ->
                repairs += version
                publish()
            },
            onUnconfirmed = unconfirmed::add,
        )
        runCurrent()
        platform.dropDeliveries = false

        advanceTimeBy(750)
        runCurrent()

        // The repair uses the intended session/version/action and actual background priority.
        assertEquals(paused, platform.posted)
        assertEquals("current-session", platform.posted.session)
        assertEquals(8L, platform.posted.version)
        assertEquals("Play", platform.posted.action)
        assertFalse(platform.posted.ongoing)
        assertFalse(platform.posted.foregroundFlag)
        assertFalse(platform.isForeground)
        assertEquals(1, platform.foregroundRefreshes)
        assertEquals(listOf(8L), repairs)

        advanceTimeBy(750)
        runCurrent()
        assertEquals(2, readbacks)
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(paused, platform.posted)
        assertEquals(listOf(8L), repairs)
        assertEquals(2, readbacks)
        assertEquals(1, platform.foregroundRefreshes)
        assertTrue(unconfirmed.isEmpty())
        updates.close()
    }

    private data class Notification(
        val session: String,
        val version: Long,
        val title: String,
        val action: String,
        val ongoing: Boolean,
        val foregroundFlag: Boolean = false,
    )

    private class Platform(initiallyPosted: Notification) {
        private val deliveries = ArrayDeque<Notification>()
        private var savedForeground: Notification? = initiallyPosted.copy(foregroundFlag = true)
        var posted = initiallyPosted
            private set
        var isForeground = true
            private set
        var foregroundRefreshes = 0
            private set
        var dropDeliveries = true

        fun updateForeground(notification: Notification) {
            check(isForeground) { "A retained-notification repair must not promote the service." }
            foregroundRefreshes++
            savedForeground = notification.copy(foregroundFlag = true).also(deliveries::addLast)
        }

        fun notify(notification: Notification) {
            deliveries.addLast(notification)
        }

        fun detach() {
            isForeground = false
            savedForeground?.let { deliveries.addLast(it.copy(foregroundFlag = false)) }
            savedForeground = null
        }

        fun deliverNotifications() {
            while (deliveries.isNotEmpty()) {
                val notification = deliveries.removeFirst()
                if (!dropDeliveries) posted = notification
            }
        }
    }
}
