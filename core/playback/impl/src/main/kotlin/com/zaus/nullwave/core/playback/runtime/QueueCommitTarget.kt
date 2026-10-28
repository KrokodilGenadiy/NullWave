package com.zaus.nullwave.core.playback.runtime

import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.result.EmptyResult

/**
 * Synchronous application-looper boundary of the service's engine. Calls must not block or suspend.
 * The engine owns actual queue/transport state and applies its current repeat/shuffle at installation.
 * A complete payload prevents partial source preflight; it does not promise rollback after an engine
 * exception. Native controls must enter the same owner before changing transport during preparation.
 */
internal interface QueueCommitTarget {
    fun install(commit: QueueCommit): EmptyResult<PlaybackCommandError>

    /** Receives all commands except ReplaceQueue; target checks current token/capability itself. */
    fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError>
}
