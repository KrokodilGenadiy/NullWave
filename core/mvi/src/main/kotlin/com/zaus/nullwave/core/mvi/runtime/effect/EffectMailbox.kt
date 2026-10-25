package com.zaus.nullwave.core.mvi.runtime.effect

import com.zaus.nullwave.core.mvi.runtime.MviAccess
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A bounded, Main-confined mailbox for optional signals. Invalid entries never consume capacity.
 * One consumer owns sequential delivery and the lifetime of its managed handlers.
 */
internal class EffectMailbox<E>(private val capacity: Int = 64) {
    private class Envelope<E>(val value: E, val epoch: EffectEpoch?, val valid: () -> Boolean) {
        fun isCurrent(): Boolean = epoch?.isCurrent != false && valid()
    }

    private class ActiveHandler(val epoch: EffectEpoch?, val job: Job)

    private val pending = ArrayDeque<Envelope<E>>()
    private val changed = Channel<Unit>(Channel.CONFLATED)
    private var consumerActive = false
    private var activeHandler: ActiveHandler? = null
    private var closed = false

    /** Must be called on Main. Acceptance never waits for a UI consumer. */
    fun offer(value: E, epoch: EffectEpoch?, isCurrent: () -> Boolean): Boolean {
        if (closed || epoch?.isCurrent == false || !isCurrent()) return false
        pending.removeAll { !it.isCurrent() }
        if (pending.size >= capacity) return false
        pending.addLast(Envelope(value, epoch, isCurrent))
        changed.trySend(Unit)
        return true
    }

    /** Revokes pending entries and only this generation's currently executing managed handler. */
    fun revoke(epoch: EffectEpoch) {
        epoch.revoke()
        pending.removeAll { !it.isCurrent() }
        activeHandler?.takeIf { it.epoch === epoch }?.job?.cancel()
    }

    /**
     * Runs handlers sequentially on Main. Revocation cancels the matching handler and waits for its
     * structured cleanup before delivering another signal. Cancellation remains cooperative.
     * A signal already handed to a stopped/cancelled consumer is not replayed.
     */
    suspend fun collect(handler: suspend (E) -> Unit): Unit = withContext(Dispatchers.Main.immediate) {
        withConsumer {
            coroutineScope {
                while (true) {
                    val envelope = next() ?: break
                    currentCoroutineContext().ensureActive()
                    if (closed || !envelope.isCurrent()) continue
                    // Install ownership before starting: a handler may synchronously revoke itself.
                    val job = launch(MviAccess.neutralContext(), start = CoroutineStart.LAZY) {
                        if (!closed && envelope.isCurrent()) handler(envelope.value)
                    }
                    val active = ActiveHandler(envelope.epoch, job)
                    activeHandler = active
                    try {
                        job.start()
                        job.join()
                    } finally {
                        if (activeHandler === active) activeHandler = null
                    }
                }
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        pending.clear()
        activeHandler?.job?.cancel()
        changed.close()
    }

    private suspend fun next(): Envelope<E>? {
        while (!closed) {
            currentCoroutineContext().ensureActive()
            while (pending.isNotEmpty()) {
                val envelope = pending.removeFirst()
                if (envelope.isCurrent()) return envelope
            }
            if (changed.receiveCatching().isClosed) break
        }
        return null
    }

    /** Entire collection stays on Main; coroutineScope joins handler cleanup before release. */
    private suspend fun withConsumer(block: suspend () -> Unit) {
        check(!consumerActive) { "MVI effects allow only one active collector." }
        consumerActive = true
        try {
            block()
        } finally {
            consumerActive = false
        }
    }
}
