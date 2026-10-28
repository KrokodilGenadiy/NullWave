package com.zaus.nullwave.core.mvi.runtime.effect

import com.zaus.nullwave.core.mvi.runtime.MviAccess
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
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

    private class Consumer(
        val job: Job,
        key: CoroutineContext.Key<Consumer>,
    ) : AbstractCoroutineContextElement(key) {
        // Also opened on terminal mailbox closure so a waiting host can return promptly.
        val released = CompletableDeferred<Unit>()
    }

    private val pending = ArrayDeque<Envelope<E>>()
    private val changed = Channel<Unit>(Channel.CONFLATED)
    private val consumerKey = object : CoroutineContext.Key<Consumer> {}
    private var consumer: Consumer? = null
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
     * A replacement waits for a cancelled consumer's cleanup; two active consumers are an error.
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
        consumer?.released?.complete(Unit)
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

    /** Main confines admission; the inner coroutineScope joins all handler cleanup before release. */
    private suspend fun withConsumer(block: suspend () -> Unit) {
        val caller = currentCoroutineContext()
        // A cancelled handler's NonCancellable cleanup cannot wait for its own lease to release.
        // A per-mailbox context key also preserves this check through another VM's UI handler.
        check(caller[consumerKey] == null) { "MVI effects allow only one active collector." }
        while (!closed) {
            val previous = consumer ?: break
            check(previous.job.isCancelled) { "MVI effects allow only one active collector." }
            previous.released.await()
            // Another host may have acquired the lease while we were suspended. Recheck rather
            // than silently queue multiple live UI consumers behind that host.
        }
        caller.ensureActive()
        if (closed) return
        val acquired = Consumer(caller.job, consumerKey)
        consumer = acquired
        try {
            withContext(acquired) { block() }
        } finally {
            if (consumer === acquired) consumer = null
            acquired.released.complete(Unit)
        }
    }
}
