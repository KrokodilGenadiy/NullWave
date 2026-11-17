package com.zaus.nullwave.feature.player.api.launch

import com.zaus.nullwave.core.mvi.UiEffect
import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.dsl.Operation
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
    /** Denial already handled or present at the most recent grant, identified by session and problem ID. */
    val handledDenial: PlaybackProblemKey? = null,
) {
    fun dismissed(): PlaybackLaunchState = copy(notice = null)

    /** Pass [PlaybackLaunch.publishedDenial], read in the handler: a denial older than the grant stays handled. */
    fun accessGranted(denialAtGrant: PlaybackProblemKey?): PlaybackLaunchState = copy(handledDenial = denialAtGrant)
}

/** Declared by [playbackLaunch]. Submit or cancel [operation] from intent handlers like any operation. */
class PlaybackLaunch internal constructor(
    val operation: Operation<PlaybackQueueRequest>,
    private val observation: PlaybackAccessObservation,
) {
    /** Denial the service publishes right now. Read it in a handler, not in a reducer, which must stay pure. */
    fun publishedDenial(): PlaybackProblemKey? = observation.publishedDenial()
}

/**
 * Declares how a screen starts a queue: a `latest` operation called [name] and the source
 * "[name].access" that watches for engine access denials once the first start is accepted.
 *
 * A failed start sets [PlaybackLaunchState.notice]. A confirmed access denial, from the command
 * reply or from a later engine snapshot, applies [closeAccess] once; a denial that existed before
 * the first start, or before the latest grant, does not. The screen still handles its own intents:
 * submit and cancel [PlaybackLaunch.operation], dismiss the notice and record grants in its reducers.
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
): PlaybackLaunch {
    val observation = PlaybackAccessObservation(controller)
    fun S.updateLaunch(change: PlaybackLaunchState.() -> PlaybackLaunchState): S = withLaunchState(launchState().change())

    val operation = latest<PlaybackQueueRequest, EmptyResult<PlaybackCommandError>>(name) {
        execute(observation::execute)
        onAccepted { updateLaunch { copy(notice = null, monitoring = true) } }
        onResult { _, result ->
            // The access source may already have closed the screen before this acknowledgement.
            val error = (result as? AppResult.Failure)?.error ?: return@onResult this
            val denied = error is PlaybackCommandError.SourceFailure && error.error is PlaybackSourceError.AccessDenied
            // A confirmed denial closes access even when the screen shows no text for it.
            val next = if (denied) closeAccess() else this
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
        if (update.denial == update.initialDenial || update.denial == launchState().handledDenial) this
        else closeAccess().updateLaunch { copy(handledDenial = update.denial) }
    }
    return PlaybackLaunch(operation, observation)
}
