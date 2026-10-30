package com.zaus.nullwave.core.playback.media3.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.media3.engine.Media3QueueCommitTarget
import com.zaus.nullwave.core.playback.media3.engine.OwnerSessionPlayer
import com.zaus.nullwave.core.playback.media3.interruption.PlaybackInterruptionListener
import com.zaus.nullwave.core.playback.media3.interruption.PlaybackNoisyReceiver
import com.zaus.nullwave.core.playback.media3.notification.PlaybackForegroundNotification
import com.zaus.nullwave.core.playback.media3.session.PlaybackSessionCallback
import com.zaus.nullwave.core.playback.media3.session.PlaybackSessionProtocol
import com.zaus.nullwave.core.playback.preparation.QueuePreparer
import com.zaus.nullwave.core.playback.runtime.PlaybackIdentities
import com.zaus.nullwave.core.playback.runtime.PlaybackQueueOwner
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** One service-owned engine/session. UI controller release never releases this player. */
@androidx.annotation.OptIn(UnstableApi::class)
class NullWavePlaybackService : MediaSessionService() {
    private var runtime: ServicePlaybackRuntime? = null

    override fun onCreate() {
        super.onCreate()
        val component = (application as PlaybackServiceComponentOwner).playbackServiceComponent
        runtime = ServicePlaybackRuntime(this, component.playbackServiceDependencies)
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() { runtime?.onStartNotAllowed() }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = runtime?.session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val current = checkNotNull(runtime) { "Service initialization must precede start commands." }
        val decision = PlaybackServiceStartPolicy.decide(
            current.instanceId, intent?.action == Intent.ACTION_MEDIA_BUTTON,
            intent?.getStringExtra(PlaybackSessionProtocol.START_INSTANCE_ID), current.ongoing,
        )
        if (decision == PlaybackServiceStartPolicy.Decision.DelegateMediaButton) {
            super.onStartCommand(intent, flags, startId)
        } else {
            // Call the superclass lifecycle without dispatching an obsolete notification action.
            super.onStartCommand(null, flags, startId)
            if (decision == PlaybackServiceStartPolicy.Decision.FinishIdleStart) current.finishIdleStart()
        }
        // In-memory queues cannot be restored by a null-intent sticky restart.
        return START_NOT_STICKY
    }

    override fun onUpdateNotificationAsync(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ): ListenableFuture<Void?> {
        // The default manager cannot represent an empty-queue preflight foreground lease.
        runtime?.updateNotification()
        return Futures.immediateFuture(null)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Media3's isPlaybackOngoing reads its default notification manager, which we replace.
        if (runtime?.keepStarted != true) stopSelf()
    }

    override fun onDestroy() {
        try { runtime?.close() }
        finally {
            runtime = null
            super.onDestroy()
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class ServicePlaybackRuntime(service: NullWavePlaybackService, private val dependencies: PlaybackServiceDependencies) {
    private val scope = CoroutineScope(SupervisorJob() + dependencies.main)
    private val identities = PlaybackIdentities()
    private val raw = ExoPlayer.Builder(service)
        .setLooper(Looper.getMainLooper())
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()
    private val target = Media3QueueCommitTarget(raw, identities, dependencies.registry, dependencies.clock)
    private val owner = PlaybackQueueOwner(scope, dependencies.main, QueuePreparer(dependencies.resolver, dependencies.default), target, identities)
    private val foreground: PlaybackForegroundNotification
    private val startGuard = PlaybackStartGuard(
        acquire = { foreground.acquire() },
        onDenied = { stopAfterDeniedStart() },
    )
    private val player = OwnerSessionPlayer(raw, owner, target, scope, startGuard)
    private val interruption = PlaybackInterruptionListener(owner, scope, raw::pause)
    private val noisy: PlaybackNoisyReceiver
    val session: MediaSession
    private var closed = false

    init {
        var builtSession: MediaSession? = null
        var builtForeground: PlaybackForegroundNotification? = null
        var builtNoisy: PlaybackNoisyReceiver? = null
        try {
            raw.addListener(interruption)
            val builder = MediaSession.Builder(service, player)
                .setId("NullWave")
                .setSessionExtras(android.os.Bundle().apply { putString(PlaybackSessionProtocol.SESSION_ID, identities.sessionId.value) })
                .setCallback(PlaybackSessionCallback(::execute, dependencies.tickets, scope))
            service.packageManager.getLaunchIntentForPackage(service.packageName)?.let { launch ->
                builder.setSessionActivity(PendingIntent.getActivity(service, 0, launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            session = builder.build().also { builtSession = it }
            foreground = PlaybackForegroundNotification(service, session, raw, scope, ::onStartNotAllowed, startService = {
                ContextCompat.startForegroundService(
                    service,
                    Intent(service, NullWavePlaybackService::class.java)
                        .setAction(PlaybackSessionProtocol.START_SERVICE)
                        .setIdentifier(instanceId)
                        .putExtra(PlaybackSessionProtocol.START_INSTANCE_ID, instanceId),
                )
            }).also { builtForeground = it }
            // One noisy event boundary; Exo's state-deduplicated noisy receiver is intentionally off.
            noisy = PlaybackNoisyReceiver(service, interruption::onAudioBecomingNoisy).also { builtNoisy = it }
        } catch (failure: Throwable) {
            // A failed platform registration must not hide its cause or leak the earlier resources.
            val cleanup = listOf<() -> Unit>(
                { builtNoisy?.close() },
                { builtForeground?.close() },
                { raw.removeListener(interruption) },
                { target.close() },
                { builtSession?.release() },
                { player.release() },
                { scope.cancel() },
            )
            cleanup.forEach { release ->
                try { release() }
                catch (secondary: Throwable) { if (secondary !== failure) failure.addSuppressed(secondary) }
            }
            throw failure
        }
    }

    val ongoing: Boolean get() = foreground.ongoing
    val keepStarted: Boolean get() = foreground.keepStarted
    val instanceId: String get() = identities.sessionId.value

    fun updateNotification() { if (!closed) foreground.update() }
    fun finishIdleStart() { if (!closed) foreground.finishIdleStart() }

    private suspend fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> {
        val needsForeground = when (command) {
            // Even a paused replacement owns preparation and a started service beyond UI release.
            is PlaybackCommand.ReplaceQueue -> true
            is PlaybackCommand.SetPlayRequested -> command.value
            is PlaybackCommand.Retry -> raw.playWhenReady
            is PlaybackCommand.SeekTo, PlaybackCommand.Next, PlaybackCommand.Previous -> raw.playWhenReady
            else -> false
        }
        return startGuard.run(needsForeground) { owner.execute(command) }
    }

    private suspend fun stopAfterDeniedStart() {
        owner.executeNative {
            raw.pause()
            raw.stop()
            dependencies.registry.startNotAllowed(identities.sessionId)
        }
    }

    fun onStartNotAllowed() {
        if (closed) return
        scope.launch { stopAfterDeniedStart() }
    }

    fun close() {
        if (closed) return
        closed = true
        try { noisy.close() }
        finally {
            try { foreground.close() }
            finally {
                // Revoke lifetime even if platform cleanup fails or admission is still occupied.
                scope.cancel()
                // Immediate on the application looper; if reentrant, admission finishes before release.
                scope.launch(dependencies.main + NonCancellable, start = CoroutineStart.UNDISPATCHED) {
                    try { owner.close() }
                    finally {
                        raw.removeListener(interruption)
                        try { target.close() }
                        finally {
                            try { session.release() }
                            finally { player.release() }
                        }
                    }
                }
            }
        }
    }
}
