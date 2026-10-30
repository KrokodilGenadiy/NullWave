package com.zaus.nullwave.core.playback.media3.notification

import android.app.Notification
import android.app.NotificationChannel
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.test.utils.FakePlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.playback.impl.R
import com.zaus.nullwave.core.playback.media3.session.PlaybackSessionProtocol
import com.zaus.nullwave.core.playback.media3.testing.withPlaybackTestCleanup
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real renderer/readback and Android payloads; only platform publication is held in memory. */
@RunWith(AndroidJUnit4::class)
@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackForegroundNotificationInstrumentedTest {
    @Test
    fun emptyQueueCancelsPendingContentWithoutResurrectingTheOldNotification(): Unit =
        verifyNoResurrection(Pending.Content, Shutdown.EmptyQueue)

    @Test
    fun closeCancelsPendingContentWithoutPublishingItsNewVersion(): Unit =
        verifyNoResurrection(Pending.Content, Shutdown.Close)

    @Test
    fun emptyQueueCancelsPendingConfirmationWithoutRepublishingTheRemovedVersion(): Unit =
        verifyNoResurrection(Pending.Confirmation, Shutdown.EmptyQueue)

    @Test
    fun closeCancelsPendingConfirmationWithoutRepublishingTheClosedVersion(): Unit =
        verifyNoResurrection(Pending.Confirmation, Shutdown.Close)

    private fun verifyNoResurrection(pending: Pending, shutdown: Shutdown): Unit = runBlocking {
        withContext(Dispatchers.Main.immediate) {
            val fixture = Fixture()
            withPlaybackTestCleanup(
                cleanup = listOf(
                    { fixture.notification.close() },
                    {
                        fixture.job.cancel()
                        fixture.clock.runCurrent()
                        fixture.job.join()
                    },
                    { fixture.session.release() },
                    { fixture.player.release() },
                    { fixture.platform.cancelPendingIntents() },
                ),
            ) {
                fixture.platform.dropNextPublication = pending == Pending.Confirmation
                fixture.notification.update()
                fixture.clock.runCurrent()
                fixture.assertRequestedPayload()

                when (pending) {
                    Pending.Content -> fixture.awaitPendingContent()
                    Pending.Confirmation -> fixture.awaitPendingConfirmation()
                }
                assertEquals("The selected delayed job must really be pending", 1, fixture.job.children.count { it.isActive })
                val requestsBeforeShutdown = fixture.platform.requests.toList()
                val readsBeforeShutdown = fixture.platform.reads

                when (shutdown) {
                    Shutdown.EmptyQueue -> {
                        fixture.player.stop()
                        fixture.player.clearMediaItems()
                        fixture.notification.update()
                        assertEquals(0, fixture.player.mediaItemCount)
                    }
                    Shutdown.Close -> fixture.notification.close()
                }

                assertTrue(fixture.platform.posted.isEmpty())
                assertEquals(1, fixture.platform.cancellations)
                assertEquals(1, fixture.platform.stops)
                assertFalse(fixture.notification.ongoing)
                assertFalse(fixture.notification.keepStarted)
                assertTrue("The service scope stays alive while the notification cancels its own work", fixture.job.isActive)
                assertTrue("Shutdown revokes its delayed job before either deadline", fixture.job.children.none { it.isActive })
                val callsAfterShutdown = fixture.platform.calls.toList()

                // Cross the content deadline and every possible confirmation/repair deadline.
                fixture.clock.advanceTimeBy(10_000)
                fixture.clock.runCurrent()

                assertEquals("No old or deferred version may be sent after shutdown", requestsBeforeShutdown, fixture.platform.requests)
                assertEquals("No cancelled readback may run after shutdown", readsBeforeShutdown, fixture.platform.reads)
                assertEquals(callsAfterShutdown, fixture.platform.calls)
                assertTrue(fixture.platform.posted.isEmpty())
            }
        }
    }

    private class Fixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val service = NotificationRenderService(context)
        val clock = TestCoroutineScheduler()
        val job = SupervisorJob()
        private val scope = CoroutineScope(job + StandardTestDispatcher(clock))
        val player = FakePlayer(Player.STATE_IDLE, false, emptyList(), 1f, 0).apply {
            setMediaItem(track("Original"))
            setPlaybackState(Player.STATE_READY)
        }
        private val instance = UUID.randomUUID().toString()
        val session = MediaSession.Builder(service, player)
            .setId("notification-render-$instance")
            .setSessionExtras(Bundle().apply { putString(PlaybackSessionProtocol.SESSION_ID, instance) })
            .build()
        val platform = RecordingPlatform()
        val notification = PlaybackForegroundNotification(
            service, session, player, scope,
            onStartNotAllowed = { error("A paused notification must not request foreground") },
            startService = { error("A paused notification must not start the service") },
            platform = platform,
        )

        fun assertRequestedPayload() {
            val requested = platform.requests.single()
            val payload = requested.notification
            assertEquals(NOTIFICATION_ID, requested.id)
            assertEquals(instance, payload.extras.getString(PlaybackNotificationMetadata.INSTANCE))
            assertEquals(1L, payload.extras.getLong(PlaybackNotificationMetadata.VERSION))
            assertEquals("Original", payload.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            assertEquals("Artist · Album", payload.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            assertEquals(PlaybackNotificationAction.Play.name, payload.extras.getString(PlaybackNotificationMetadata.ACTION))
            assertFalse(payload.extras.getBoolean(PlaybackNotificationMetadata.FOREGROUND))
            val play = payload.actions.single { it.title.toString() == context.getString(R.string.playback_notification_play) }
            assertEquals(
                playbackNotificationPendingIntent(service, service.javaClass, session.id, instance, PlaybackNotificationAction.Play),
                play.actionIntent,
            )
            assertTrue(play.actionIntent.isForegroundService)
        }

        fun awaitPendingContent() {
            clock.advanceTimeBy(750)
            clock.runCurrent()
            assertEquals("The real isPublished path must confirm the original payload", 1, platform.reads)
            assertEquals(1, platform.requests.size)

            player.setMediaItem(track("Deferred"))
            notification.update()
            clock.runCurrent()
            clock.advanceTimeBy(249)
            clock.runCurrent()

            assertEquals("Deferred", player.mediaMetadata.title?.toString())
            assertEquals("Content must still be pending immediately before its deadline", 1, platform.requests.size)
            assertEquals("Original", platform.posted.getValue(NOTIFICATION_ID).extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            assertEquals(1, platform.reads)
        }

        fun awaitPendingConfirmation() {
            assertTrue("The first publication is deliberately lost", platform.posted.isEmpty())
            clock.advanceTimeBy(750)
            clock.runCurrent()

            assertEquals("The real isPublished path must detect the lost notification", 1, platform.reads)
            assertEquals("A failed readback must actually request a repair", 2, platform.requests.size)
            assertEquals(listOf(1L, 1L), platform.requests.map { it.notification.extras.getLong(PlaybackNotificationMetadata.VERSION) })
            assertEquals("Original", platform.posted.getValue(NOTIFICATION_ID).extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            clock.advanceTimeBy(749)
            clock.runCurrent()
            assertEquals("The next confirmation must still be pending", 1, platform.reads)
            assertEquals(2, platform.requests.size)
        }
    }

    private class RecordingPlatform : PlaybackNotificationPlatform {
        val requests = mutableListOf<PostedPlaybackNotification>()
        val posted = mutableMapOf<Int, Notification>()
        val calls = mutableListOf<String>()
        var dropNextPublication = false
        var reads = 0
            private set
        var cancellations = 0
            private set
        var stops = 0
            private set

        override fun createChannel(channel: NotificationChannel) {
            calls += "channel:${channel.id}"
        }

        override fun notify(id: Int, notification: Notification) {
            calls += "notify:${notification.extras.getLong(PlaybackNotificationMetadata.VERSION)}"
            requests += PostedPlaybackNotification(id, notification)
            if (dropNextPublication) dropNextPublication = false else posted[id] = notification
        }

        override fun activeNotifications(): List<PostedPlaybackNotification> {
            calls += "read"
            reads++
            return posted.map { (id, payload) -> PostedPlaybackNotification(id, payload) }
        }

        override fun startForeground(id: Int, notification: Notification) {
            error("Paused rendering must not promote to foreground")
        }

        override fun stopForeground(remove: Boolean) {
            calls += "stopForeground:$remove"
        }

        override fun cancel(id: Int) {
            calls += "cancel:$id"
            cancellations++
            posted.remove(id)
        }

        override fun stopSelf() {
            calls += "stopSelf"
            stops++
        }

        fun cancelPendingIntents() {
            requests.flatMap { it.notification.actions.orEmpty().toList() }
                .map { it.actionIntent }.distinct().forEach { it.cancel() }
        }
    }

    /** Context/resources are real; no Service lifecycle, service start or platform foreground call. */
    private class NotificationRenderService(context: Context) : MediaSessionService() {
        init { attachBaseContext(context) }
        override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = null
    }

    private enum class Pending { Content, Confirmation }
    private enum class Shutdown { EmptyQueue, Close }

    private companion object {
        const val NOTIFICATION_ID = 1001

        fun track(title: String): MediaItem = MediaItem.Builder()
            .setMediaId("notification-track")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("Artist").setAlbumTitle("Album").build())
            .build()
    }
}
