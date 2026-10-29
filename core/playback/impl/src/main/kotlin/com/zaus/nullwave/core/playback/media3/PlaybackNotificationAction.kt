package com.zaus.nullwave.core.playback.media3

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSessionService
import androidx.media3.session.PlaybackPendingIntentBuilder

/** Explicit actions keep the notification's label and native event consistent, including replay. */
internal enum class PlaybackNotificationAction(val keyCode: Int) {
    Previous(KeyEvent.KEYCODE_MEDIA_PREVIOUS),
    Play(KeyEvent.KEYCODE_MEDIA_PLAY),
    Pause(KeyEvent.KEYCODE_MEDIA_PAUSE),
    Next(KeyEvent.KEYCODE_MEDIA_NEXT),
    Stop(KeyEvent.KEYCODE_MEDIA_STOP);

    val startsForeground: Boolean get() = this == Play

    companion object {
        /** Suppression does not clear play intent: active/buffering playback still offers Pause. */
        fun playPause(playbackState: Int, playRequested: Boolean): PlaybackNotificationAction =
            if (playRequested && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)) Pause
            else Play
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun playbackNotificationIntent(
    context: Context,
    serviceClass: Class<out MediaSessionService>,
    sessionId: String,
    instanceId: String,
    action: PlaybackNotificationAction,
): Intent {
    val command = when (action) {
        PlaybackNotificationAction.Play, PlaybackNotificationAction.Pause -> Player.COMMAND_PLAY_PAUSE
        PlaybackNotificationAction.Previous -> Player.COMMAND_SEEK_TO_PREVIOUS
        PlaybackNotificationAction.Next -> Player.COMMAND_SEEK_TO_NEXT
        PlaybackNotificationAction.Stop -> Player.COMMAND_STOP
    }
    return PlaybackPendingIntentBuilder.createMediaButtonIntent(
        context, command,
        Bundle().apply { putString(PlaybackSessionProtocol.START_INSTANCE_ID, instanceId) },
        sessionId, serviceClass,
    ).apply {
        // Extras are not PendingIntent identity. Keep old notifications bound to their old instance.
        identifier = instanceId
        putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, action.keyCode))
    }
}

internal fun playbackNotificationPendingIntent(
    context: Context,
    serviceClass: Class<out MediaSessionService>,
    sessionId: String,
    instanceId: String,
    action: PlaybackNotificationAction,
): PendingIntent {
    val intent = playbackNotificationIntent(context, serviceClass, sessionId, instanceId, action)
    val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    return if (action.startsForeground) PendingIntent.getForegroundService(context, action.keyCode, intent, flags)
    else PendingIntent.getService(context, action.keyCode, intent, flags)
}
