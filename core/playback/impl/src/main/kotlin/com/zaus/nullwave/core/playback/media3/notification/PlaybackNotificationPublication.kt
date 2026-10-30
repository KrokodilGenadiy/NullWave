package com.zaus.nullwave.core.playback.media3.notification

/** App-owned identity survives system notification copies and distinguishes service incarnations. */
internal object PlaybackNotificationMetadata {
    const val INSTANCE = "com.zaus.nullwave.playback.notification.INSTANCE"
    const val VERSION = "com.zaus.nullwave.playback.notification.VERSION"
    const val ACTION = "com.zaus.nullwave.playback.notification.ACTION"
    const val FOREGROUND = "com.zaus.nullwave.playback.notification.FOREGROUND"
}

/**
 * Updates both system copies before demotion. Android can replay its saved foreground notification
 * while detaching it, independently of NotificationManager.notify. Updating that saved payload
 * prevents an older Pause action from replacing the newly posted Play action.
 *
 * Only refresh an already-foreground service; an ordinary retained update must not promote it.
 * Keep notify-before-detach, as Media3 does to avoid missed updates on older platform versions.
 */
internal inline fun <N> publishRetainedNotification(
    notification: N,
    wasForeground: Boolean,
    updateForeground: (N) -> Unit,
    notify: (N) -> Unit,
    detach: () -> Unit,
) {
    if (wasForeground) updateForeground(notification)
    notify(notification)
    detach()
}
