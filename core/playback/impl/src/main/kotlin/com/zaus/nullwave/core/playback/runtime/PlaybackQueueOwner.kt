package com.zaus.nullwave.core.playback.runtime

import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueOrder
import com.zaus.nullwave.core.playback.preparation.QueuePreparer
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Service-owned admission and queue replacement, independent of a UI caller's wait. Preparation is
 * latest, while transport actions remain ordered. The future service closes this owner before
 * releasing its engine on [main]; this class neither creates nor releases the audio engine.
 */
internal class PlaybackQueueOwner(
    serviceScope: CoroutineScope,
    private val main: CoroutineDispatcher,
    private val preparer: QueuePreparer,
    private val target: QueueCommitTarget,
    private val identities: PlaybackIdentities = PlaybackIdentities(),
) {
    private val lifetime = SupervisorJob(serviceScope.coroutineContext.job)
    private val scope = CoroutineScope(serviceScope.coroutineContext + main + lifetime)
    // Main confines the state. The short lock also defers Main.immediate callbacks reentering a target call.
    private val admission = Mutex()
    private var pending: Pending? = null
    private var closed = false

    /** Last actual failure in this service lifetime. Read on [main]; retained across successful commands. */
    var lastFailure: PlaybackCommandFailure? = null
        private set

    suspend fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> {
        val reply = withContext(main) {
            admission.withLock {
                if (closed || !lifetime.isActive) {
                    return@withLock CompletableDeferred<EmptyResult<PlaybackCommandError>>(
                        AppResult.Failure(PlaybackCommandError.ConnectionUnavailable),
                    )
                }
                if (command is PlaybackCommand.ReplaceQueue) accept(command)
                else {
                    // Explicit Play/Retry also revoke pending replacement: the later user action wins.
                    if (command !is PlaybackCommand.SetRepeat && command !is PlaybackCommand.SetShuffle) {
                        invalidate(PlaybackCommandError.Superseded)
                    }
                    val result = runTarget(command) { target.execute(command) }
                    if (command is PlaybackCommand.SetShuffle && result is AppResult.Success) {
                        pending?.applyShuffle(command.enabled)
                    }
                    CompletableDeferred(result)
                }
            }
        }
        // The reply belongs to the owner lifetime, never to the UI caller awaiting it.
        return reply.await()
    }

    /** Native Player actions (e.g. stop-without-clear) share admission without changing their semantics. */
    suspend fun executeNative(action: () -> Unit): EmptyResult<PlaybackCommandError> = withContext(main) {
        admission.withLock {
            if (closed || !lifetime.isActive) return@withLock AppResult.Failure(PlaybackCommandError.ConnectionUnavailable)
            invalidate(PlaybackCommandError.Superseded)
            action()
            AppResult.Success(Unit)
        }
    }

    /** Close is a short cleanup boundary, including when called from an already-cancelled service. */
    suspend fun close() = withContext(main + NonCancellable) {
        admission.withLock {
            if (closed) return@withLock
            closed = true
            invalidate(PlaybackCommandError.ConnectionUnavailable)
            lifetime.cancel()
        }
    }

    private fun accept(command: PlaybackCommand.ReplaceQueue): CompletableDeferred<EmptyResult<PlaybackCommandError>> {
        invalidate(PlaybackCommandError.Superseded)
        val accepted = Pending(lifetime, command.request.order)
        val work = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = preparer.prepare(command.request)
                admission.withLock {
                    if (closed || !lifetime.isActive || pending !== accepted) return@withLock
                    try {
                        val outcome = when (result) {
                            is AppResult.Success -> {
                                val commit = QueueCommit.create(result.value, identities, accepted.order)
                                // Payload construction can take time; service lifetime may end meanwhile.
                                if (closed || !lifetime.isActive || pending !== accepted) return@withLock
                                rememberFailure(command, target.install(commit))
                            }
                            is AppResult.Failure -> rememberFailure(command, result)
                        }
                        pending = null
                        accepted.reply.complete(outcome)
                    } catch (failure: Throwable) {
                        // Unlock can resume a queued Main.immediate caller. Finish this request first.
                        finishFailure(command, accepted, failure)
                    }
                }
            } catch (failure: Throwable) {
                finishFailure(command, accepted, failure)
            } finally {
                withContext(main + NonCancellable) {
                    admission.withLock { if (pending === accepted) pending = null }
                }
            }
        }
        accepted.work = work
        pending = accepted
        // A cancelled service may prevent even the first instruction of a lazy coroutine from running.
        work.invokeOnCompletion { failure ->
            if (failure != null) accepted.reply.completeExceptionally(failure)
        }
        work.start()
        return accepted.reply
    }

    private fun finishFailure(command: PlaybackCommand.ReplaceQueue, accepted: Pending, failure: Throwable) {
        if (pending === accepted) {
            pending = null
            if (failure !is CancellationException && !closed && lifetime.isActive) {
                lastFailure = PlaybackCommandFailure.Unexpected(command, failure)
            }
        }
        // Preserve the original failure rather than classifying it as a source rejection.
        accepted.reply.completeExceptionally(failure)
    }

    private fun runTarget(
        command: PlaybackCommand,
        action: () -> EmptyResult<PlaybackCommandError>,
    ): EmptyResult<PlaybackCommandError> = try {
        rememberFailure(command, action())
    } catch (failure: Throwable) {
        if (failure !is CancellationException) lastFailure = PlaybackCommandFailure.Unexpected(command, failure)
        throw failure
    }

    private fun rememberFailure(
        command: PlaybackCommand,
        result: EmptyResult<PlaybackCommandError>,
    ): EmptyResult<PlaybackCommandError> {
        if (result is AppResult.Failure) lastFailure = PlaybackCommandFailure.Rejected(command, result)
        return result
    }

    private fun invalidate(error: PlaybackCommandError) {
        val previous = pending ?: return
        pending = null
        previous.reply.complete(AppResult.Failure(error))
        previous.work.cancel()
    }

    private class Pending(parent: Job, var order: PlaybackQueueOrder) {
        val reply = CompletableDeferred<EmptyResult<PlaybackCommandError>>(parent = parent)
        lateinit var work: Job

        fun applyShuffle(enabled: Boolean) {
            // KeepShuffleMode already follows the live player. Explicit orders are owned by this replacement.
            if (order != PlaybackQueueOrder.KeepShuffleMode) {
                order = if (enabled) PlaybackQueueOrder.Shuffled else PlaybackQueueOrder.InOrder
            }
        }
    }
}
