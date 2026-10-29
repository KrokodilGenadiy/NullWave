package com.zaus.nullwave.core.playback.media3.service

import android.app.NotificationManager
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.media3.session.PlaybackSessionProtocol
import com.zaus.nullwave.core.playback.media3.testing.PlaybackTestActivity
import com.zaus.nullwave.core.playback.media3.testing.PlaybackTestApplication
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual cold FGS starts, without a bound controller, audio or a user media-library permission. */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceStartInstrumentedTest {
    @Test
    fun staleSelfStartRetiresTheNewEmptyServiceAndRemovesItsShutdownNotification(): Unit = runBlocking {
        coldStartRetires { app ->
            Intent(app, NullWavePlaybackService::class.java)
                .setAction(PlaybackSessionProtocol.START_SERVICE)
                .putExtra(PlaybackSessionProtocol.START_INSTANCE_ID, "expired-service")
        }
    }

    @Test
    fun unmarkedEmptyStartDoesNotLeaveAStartedOrForegroundService(): Unit = runBlocking {
        coldStartRetires { app -> Intent(app, NullWavePlaybackService::class.java) }
    }

    private suspend fun coldStartRetires(intent: (PlaybackTestApplication) -> Intent) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PlaybackTestApplication
        val activity = ActivityScenario.launch(PlaybackTestActivity::class.java)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val attached = CompletableDeferred<PlaybackSessionId>()
        val detached = CompletableDeferred<Unit>()
        try {
            withContext(Dispatchers.Main.immediate) {
                assertNull("The preceding test must release its service", app.dependencies.registry.snapshot.value)
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    app.dependencies.registry.snapshot.collect { snapshot ->
                        if (snapshot != null) attached.complete(snapshot.sessionId)
                        else if (attached.isCompleted) detached.complete(Unit)
                    }
                }
                ContextCompat.startForegroundService(app, intent(app))
            }
            withTimeout(15_000.milliseconds) {
                assertNotEquals("expired-service", attached.await().value)
                detached.await()
            }
            assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
        } finally {
            withContext(Dispatchers.Main.immediate) { app.stopService(Intent(app, NullWavePlaybackService::class.java)) }
            scope.cancel()
            activity.close()
        }
    }
}
