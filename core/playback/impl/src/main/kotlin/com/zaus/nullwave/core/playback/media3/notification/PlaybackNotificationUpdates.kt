package com.zaus.nullwave.core.playback.media3.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Main-confined notification batching and bounded delivery confirmation; the service owns [scope]. */
internal class PlaybackNotificationUpdates(
    private val scope: CoroutineScope,
    private val contentDelayMs: Long = 250,
    private val confirmationDelayMs: Long = 750,
    private val maxRepublishes: Int = 2,
) : AutoCloseable {
    private var closed = false
    private var scheduled: Job? = null
    private var pendingUpdate: (() -> Unit)? = null
    private var confirmation: Job? = null
    private var confirmationState: ConfirmationState? = null

    init {
        require(contentDelayMs >= 0 && confirmationDelayMs >= 0) { "Notification delays must not be negative." }
        require(maxRepublishes >= 0) { "Notification republication limit must not be negative." }
    }

    /** Keeps the first deadline and replaces only its content; a burst cannot postpone it forever. */
    fun schedule(update: () -> Unit) {
        if (closed) return
        pendingUpdate = update
        if (scheduled != null) return
        lateinit var work: Job
        work = scope.launch(start = CoroutineStart.LAZY) {
            try {
                delay(contentDelayMs)
                if (closed || scheduled !== work || !work.isActive) return@launch
                val latest = pendingUpdate
                pendingUpdate = null
                // Release the slot first: a callback can schedule the next independent window.
                scheduled = null
                latest?.invoke()
            } finally {
                if (scheduled === work) {
                    scheduled = null
                    pendingUpdate = null
                }
            }
        }
        scheduled = work
        work.start()
    }

    fun cancelScheduled() {
        val previous = scheduled
        scheduled = null
        pendingUpdate = null
        previous?.cancel()
    }

    /** Same-version calls preserve their deadline and repair budget, including a finished result. */
    fun confirm(
        version: Long,
        isCurrent: (Long) -> Boolean,
        isPublished: (Long) -> Boolean,
        republish: (Long) -> Unit,
        onUnconfirmed: (Long) -> Unit,
    ) {
        if (closed) return
        val state = confirmationState?.takeIf { it.version == version } ?: run {
            cancelConfirmation()
            ConfirmationState(version).also { confirmationState = it }
        }
        if (state.finished || confirmation?.isActive == true) return
        lateinit var work: Job
        work = scope.launch(start = CoroutineStart.LAZY) {
            fun ownsConfirmation(): Boolean = !closed && confirmationState === state &&
                confirmation === work && work.isActive
            try {
                while (true) {
                    delay(confirmationDelayMs)
                    if (!ownsConfirmation() || !isCurrent(version) || !ownsConfirmation()) return@launch
                    val published = isPublished(version)
                    if (!ownsConfirmation() || !isCurrent(version) || !ownsConfirmation()) return@launch
                    if (published) {
                        state.finished = true
                        return@launch
                    }
                    if (state.repairs == maxRepublishes) {
                        state.finished = true
                        onUnconfirmed(version)
                        return@launch
                    }
                    // Charge before the callback: cancellation/rearming from it cannot reset the budget.
                    state.repairs++
                    republish(version)
                }
            } finally {
                // A repair may synchronously confirm a newer publication or close this coordinator.
                if (confirmation === work) confirmation = null
            }
        }
        confirmation = work
        work.start()
    }

    fun cancelConfirmation() {
        val previous = confirmation
        confirmation = null
        previous?.cancel()
    }

    private class ConfirmationState(val version: Long) {
        var repairs = 0
        var finished = false
    }

    override fun close() {
        if (closed) return
        closed = true
        cancelScheduled()
        cancelConfirmation()
    }
}
