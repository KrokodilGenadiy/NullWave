package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.components.control.slider.NullWaveSliderControl
import com.zaus.nullwave.core.designsystem.components.control.slider.NullWaveSliderNode
import com.zaus.nullwave.core.designsystem.components.control.slider.SliderNodeSize
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Timeline with time labels. [mediaKey] identifies the current item and cancels stale scrubbing.
 * Null/zero duration disables seeking; negative duration is invalid (map engine sentinels to null).
 * [formatTime] is a pure, localized formatter of nonnegative milliseconds, owned by the caller.
 * Playback updates cannot overwrite a pending scrub. [onSeek] fires once at completion; the caller
 * should immediately reflect that position in its UI while the engine applies the command.
 * [onScrubPositionChange] receives preview milliseconds, then null at completion/cancellation.
 * [waveform] is an immutable list of normalized peaks, not audio samples. Empty uses a plain track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NullWaveSeekBar(
    mediaKey: String,
    positionMs: Long,
    durationMs: Long?,
    onSeek: (Long) -> Unit,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = stringResource(R.string.nw_seek_label),
    waveform: List<Float> = emptyList(),
    onScrubPositionChange: (Long?) -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
) {
    require(mediaKey.isNotBlank()) { "SeekBar needs a stable media key" }
    require(durationMs == null || durationMs >= 0L) { "Unknown duration must be null, not negative" }
    val peaks = remember(waveform) {
        waveform.toList().also { values ->
            require(values.all { it.isFinite() && it in 0f..1f }) { "Waveform peaks must be finite and normalized" }
        }
    }
    val interactive = enabled && durationMs != null && durationMs > 0L
    // Recreate the actual Material gesture node too, so a gesture cannot transfer to another item.
    key(mediaKey, durationMs, enabled) {
        val session = remember { SeekSession() }
        val currentOnScrub by rememberUpdatedState(onScrubPositionChange)
        val source = interactionSource ?: remember { MutableInteractionSource() }
        val cancel = remember(session) {
            {
                if (session.pendingPositionMs != null) {
                    session.cancel()
                    currentOnScrub(null)
                }
            }
        }
        val seekSource = remember(source, session) { SeekInteractionSource(source, cancel) }
        DisposableEffect(session) { onDispose { cancel() } }
        val position = session.displayedPosition(positionMs, durationMs)
        val fraction = if (durationMs != null && durationMs > 0L) (position.toDouble() / durationMs).toFloat() else 0f
        val positionText = formatTime(position)
        val durationText = durationMs?.let(formatTime) ?: stringResource(R.string.nw_time_unknown)
        val description = if (durationMs == null) stringResource(R.string.nw_seek_unknown, positionText)
            else stringResource(R.string.nw_seek_position, positionText, durationText)
        Column(modifier, verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs)) {
            NullWaveSliderControl(
                fraction,
                onValueChange = { if (interactive) currentOnScrub(session.change(it, requireNotNull(durationMs))) },
                label = label,
                modifier = Modifier.fillMaxWidth(),
                enabled = interactive,
                valueDescription = description,
                interactionSource = seekSource,
                onValueChangeFinished = {
                    session.finish()?.let { target ->
                        currentOnScrub(null)
                        onSeek(target)
                    }
                },
                thumb = {
                    // Fixed slot keeps timeline endpoints stable between resting and scrubbing.
                    Box(Modifier.size(SliderNodeSize)) {
                        if (session.pendingPositionMs != null) NullWaveSliderNode(interactive)
                    }
                },
                track = { SeekTrack(it, interactive, peaks, session.pendingPositionMs != null) },
            )
            NullWaveTimeLabels(positionText, durationText,
                endDescription = if (durationMs == null) stringResource(R.string.nw_time_duration_unknown)
                else stringResource(R.string.nw_time_duration, durationText))
        }
    }
}
