package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
 * The same thin playhead marks the exact position at rest and while scrubbing; no visible thumb
 * obscures it. [glowEnabled] adds a subtle halo to the played timeline and strengthens the playhead
 * during scrub. This accent takes over from Play/Pause; disabled timelines never glow.
 * [glowEnabled] can disable glow locally but cannot re-enable it when [NullWaveTheme.effects] forbids it.
 * For one accent across a player, the host observes drag on [interactionSource] and non-null
 * [onScrubPositionChange] (also keyboard input), suppressing its Play/Pause halo while either is active.
 * Key the host's drag observer to its source, and reset that source on media/duration/enabled changes.
 * Give each simultaneously displayed seek control its own interaction source.
 * Observing drag is visual only; cancellation/commit still use the synchronous session below.
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
    glowEnabled: Boolean = true,
) {
    require(mediaKey.isNotBlank()) { "SeekBar needs a stable media key" }
    require(durationMs == null || durationMs >= 0L) { "Unknown duration must be null, not negative" }
    val peaks = remember(waveform) {
        waveform.toList().also { values ->
            require(values.all { it.isFinite() && it in 0f..1f }) { "Waveform peaks must be finite and normalized" }
        }
    }
    val interactive = enabled && durationMs != null && durationMs > 0L
    val effects = NullWaveTheme.effects.withRestrictions(glowEnabled = glowEnabled)
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
        val dragged by key(seekSource) { seekSource.collectIsDraggedAsState() }
        DisposableEffect(session) { onDispose { cancel() } }
        val scrubbing = dragged || session.pendingPositionMs != null
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
                    // Fixed invisible slot preserves Material's endpoints and 48 dp interaction area.
                    // SeekTrack draws a precise playhead in both states instead of a wide thumb.
                    Box(Modifier.size(SliderNodeSize))
                },
                track = { SeekTrack(it, interactive, peaks, scrubbing, effects.glowEnabled) },
            )
            NullWaveTimeLabels(positionText, durationText,
                endDescription = if (durationMs == null) stringResource(R.string.nw_time_duration_unknown)
                else stringResource(R.string.nw_time_duration, durationText))
        }
    }
}
