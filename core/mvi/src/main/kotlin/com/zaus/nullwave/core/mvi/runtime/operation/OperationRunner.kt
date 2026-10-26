package com.zaus.nullwave.core.mvi.runtime.operation

import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.dsl.OperationKey
import com.zaus.nullwave.core.mvi.state.OperationStates
import com.zaus.nullwave.core.mvi.state.OperationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Main-confined scheduling for stable, ViewModel-owned operation properties. */
internal class OperationRunner<S : UiState>(
    private val scope: CoroutineScope,
    private val setState: (S.() -> S) -> Unit,
    private val onActivityChanged: () -> Unit = {},
) : AutoCloseable {
    private class Request {
        lateinit var job: Job
        var valid = true
    }

    private class Slot(val key: OperationKey<*>, val policy: OperationPolicy, val maxPending: Int) {
        var active: Request? = null
        val pending = ArrayDeque<Request>()
        var pumping = false
        var admissions = 0

        /** The first pending input reserves execution when no active request occupies the slot. */
        fun hasQueueCapacity(hasActiveRequest: Boolean, pendingCount: Int): Boolean {
            val reservedForExecution = if (!hasActiveRequest && pendingCount > 0) 1 else 0
            return pendingCount - reservedForExecution < maxPending
        }
    }

    private val slots = mutableListOf<Slot>()
    private var closed = false
    private val isOpen: Boolean get() = !closed && scope.isActive

    fun latestOperation(key: OperationKey<*> = OperationKey<Unit>("latest")): MviOperation<S> =
        operation(key, OperationPolicy.Latest)

    fun queuedOperation(maxPending: Int, key: OperationKey<*> = OperationKey<Unit>("queued")): MviOperation<S> {
        require(maxPending > 0) { "maxPending must be positive" }
        return operation(key, OperationPolicy.Queued, maxPending)
    }

    fun idleOperation(key: OperationKey<*> = OperationKey<Unit>("idle")): MviOperation<S> =
        operation(key, OperationPolicy.Idle)

    fun activity(): OperationStates = OperationStates(true, slots.associate { slot ->
        val active = slot.active?.takeUnless { it.job.isCompleted }
        val pending = slot.pending.count { it.valid && !it.job.isCancelled && !it.job.isCompleted }
        slot.key to OperationStatus(
            isBusy = isOpen && (active != null || pending > 0),
            isExecuting = isOpen && active?.let { it.valid && it.job.isActive } == true,
            pendingCount = if (isOpen) pending else 0,
            canSubmit = isOpen && when (slot.policy) {
                OperationPolicy.Latest -> true
                OperationPolicy.Queued -> slot.hasQueueCapacity(active != null, pending)
                OperationPolicy.Idle -> active == null && pending == 0
            },
        )
    })

    private fun operation(key: OperationKey<*>, policy: OperationPolicy, maxPending: Int = 0): MviOperation<S> {
        val slot = Slot(key, policy, maxPending)
        if (isOpen) slots.add(slot)
        return MviOperation(
            submitRequest = { onAccepted, block ->
                if (!isOpen) false else when (slot.policy) {
                    OperationPolicy.Latest -> launchLatest(slot, onAccepted, block)
                    OperationPolicy.Queued -> enqueue(slot, onAccepted, block)
                    OperationPolicy.Idle -> launchIfIdle(slot, onAccepted, block)
                }
            },
            cancelRequests = { onCancelled, beforeState -> cancel(slot, onCancelled, beforeState) },
        )
    }

    private fun launchLatest(
        slot: Slot,
        onAccepted: (S.() -> S)?,
        block: suspend OperationScope<S>.() -> Unit,
    ): Boolean {
        pruneCompleted(slot)
        val request = request(slot, block)
        val previous = slot.active
        val superseded = slot.pending.toList()

        // Reserve and revoke before publishing state or running cancellation callbacks: both can
        // synchronously submit a newer intent, whose request must win over this one.
        slot.pending.clear()
        slot.pending.addLast(request)
        previous?.valid = false
        superseded.forEach { it.valid = false }
        accept(slot, request, onAccepted) {
            superseded.forEach { it.job.cancel() }
            previous?.job?.cancel()
        }
        return true
    }

    private fun enqueue(
        slot: Slot,
        onAccepted: (S.() -> S)?,
        block: suspend OperationScope<S>.() -> Unit,
    ): Boolean {
        pruneCompleted(slot)
        if (!slot.hasQueueCapacity(slot.active != null, slot.pending.size)) return false
        val request = request(slot, block)
        slot.pending.addLast(request)
        accept(slot, request, onAccepted)
        return true
    }

    private fun launchIfIdle(
        slot: Slot,
        onAccepted: (S.() -> S)?,
        block: suspend OperationScope<S>.() -> Unit,
    ): Boolean {
        pruneCompleted(slot)
        if (slot.active != null || slot.pending.isNotEmpty()) return false
        val request = request(slot, block)
        slot.pending.addLast(request)
        accept(slot, request, onAccepted)
        return true
    }

    private fun cancel(slot: Slot, onCancelled: (S.() -> S)?, beforeState: () -> Unit): Boolean {
        if (!isOpen) {
            beforeState()
            return false
        }
        pruneCompleted(slot)
        val requests = (listOfNotNull(slot.active) + slot.pending)
            .filter { it.valid && !it.job.isCancelled }
        if (requests.isEmpty()) {
            beforeState()
            return false
        }

        // Detach the snapshot before publishing state. A reentrant submit/cancel must see the new
        // admission state, while the active Job keeps its slot until all cleanup/children finish.
        slot.pending.clear()
        requests.forEach { it.valid = false }
        slot.admissions++
        try {
            // The adapter revokes a completed request's UI handler only after this snapshot loses
            // its write rights, but before onCancelled can resume that handler via State observers.
            beforeState()
            if (isOpen && onCancelled != null) setState(onCancelled)
        } finally {
            try {
                requests.forEach { it.job.cancel() }
            } finally {
                slot.admissions--
                onActivityChanged()
                pump(slot)
            }
        }
        return true
    }

    private fun accept(
        slot: Slot,
        request: Request,
        onAccepted: (S.() -> S)?,
        cancelPrevious: () -> Unit = {},
    ) {
        // State publication and cancellation can reenter this slot. Defer pumping until the
        // outermost acceptance finishes so only the surviving request's body starts.
        slot.admissions++
        try {
            if (onAccepted != null) setState(onAccepted)
        } catch (failure: Throwable) {
            request.valid = false
            request.job.cancel()
            throw failure
        } finally {
            try {
                cancelPrevious()
            } finally {
                slot.admissions--
                onActivityChanged()
                pump(slot)
            }
        }
    }

    private fun request(slot: Slot, block: suspend OperationScope<S>.() -> Unit): Request {
        val request = Request()
        request.job = scope.launch(start = CoroutineStart.LAZY) {
            val canCommit = {
                isOpen && request.valid && request.job.isActive && slot.active === request
            }
            val operation = OperationScope<S>(
                commit = { reducer ->
                    if (!canCommit()) false else {
                        setState(reducer)
                        true
                    }
                },
                canCommit = canCommit,
            )
            onActivityChanged()
            // A status observer can cancel/clear before execute, even without onStarted.
            if (!canCommit()) return@launch
            operation.block()
        }
        request.job.invokeOnCompletion {
            // Completion can run on the dispatcher of the last child. Slot mutations stay on Main.
            scope.launch {
                if (!closed) {
                    request.valid = false
                    if (slot.active === request) slot.active = null
                    slot.pending.remove(request)
                    onActivityChanged()
                    pump(slot)
                }
            }
        }
        return request
    }

    private fun pruneCompleted(slot: Slot) {
        slot.active?.takeIf { it.job.isCompleted }?.let {
            it.valid = false
            slot.active = null
        }
        slot.pending.removeAll { it.job.isCancelled || it.job.isCompleted }
    }

    private fun pump(slot: Slot) {
        if (slot.pumping || slot.admissions > 0) return
        slot.pumping = true
        try {
            while (isOpen) {
                pruneCompleted(slot)
                if (slot.active != null) return
                val next = slot.pending.removeFirstOrNull() ?: return
                slot.active = next
                // Only the runner can start this private LAZY Job, after assigning its slot.
                next.job.start()
                // A Main.immediate body can finish or submit another command before start returns.
                // Loop rather than recursively pumping a queue of synchronously completing jobs.
            }
        } finally {
            slot.pumping = false
        }
    }

    override fun close() {
        closed = true
        val requests = slots.flatMap { listOfNotNull(it.active) + it.pending }
        slots.forEach {
            it.active = null
            it.pending.clear()
        }
        // Retain stable keys for the terminal snapshot; no request/Job stays in these slots.
        requests.forEach { it.valid = false }
        requests.forEach { it.job.cancel() }
        onActivityChanged()
    }
}
