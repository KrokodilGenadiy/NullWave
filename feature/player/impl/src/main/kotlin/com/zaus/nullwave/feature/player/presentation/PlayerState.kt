package com.zaus.nullwave.feature.player.presentation

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackProblem
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot

/** UI cache is display-only. The service remains the owner of queue, transport and position. */
internal data class PlayerState(
    val connection: PlaybackConnection = PlaybackConnection.Disconnected,
    val lastKnownSnapshot: PlaybackSnapshot? = null,
    val progress: PlaybackProgress? = null,
    val commandNotice: PlayerCommandNotice? = null,
    val dismissedProblem: PlayerProblemKey? = null,
) : UiState {
    val liveSnapshot: PlaybackSnapshot? get() = (connection as? PlaybackConnection.Connected)?.snapshot
    val displaySnapshot: PlaybackSnapshot? get() = liveSnapshot ?: lastKnownSnapshot

    /** Host should remember this lookup by display queue, outside frame-level position rendering. */
    val currentTrack: MediaTrack? get() = displaySnapshot?.queue?.let { queue ->
        queue.entries.firstOrNull { it.entryId == queue.current?.entryId }?.track
    }
    val playRequested: Boolean get() = liveSnapshot?.transport?.playRequested == true
    val problemKey: PlayerProblemKey? get() = liveSnapshot?.let { snapshot ->
        snapshot.transport.problem?.let { PlayerProblemKey(snapshot.sessionId, it.id) }
    }
    val visibleProblem: PlaybackProblem? get() =
        liveSnapshot?.transport?.problem?.takeUnless { commandNotice != null || problemKey == dismissedProblem }
    val noticeMessage: UiText? get() = commandNotice?.message ?: visibleProblem?.code?.toPlayerMessage()
    val connectionMessage: UiText? get() = (connection as? PlaybackConnection.Unavailable)?.reason?.toPlayerMessage()

    fun supportsItemCommand(capability: PlaybackCapability): Boolean = liveSnapshot?.let {
        it.queue.current != null && capability in it.capabilities
    } == true

    /** Call with PlaybackClock.nowMs(), never a frame timestamp. Unknown position stays null. */
    fun positionAt(nowMs: Long): Long? {
        val snapshot = liveSnapshot ?: return null
        val anchor = progress?.takeIf { it.matches(snapshot) } ?: return null
        return if (snapshot.transport.isPlaying) anchor.positionAt(nowMs)
        else anchor.positionAt(nowMs, maxExtrapolationMs = 0)
    }
}

internal data class PlayerProblemKey(val sessionId: PlaybackSessionId, val problemId: PlaybackProblemId)

/** Reference identity distinguishes repeated failures with identical localized text. */
internal class PlayerCommandNoticeId

internal data class PlayerCommandNotice(val id: PlayerCommandNoticeId, val message: UiText)
