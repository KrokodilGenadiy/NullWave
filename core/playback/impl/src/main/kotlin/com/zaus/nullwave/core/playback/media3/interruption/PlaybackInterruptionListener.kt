package com.zaus.nullwave.core.playback.media3.interruption

import androidx.media3.common.Player
import com.zaus.nullwave.core.playback.runtime.PlaybackQueueOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** System interruptions share owner admission even when they do not change the raw playback intent. */
internal class PlaybackInterruptionListener(
    private val owner: PlaybackQueueOwner,
    private val scope: CoroutineScope,
    private val pause: () -> Unit,
) : Player.Listener {
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) revokePreparation()
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) revokePreparation()
    }

    /** Each delivered noisy event must cancel pending autoplay, including repeated events while paused. */
    fun onAudioBecomingNoisy() {
        scope.launch { owner.executeNative(pause) }
    }

    private fun revokePreparation() {
        // The engine already handles focus suppression/pause and later resume of the installed item.
        scope.launch { owner.executeNative {} }
    }
}
