package com.zaus.nullwave.core.mvi.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orders admission across Main and worker callers without making every Main callback asynchronous.
 * An idle Main caller runs synchronously. Calls admitted during a handler or a deferred publication
 * join the FIFO instead of entering that transition recursively. A later Main caller cannot overtake
 * an earlier worker. Queued failures belong to the VM scope, not the caller that releases a turn.
 */
internal class MainIntentIngress(private val scope: CoroutineScope) : AutoCloseable {
    private val lock = Any()
    private val pending = ArrayDeque<() -> Unit>()
    private var scheduled = false
    private var running = false
    private var closed = false
    private var deferrals = 0

    fun dispatch(block: () -> Unit) {
        val onMain = !Dispatchers.Main.immediate.isDispatchNeeded(scope.coroutineContext)
        var inline = false
        var schedule = false
        synchronized(lock) {
            if (closed || !scope.isActive) return
            if (onMain && pending.isEmpty() && !running && !scheduled && deferrals == 0) {
                inline = true
                running = true
            } else {
                pending.addLast(block)
                if (!running && !scheduled && deferrals == 0) {
                    scheduled = true
                    schedule = true
                }
            }
        }
        if (inline) {
            try {
                block()
            } finally {
                finishRun()
            }
        }
        if (schedule) scheduleDrain()
    }

    /** Main-only boundary for publication/cancellation; never hold it across suspension. */
    fun <T> deferDispatch(block: () -> T): T {
        synchronized(lock) { deferrals++ }
        return try {
            block()
        } finally {
            val schedule = synchronized(lock) {
                deferrals--
                if (closed || !scope.isActive) pending.clear()
                if (deferrals == 0 && pending.isNotEmpty() && !running && !scheduled) {
                    scheduled = true
                    true
                } else {
                    false
                }
            }
            if (schedule) scheduleDrain()
        }
    }

    private fun scheduleDrain() {
        scope.launch(Dispatchers.Main.immediate) { drain() }
    }

    private fun drain() {
        synchronized(lock) {
            if (running || deferrals > 0) {
                scheduled = false
                return
            }
            running = true
        }
        try {
            while (true) {
                val next = synchronized(lock) {
                    if (closed || !scope.isActive) pending.clear()
                    if (deferrals == 0) pending.removeFirstOrNull() else null
                } ?: return
                next()
            }
        } finally {
            finishRun()
        }
    }

    private fun finishRun() {
        val reschedule = synchronized(lock) {
            running = false
            if (closed || !scope.isActive) pending.clear()
            scheduled = deferrals == 0 && pending.isNotEmpty()
            scheduled
        }
        // A throwing callback must not strand independently admitted callbacks or replace the
        // original caller's exception. The VM scope owns failures from the queued drain.
        if (reschedule) scheduleDrain()
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            pending.clear()
        }
    }
}
