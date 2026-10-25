package com.zaus.nullwave.core.mvi

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher

/** Main queues; immediate and delayed resume follow Android's inline continuation behavior. */
@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
internal class AndroidLikeTestMainDispatcher(
    private val delegate: TestDispatcher,
    private val inline: Boolean = false,
    private val owner: Thread = Thread.currentThread(),
) : MainCoroutineDispatcher(), Delay by delegate {
    override val immediate: MainCoroutineDispatcher =
        if (inline) this else AndroidLikeTestMainDispatcher(delegate, true, owner)

    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        !inline || Thread.currentThread() !== owner

    override fun dispatch(context: CoroutineContext, block: Runnable) = delegate.dispatch(context, block)

    // Delegating Delay to TestDispatcher would hide the Work ThreadLocal leak on Android.
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val timer = delegate.invokeOnTimeout(timeMillis, {
            with(continuation) { resumeUndispatched(Unit) }
        }, continuation.context)
        continuation.invokeOnCancellation { timer.dispose() }
    }
}
