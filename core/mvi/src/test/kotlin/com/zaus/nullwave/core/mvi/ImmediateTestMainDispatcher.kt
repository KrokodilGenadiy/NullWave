package com.zaus.nullwave.core.mvi

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher

/**
 * Models Android Main.immediate while retaining the test scheduler's virtual time. The test's
 * owning thread executes Main calls immediately; worker calls are queued on [delegate]. Tests must
 * pass that same delegate to runTest, since this wrapper is not itself a TestDispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
internal class ImmediateTestMainDispatcher(
    private val delegate: TestDispatcher,
    private val owner: Thread = Thread.currentThread(),
) : MainCoroutineDispatcher(), Delay by delegate {
    override val immediate: MainCoroutineDispatcher get() = this

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = Thread.currentThread() !== owner

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        delegate.dispatch(context, block)
    }
}
