package com.zaus.nullwave.core.playback.media3

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper.MediaStyle
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.impl.R
import com.zaus.nullwave.core.result.AppResult

/**
 * Owns the service's foreground/notification policy on Main. Media3's default notification manager
 * cannot promote an empty player during asynchronous queue preparation. A lease promotes before
 * that work starts, so a later ExoPlayer audio-focus request can run after the Activity stops.
 *
 * The service delegates notification updates here instead of calling the default manager. There
 * is one notification and no handoff race between a preparation notification and a player one.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackForegroundNotification(
    private val service: MediaSessionService,
    private val session: MediaSession,
    private val player: Player,
    private val onStartNotAllowed: () -> Unit,
    private val startService: () -> Unit,
) : Player.Listener, AutoCloseable {
    private val instanceId = requireNotNull(session.sessionExtras.getString(PlaybackSessionProtocol.SESSION_ID))
    private val manager = service.getSystemService(NotificationManager::class.java)
    private val policy = PlaybackForegroundPolicy()
    private var closed = false
    private var foreground = false
    private var shown: Appearance? = null

    val ongoing: Boolean get() = !closed && foreground

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, service.getString(R.string.playback_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        player.addListener(this)
    }

    /** Release in the service-owned command's finally block, never in the UI caller's finally. */
    fun acquire(): AppResult<AutoCloseable, PlaybackCommandError> {
        if (closed) return AppResult.Failure(PlaybackCommandError.ConnectionUnavailable)
        val lease = policy.acquire()
        val failure = try { render() }
        catch (failure: Throwable) {
            lease.close()
            throw failure
        }
        if (failure != null) {
            lease.close()
            return failure
        }
        return AppResult.Success(AutoCloseable {
            lease.close()
            update()
        })
    }

    fun update() {
        if (render() != null) onStartNotAllowed()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_TIMELINE_CHANGED, Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                Player.EVENT_PLAYER_ERROR, Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
            )) update()
    }

    /** Expected platform rejection is returned; manifest/configuration/programming failures escape. */
    // Session-associated media notifications are exempt from POST_NOTIFICATIONS permission.
    @SuppressLint("MissingPermission")
    private fun render(): AppResult.Failure<PlaybackCommandError>? {
        if (closed) return null
        val activeState = player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
        val needsForeground = policy.needsForeground(player.playWhenReady, activeState)
        val hasItem = player.currentMediaItem != null
        if (!needsForeground && !hasItem) {
            demote(remove = true)
            return null
        }
        val metadata = player.mediaMetadata
        val appearance = Appearance(
            title = metadata.title?.toString()?.takeIf(String::isNotBlank)
                ?: service.getString(if (policy.preparing && !hasItem) R.string.playback_notification_preparing else R.string.playback_notification_app_name),
            description = listOfNotNull(metadata.artist?.toString()?.takeIf(String::isNotBlank), metadata.albumTitle?.toString()?.takeIf(String::isNotBlank)).joinToString(" · "),
            playPause = PlaybackNotificationAction.playPause(player.playbackState, player.playWhenReady),
            preparing = policy.preparing && !hasItem,
            foreground = needsForeground,
            previous = hasItem && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
            toggle = hasItem && player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
            next = hasItem && player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT),
        )
        if (shown == appearance && foreground == needsForeground) return null
        val notification = notification(appearance)
        if (needsForeground) {
            try {
                if (!foreground) startService()
                ServiceCompat.startForeground(service, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                foreground = true
            } catch (failure: IllegalStateException) {
                if (Build.VERSION.SDK_INT >= 31 && failure is ForegroundServiceStartNotAllowedException) {
                    return AppResult.Failure(PlaybackCommandError.StartNotAllowed, failure)
                }
                throw failure
            }
        } else {
            manager.notify(NOTIFICATION_ID, notification)
            demote(remove = false)
        }
        shown = appearance
        return null
    }

    private fun notification(value: Appearance): Notification {
        val builder = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_playback_notification)
            .setContentTitle(value.title)
            .setContentText(value.description)
            .setContentIntent(session.sessionActivity)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(value.foreground)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val compact = mutableListOf<Int>()
        fun action(icon: Int, label: Int, action: PlaybackNotificationAction) {
            compact += compact.size
            builder.addAction(
                icon, service.getString(label),
                playbackNotificationPendingIntent(
                    service, service.javaClass, session.id,
                    instanceId, action,
                ),
            )
        }
        if (value.previous) action(android.R.drawable.ic_media_previous, R.string.playback_notification_previous, PlaybackNotificationAction.Previous)
        if (value.toggle) action(
            if (value.playPause == PlaybackNotificationAction.Pause) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (value.playPause == PlaybackNotificationAction.Pause) R.string.playback_notification_pause else R.string.playback_notification_play,
            value.playPause,
        )
        if (value.next) action(android.R.drawable.ic_media_next, R.string.playback_notification_next, PlaybackNotificationAction.Next)
        if (value.preparing) {
            action(android.R.drawable.ic_menu_close_clear_cancel, R.string.playback_notification_cancel, PlaybackNotificationAction.Stop)
            builder.setProgress(0, 0, true)
        }
        return builder.setStyle(MediaStyle(session).setShowActionsInCompactView(*compact.toIntArray())).build()
    }

    private fun demote(remove: Boolean) {
        ServiceCompat.stopForeground(service, if (remove) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH)
        foreground = false
        if (remove) {
            manager.cancel(NOTIFICATION_ID)
            shown = null
        }
        // A bound controller keeps the paused queue alive; an unbound idle service can terminate.
        service.stopSelf()
    }

    /** A stale/cold FGS start must fulfil its platform deadline before the empty service stops. */
    fun finishIdleStart() {
        if (closed || ongoing) return
        val shutdown = notification(Appearance(
            service.getString(R.string.playback_notification_app_name), "",
            PlaybackNotificationAction.Play, preparing = false, foreground = false,
            previous = false, toggle = false, next = false,
        ))
        try {
            // Do not call startService: that would enqueue another start while retiring this one.
            ServiceCompat.startForeground(service, NOTIFICATION_ID, shutdown, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (failure: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= 31 && failure is ForegroundServiceStartNotAllowedException) onStartNotAllowed()
            else throw failure
        } finally {
            demote(remove = true)
            // A late start can also arrive after a quick failure/pause of an existing bound queue.
            // Retiring the started state must not erase that queue's usable metadata notification.
            if (!closed && player.currentMediaItem != null) update()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        policy.close()
        player.removeListener(this)
        demote(remove = true)
    }

    private data class Appearance(
        val title: String,
        val description: String,
        val playPause: PlaybackNotificationAction,
        val preparing: Boolean,
        val foreground: Boolean,
        val previous: Boolean,
        val toggle: Boolean,
        val next: Boolean,
    )

    private companion object {
        const val CHANNEL_ID = "nullwave_playback"
        const val NOTIFICATION_ID = 1001
    }
}
