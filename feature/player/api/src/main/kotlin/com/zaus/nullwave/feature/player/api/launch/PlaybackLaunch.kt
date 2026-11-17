package com.zaus.nullwave.feature.player.api.launch

import com.zaus.nullwave.core.mvi.UiEffect
import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.dsl.IntentScope
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.dsl.Operation
import com.zaus.nullwave.core.mvi.dsl.Source
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.flow.emptyFlow

/** A backend may reuse a problem ID after a new service incarnation. */
data class PlaybackProblemKey(
    val sessionId: PlaybackSessionId,
    val problemId: PlaybackProblemId,
)

/**
 * What a screen that starts queues keeps about its own starts. Player presents engine problems;
 * this holds only the screen's command failure and its access-denial bookkeeping. Never persisted.
 */
data class PlaybackLaunchState(
    /** Localized failure of this screen's latest start, kept apart from its own read errors. */
    val notice: UiText? = null,
    /** Access observation starts with the first accepted start, before acknowledgement. */
    val monitoring: Boolean = false,
    /** Last observed engine denial, identified by session and problem ID across snapshot updates. */
    val handledDenial: PlaybackProblemKey? = null,
    /** Failures up to this point are covered by a successful Android check. */
    internal val checkedThroughDenial: Long = 0,
    /** An older queued grant must not reopen a gate already closed by this failure. */
    internal val lastAppliedDenial: Long = 0,
) {
    fun dismissed(): PlaybackLaunchState = copy(notice = null)
}

/** Declared by [playbackLaunch]. Submit or cancel [operation] from intent handlers like any operation. */
class PlaybackLaunch<S : UiState> internal constructor(
    val operation: Operation<PlaybackQueueRequest>,
    private val launchState: S.() -> PlaybackLaunchState,
    private val withLaunchState: S.(PlaybackLaunchState) -> S,
) {
    /** Refresh only if the grant covers the most recent denial that closed this screen. */
    fun restoreAccess(scope: IntentScope<S, *>, checkedThroughDenial: Long, source: Source<S>, reopen: S.() -> S) {
        val current = scope.state.launchState()
        if (checkedThroughDenial < current.checkedThroughDenial) return
        val checked = current.copy(checkedThroughDenial = checkedThroughDenial)
        if (current.lastAppliedDenial > checkedThroughDenial) {
            // Remember the check for older replies still in flight. Keep the gate and source intact.
            scope.reduce { withLaunchState(checked) }
        } else {
            scope.refresh(source) { reopen().withLaunchState(checked) }
        }
    }
}

/**
 * Declares how a screen starts a queue: a `latest` operation called [name] and the source
 * "[name].access" that watches for engine access denials once the first start is accepted.
 *
 * A failed start can set [PlaybackLaunchState.notice]. A current access denial from the command
 * reply or engine snapshot applies [closeAccess]. On a successful permission check the screen
 * applies [PlaybackLaunch.restoreAccess] to account for denials that predate the grant.
 *
 * @param launchState reads the screen's [PlaybackLaunchState].
 * @param withLaunchState returns the screen state with an updated [PlaybackLaunchState].
 * @param closeAccess the screen's own gate for a confirmed access denial. It must not change any source input.
 * @param message localized text for a failed start; null shows nothing, for example when superseded.
 *   A confirmed access denial still applies [closeAccess] when this returns null.
 */
fun <S : UiState, I : UiIntent, E : UiEffect> MviDefinition<S, I, E>.playbackLaunch(
    name: String,
    controller: PlaybackController,
    launchState: S.() -> PlaybackLaunchState,
    withLaunchState: S.(PlaybackLaunchState) -> S,
    closeAccess: S.() -> S,
    message: (PlaybackCommandError) -> UiText?,
): PlaybackLaunch<S> {
    val observation = PlaybackAccessObservation(controller)
    fun S.updateLaunch(change: PlaybackLaunchState.() -> PlaybackLaunchState): S = withLaunchState(launchState().change())

    val operation = latest<PlaybackQueueRequest, EmptyResult<PlaybackCommandError>>(name) {
        execute(observation::execute)
        onAccepted { updateLaunch { copy(notice = null, monitoring = true) } }
        onResult { _, result ->
            val error = (result as? AppResult.Failure)?.error ?: return@onResult this
            val denial = (error as? PlaybackCommandError.SourceFailure)?.error as? PlaybackSourceError.AccessDenied
            val sequence = denial?.occurrence?.sequence
            if (sequence != null && sequence <= launchState().checkedThroughDenial) return@onResult this
            // A confirmed denial closes access even when the screen shows no text for it.
            val next = if (sequence == null) this else closeAccess().updateLaunch {
                copy(lastAppliedDenial = maxOf(lastAppliedDenial, sequence))
            }
            val text = message(error) ?: return@onResult next
            next.updateLaunch { copy(notice = text) }
        }
    }
    // Engine denial closes the screen without creating or replacing a command notice.
    source(
        name = "$name.access",
        input = { launchState().monitoring },
        flow = { monitoring -> if (monitoring) observation.observe() else emptyFlow() },
    ) { _, update ->
        when {
            update.denial == launchState().handledDenial -> this
            update.sequence <= launchState().checkedThroughDenial -> updateLaunch { copy(handledDenial = update.denial) }
            else -> closeAccess().updateLaunch {
                copy(handledDenial = update.denial, lastAppliedDenial = maxOf(lastAppliedDenial, update.sequence))
            }
        }
    }
    return PlaybackLaunch(operation, launchState, withLaunchState)
}
