package com.zaus.nullwave.core.playback.media3.session

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Future cancellation never rolls back service work; service lifetime still cancels that work. */
internal fun <T> CoroutineScope.serviceFuture(block: suspend () -> T): ListenableFuture<T> {
    val result = SettableFuture.create<T>()
    val work = launch(start = CoroutineStart.LAZY) {
        try { result.set(block()) }
        catch (failure: Throwable) { result.setException(failure) }
    }
    work.invokeOnCompletion { failure -> if (failure != null) result.setException(failure) }
    work.start()
    return result
}
