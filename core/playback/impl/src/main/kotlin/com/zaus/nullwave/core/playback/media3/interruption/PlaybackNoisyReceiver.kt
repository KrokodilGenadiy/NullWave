package com.zaus.nullwave.core.playback.media3.interruption

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat

/** Main-confined service receiver; noisy is an event, not a deduplicated Player state change. */
internal class PlaybackNoisyReceiver(
    context: Context,
    private val onNoisy: () -> Unit,
) : BroadcastReceiver(), AutoCloseable {
    private val application = context.applicationContext
    private var closed = false

    init {
        ContextCompat.registerReceiver(
            application, this, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!closed && intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onNoisy()
    }

    override fun close() {
        if (closed) return
        closed = true
        application.unregisterReceiver(this)
    }
}
