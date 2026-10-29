package com.zaus.nullwave.core.playback.media3

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Builds the real immutable platform actions; no Activity, engine or audio permission is needed. */
@RunWith(AndroidJUnit4::class)
class PlaybackNotificationActionInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun playAndPauseIntentsMatchThePresentedActionInsteadOfSendingToggle() {
        for (action in listOf(PlaybackNotificationAction.Play, PlaybackNotificationAction.Pause)) {
            val intent = playbackNotificationIntent(context, NullWavePlaybackService::class.java, SESSION, FIRST, action)
            assertEquals(Intent.ACTION_MEDIA_BUTTON, intent.action)
            assertEquals(ComponentName(context, NullWavePlaybackService::class.java), intent.component)
            val key = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)!!
            assertEquals(KeyEvent.ACTION_DOWN, key.action)
            assertEquals(action.keyCode, key.keyCode)
            assertNotEquals(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, key.keyCode)
            assertEquals(FIRST, intent.getStringExtra(PlaybackSessionProtocol.START_INSTANCE_ID))
        }
    }

    @Test
    fun nativePreviousNextAndCancelKeepTheirCommandsAndCarryTheServiceInstance() {
        for (action in listOf(PlaybackNotificationAction.Previous, PlaybackNotificationAction.Next, PlaybackNotificationAction.Stop)) {
            val intent = playbackNotificationIntent(context, NullWavePlaybackService::class.java, SESSION, FIRST, action)
            val key = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)!!
            assertEquals(action.keyCode, key.keyCode)
            assertEquals(FIRST, intent.getStringExtra(PlaybackSessionProtocol.START_INSTANCE_ID))
            assertEquals(FIRST, intent.identifier)
        }
    }

    @Test
    fun playUsesForegroundPendingIntentAndAStaleInstanceCannotBeUpdatedIntoANewOne() {
        val oldPlay = playbackNotificationPendingIntent(context, NullWavePlaybackService::class.java, SESSION, FIRST, PlaybackNotificationAction.Play)
        val pause = playbackNotificationPendingIntent(context, NullWavePlaybackService::class.java, SESSION, FIRST, PlaybackNotificationAction.Pause)
        val newPlay = playbackNotificationPendingIntent(context, NullWavePlaybackService::class.java, SESSION, SECOND, PlaybackNotificationAction.Play)
        try {
            assertTrue(oldPlay.isForegroundService)
            assertFalse(pause.isForegroundService)
            assertTrue(pause.isService)
            if (Build.VERSION.SDK_INT >= 31) {
                assertTrue(oldPlay.isImmutable)
                assertTrue(pause.isImmutable)
            }
            assertNotEquals(oldPlay, pause)
            assertNotEquals(oldPlay, newPlay)
            val oldIntent = playbackNotificationIntent(context, NullWavePlaybackService::class.java, SESSION, FIRST, PlaybackNotificationAction.Play)
            val newIntent = playbackNotificationIntent(context, NullWavePlaybackService::class.java, SESSION, SECOND, PlaybackNotificationAction.Play)
            assertEquals(oldIntent.data, newIntent.data)
            assertFalse(oldIntent.filterEquals(newIntent))
        } finally {
            oldPlay.cancel()
            pause.cancel()
            newPlay.cancel()
        }
    }

    private companion object {
        const val SESSION = "NullWave"
        const val FIRST = "notification-instance-a"
        const val SECOND = "notification-instance-b"
    }
}
