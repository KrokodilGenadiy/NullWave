package com.zaus.nullwave.core.playback.media3.service

import android.app.ActivityManager
import android.app.Notification
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
import com.zaus.nullwave.core.playback.media3.testing.withPlaybackTestCleanup
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
        withPlaybackTestCleanup(
            cleanup = listOf(
                {
                    withContext(Dispatchers.Main.immediate) { app.stopService(Intent(app, NullWavePlaybackService::class.java)) }
                    awaitRetired(app)
                },
                { scope.cancel() },
                { activity.close() },
            ),
        ) {
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
            // Runtime detach is not an acknowledgement from ActivityManager/NotificationManager.
            // Wait for both platform publications without creating a controller or another start.
            awaitRetired(app)
        }
    }

    private suspend fun awaitRetired(app: PlaybackTestApplication) {
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            withTimeout(5_000.milliseconds) {
                while (app.dependencies.registry.snapshot.value != null ||
                    runningService(app) != null || manager.activeNotifications.isNotEmpty()
                ) delay(25)
            }
        } catch (failure: TimeoutCancellationException) {
            val service = runningService(app)
            val notifications = manager.activeNotifications.joinToString { posted ->
                val notification = posted.notification
                "id=${posted.id}, title=${notification.extras.getCharSequence(Notification.EXTRA_TITLE)}, " +
                    "actions=${notification.actions.orEmpty().map { it.title.toString() }}, flags=${notification.flags}"
            }
            throw AssertionError(
                "Empty cold start must retire its service and notifications; " +
                    "service=${service?.service}, started=${service?.started}, foreground=${service?.foreground}, " +
                    "snapshot=${app.dependencies.registry.snapshot.value}, notifications=[$notifications]",
                failure,
            )
        }
    }

    @Suppress("DEPRECATION") // The platform still reports this app's own services.
    private fun runningService(app: PlaybackTestApplication): ActivityManager.RunningServiceInfo? =
        app.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE).singleOrNull {
            it.service.className == NullWavePlaybackService::class.java.name
        }
}
