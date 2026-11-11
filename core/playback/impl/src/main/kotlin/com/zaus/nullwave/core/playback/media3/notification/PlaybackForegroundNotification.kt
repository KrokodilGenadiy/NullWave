package com.zaus.nullwave.core.playback.media3.notification

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper.MediaStyle
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.impl.R
import com.zaus.nullwave.core.playback.media3.session.PlaybackSessionProtocol
import com.zaus.nullwave.core.result.AppResult
import kotlinx.coroutines.CoroutineScope

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
    scope: CoroutineScope,
    private val onStartNotAllowed: () -> Unit,
    private val startService: () -> Unit,
    private val platform: PlaybackNotificationPlatform = AndroidPlaybackNotificationPlatform(service),
) : Player.Listener, AutoCloseable {
    private val instanceId = requireNotNull(session.sessionExtras.getString(PlaybackSessionProtocol.SESSION_ID))
    private val policy = PlaybackForegroundPolicy()
    private var closed = false
    private var foreground = false
    // notify/startForeground returning is a request, not a delivery acknowledgement from NMS.
    private var lastRequested: Publication? = null
    private var nextVersion = 0L
    private val updates = PlaybackNotificationUpdates(scope)

    val ongoing: Boolean get() = !closed && foreground
    val keepStarted: Boolean get() = !closed && policy.shouldKeepStarted(player.mediaItemCount > 0)

    init {
        platform.createChannel(
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
    private fun render(deferContent: Boolean = true): AppResult.Failure<PlaybackCommandError>? {
        if (closed) return null
        val appearance = appearance()
        if (appearance == null) {
            demote(remove = true)
            return null
        }
        val previous = lastRequested
        if (previous?.appearance == appearance && foreground == appearance.foreground) {
            updates.cancelScheduled()
            confirm(previous)
            return null
        }
        // First promotion and demotion/removal must run immediately. Only content changes at the
        // same priority are conflated, avoiding a burst of preparation/timeline/Ready updates.
        if (deferContent && previous != null && foreground == appearance.foreground) {
            updates.cancelConfirmation()
            updates.schedule { if (render(deferContent = false) != null) onStartNotAllowed() }
            return null
        }
        updates.cancelScheduled()
        updates.cancelConfirmation()
        val version = ++nextVersion
        val publication = Publication(appearance, version, notification(appearance, version))
        val failure = publish(publication)
        if (failure != null) return failure
        lastRequested = publication
        confirm(publication)
        return null
    }

    private fun appearance(): Appearance? {
        val activeState = player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
        val needsForeground = policy.needsForeground(player.playWhenReady, activeState)
        val hasItem = player.currentMediaItem != null
        if (!needsForeground && !hasItem) return null
        val metadata = player.mediaMetadata
        return Appearance(
            title = metadata.title?.toString()?.takeIf(String::isNotBlank)
                ?: service.getString(if (policy.preparing && !hasItem) R.string.playback_notification_preparing else R.string.playback_notification_app_name),
            // Same rule as the in-app rows: an item always names its artist slot. Session metadata keeps
            // the real null, so external controllers never receive a localized placeholder as a tag.
            description = if (!hasItem) "" else listOfNotNull(
                metadata.artist?.toString()?.takeIf(String::isNotBlank)
                    ?: service.getString(R.string.playback_notification_unknown_artist),
                metadata.albumTitle?.toString()?.takeIf(String::isNotBlank),
            ).joinToString(" · "),
            playPause = PlaybackNotificationAction.playPause(player.playbackState, player.playWhenReady),
            preparing = policy.preparing && !hasItem,
            foreground = needsForeground,
            previous = hasItem && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
            toggle = hasItem && player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
            next = hasItem && player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT),
        )
    }

    @SuppressLint("MissingPermission") // A media-session notification is permission-exempt.
    private fun publish(publication: Publication): AppResult.Failure<PlaybackCommandError>? {
        val appearance = publication.appearance
        val notification = publication.notification
        try {
            if (appearance.foreground) {
                if (!foreground) startService()
                updateForegroundNotification(notification)
                foreground = true
            } else {
                publishRetainedNotification(
                    notification = notification,
                    wasForeground = foreground,
                    updateForeground = ::updateForegroundNotification,
                    notify = { platform.notify(NOTIFICATION_ID, it) },
                    detach = { demote(remove = false) },
                )
            }
        } catch (failure: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= 31 && failure is ForegroundServiceStartNotAllowedException) {
                return AppResult.Failure(PlaybackCommandError.StartNotAllowed, failure)
            }
            throw failure
        }
        Log.d(TAG, "requested instance=$instanceId version=${publication.version} " +
            "action=${appearance.primaryAction} foreground=${appearance.foreground}")
        return null
    }

    private fun confirm(publication: Publication) {
        updates.confirm(
            version = publication.version,
            isCurrent = { !closed && lastRequested?.version == it && appearance() == publication.appearance },
            isPublished = { isPublished(publication) },
            republish = {
                // Read the raw player again: a late event may have changed it since publication.
                if (appearance() != publication.appearance) update()
                else if (publish(publication) != null) {
                    updates.cancelConfirmation()
                    onStartNotAllowed()
                }
            },
            onUnconfirmed = {
                Log.w(TAG, "unconfirmed instance=$instanceId version=$it action=${publication.appearance.primaryAction} " +
                    "posted=${platform.activeNotifications().joinToString { posted ->
                        val value = posted.notification
                        "${posted.id}:${value.extras.getString(PlaybackNotificationMetadata.INSTANCE)}:" +
                            "${value.extras.getLong(PlaybackNotificationMetadata.VERSION, -1)}:" +
                            value.actions.orEmpty().joinToString { action -> action.title.toString() }
                    }}")
            },
        )
    }

    private fun isPublished(publication: Publication): Boolean {
        val actual = platform.activeNotifications().firstOrNull { it.id == NOTIFICATION_ID }?.notification ?: return false
        if (actual.extras.getString(PlaybackNotificationMetadata.INSTANCE) != instanceId ||
            actual.extras.getLong(PlaybackNotificationMetadata.VERSION, -1) != publication.version
        ) return false
        val expected = publication.notification
        return actual.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty() ==
            expected.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty() &&
            actual.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty() ==
            expected.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty() &&
            actual.actions.orEmpty().map { it.title.toString() to it.actionIntent } ==
            expected.actions.orEmpty().map { it.title.toString() to it.actionIntent }
    }

    /** Refreshing an existing foreground payload does not enqueue another service start. */
    private fun updateForegroundNotification(notification: Notification) {
        platform.startForeground(NOTIFICATION_ID, notification)
    }

    private fun notification(value: Appearance, version: Long): Notification {
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
            .addExtras(Bundle().apply {
                putString(PlaybackNotificationMetadata.INSTANCE, instanceId)
                putLong(PlaybackNotificationMetadata.VERSION, version)
                putString(PlaybackNotificationMetadata.ACTION, value.primaryAction?.name)
                putBoolean(PlaybackNotificationMetadata.FOREGROUND, value.foreground)
            })
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
        platform.stopForeground(remove)
        foreground = false
        if (remove) {
            updates.cancelScheduled()
            updates.cancelConfirmation()
            platform.cancel(NOTIFICATION_ID)
            lastRequested = null
        }
        // Foreground priority and service ownership are independent: Pause/native Stop keep a
        // queue available for Resume after the last UI connection has gone away.
        if (!keepStarted) platform.stopSelf()
    }

    /** A stale/cold FGS start must fulfil its platform deadline before the empty service stops. */
    fun finishIdleStart() {
        if (closed || ongoing) return
        val shutdown = notification(Appearance(
            service.getString(R.string.playback_notification_app_name), "",
            PlaybackNotificationAction.Play, preparing = false, foreground = false,
            previous = false, toggle = false, next = false,
        ), ++nextVersion)
        try {
            // Do not call startService: that would enqueue another start while retiring this one.
            updateForegroundNotification(shutdown)
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
        updates.close()
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
    ) {
        val primaryAction: PlaybackNotificationAction?
            get() = when {
                preparing -> PlaybackNotificationAction.Stop
                toggle -> playPause
                else -> null
            }
    }

    private data class Publication(val appearance: Appearance, val version: Long, val notification: Notification)

    private companion object {
        const val TAG = "NWPlaybackNotification"
        const val CHANNEL_ID = "nullwave_playback"
        const val NOTIFICATION_ID = 1001
    }
}
