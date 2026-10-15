package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToLong

/** Transient UI position; never a playback clock or a saved playback state. */
internal class SeekSession {
    var pendingPositionMs: Long? by mutableStateOf(null)
        private set

    fun change(fraction: Float, durationMs: Long): Long {
        require(durationMs > 0 && fraction.isFinite())
        return (durationMs.toDouble() * fraction.coerceIn(0f, 1f)).roundToLong().coerceIn(0L, durationMs)
            .also { pendingPositionMs = it }
    }

    fun displayedPosition(positionMs: Long, durationMs: Long?): Long =
        (pendingPositionMs ?: positionMs).coerceIn(0L, durationMs ?: Long.MAX_VALUE)

    fun finish(): Long? = pendingPositionMs.also { pendingPositionMs = null }
    fun cancel() { pendingPositionMs = null }
}

/**
 * Material calls completion even for a cancelled drag. Foundation emits Cancel before completion;
 * intercept synchronously, rather than racing a LaunchedEffect interaction collector against it.
 */
internal class SeekInteractionSource(
    private val delegate: MutableInteractionSource,
    private val onCancel: () -> Unit,
) : MutableInteractionSource {
    override val interactions = delegate.interactions
    override suspend fun emit(interaction: Interaction) {
        if (interaction is DragInteraction.Cancel || interaction is FocusInteraction.Unfocus) onCancel()
        delegate.emit(interaction)
    }
    override fun tryEmit(interaction: Interaction): Boolean {
        if (interaction is DragInteraction.Cancel || interaction is FocusInteraction.Unfocus) onCancel()
        return delegate.tryEmit(interaction)
    }
}
