package com.zaus.nullwave.core.mvi.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orders admission across Main and worker callers without making every Main callback asynchronous.
 * An empty queue permits immediate (including publication-reentrant) Main dispatch. A later Main
 * caller must join an already pending worker queue instead of overtaking it.
 */
internal class MainIntentIngress(private val scope: CoroutineScope) : AutoCloseable {
    private val lock = Any()
    private val pending = ArrayDeque<() -> Unit>()
    private var scheduled = false
    private var closed = false
    private var deferrals = 0

    fun dispatch(block: () -> Unit) {
        val onMain = !Dispatchers.Main.immediate.isDispatchNeeded(scope.coroutineContext)
        var inline = false
        var schedule = false
        synchronized(lock) {
            if (closed || !scope.isActive) return
            if (onMain && pending.isEmpty() && deferrals == 0) {
                inline = true
            } else {
                pending.addLast(block)
                if (!scheduled && deferrals == 0) {
                    scheduled = true
                    schedule = true
                }
            }
        }
        if (inline) block()
        if (schedule) scheduleDrain()
    }

    /** Main-only, non-suspending boundary: cleanup callbacks enter after cancellation commits. */
    fun <T> deferDispatch(block: () -> T): T {
        synchronized(lock) { deferrals++ }
        return try {
            block()
        } finally {
            val schedule = synchronized(lock) {
                deferrals--
                if (closed || !scope.isActive) pending.clear()
                if (deferrals == 0 && pending.isNotEmpty() && !scheduled) {
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
        try {
            while (true) {
                val next = synchronized(lock) {
                    if (closed || !scope.isActive) pending.clear()
                    if (deferrals == 0) pending.removeFirstOrNull() else null
                } ?: return
                next()
            }
        } finally {
            // A throwing handler must not strand independently admitted callbacks. Their failures
            // still belong to the VM scope, rather than to a later Main caller joining the queue.
            val reschedule = synchronized(lock) {
                if (closed || !scope.isActive) pending.clear()
                scheduled = deferrals == 0 && pending.isNotEmpty()
                scheduled
            }
            if (reschedule) scheduleDrain()
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            pending.clear()
        }
    }
}
