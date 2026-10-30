package com.zaus.nullwave.core.playback.media3.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSessionService

/** Platform side effects only; rendering and payload verification stay in their service owner. */
internal interface PlaybackNotificationPlatform {
    fun createChannel(channel: NotificationChannel)
    fun notify(id: Int, notification: Notification)
    fun activeNotifications(): List<PostedPlaybackNotification>
    fun startForeground(id: Int, notification: Notification)
    fun stopForeground(remove: Boolean)
    fun cancel(id: Int)
    fun stopSelf()
}

internal data class PostedPlaybackNotification(val id: Int, val notification: Notification)

@androidx.annotation.OptIn(UnstableApi::class)
internal class AndroidPlaybackNotificationPlatform(private val service: MediaSessionService) : PlaybackNotificationPlatform {
    private val manager = service.getSystemService(NotificationManager::class.java)

    override fun createChannel(channel: NotificationChannel) {
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // Session-associated media notifications are permission-exempt.
    override fun notify(id: Int, notification: Notification) {
        manager.notify(id, notification)
    }

    override fun activeNotifications(): List<PostedPlaybackNotification> =
        manager.activeNotifications.map { PostedPlaybackNotification(it.id, it.notification) }

    override fun startForeground(id: Int, notification: Notification) {
        ServiceCompat.startForeground(service, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    override fun stopForeground(remove: Boolean) {
        ServiceCompat.stopForeground(service, if (remove) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH)
    }

    override fun cancel(id: Int) {
        manager.cancel(id)
    }

    override fun stopSelf() {
        service.stopSelf()
    }
}
