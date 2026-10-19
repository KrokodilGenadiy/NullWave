package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.LibraryQuery
import com.zaus.nullwave.feature.library.api.model.LibraryTrack
import com.zaus.nullwave.feature.library.api.repository.LibraryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withContext

internal typealias LibraryResult = AppResult<List<LibraryTrack>, DataError>

/** Test-only provider: independent cold subscriptions, synchronous results and delayed cleanup. */
internal class FakeLibraryRepository : LibraryRepository {
    val subscriptions = mutableListOf<Subscription>()
    var initialResult: LibraryResult? = null
    var completeAfterInitial = false
    var failureOnCollect: Throwable? = null
    var activeCount = 0
        private set
    var maxActiveCount = 0
        private set

    override fun observeTracks(query: LibraryQuery): Flow<LibraryResult> = object : Flow<LibraryResult> {
        override suspend fun collect(collector: FlowCollector<LibraryResult>) {
            val subscription = Subscription(query).also(subscriptions::add)
            activeCount++
            maxActiveCount = maxOf(maxActiveCount, activeCount)
            try {
                failureOnCollect?.let { throw it }
                initialResult?.let {
                    collector.emit(it)
                    if (completeAfterInitial || it.isAccessDenied()) return
                }
                for (result in subscription.results) {
                    collector.emit(result)
                    if (result.isAccessDenied()) return
                }
            } finally {
                subscription.cleanupStarted = true
                try {
                    withContext(NonCancellable) {
                        subscription.cleanupGate?.await()
                        subscription.lateResult?.let {
                            // Deliberately violate cooperative cancellation to exercise MVI's fence.
                            subscription.lateDeliveryAttempted = true
                            collector.emit(it)
                        }
                    }
                } finally {
                    subscription.closed = true
                    subscription.results.close()
                    activeCount--
                }
            }
        }
    }

    fun releaseCleanup() = subscriptions.forEach { it.cleanupGate?.complete(Unit) }

    private fun LibraryResult.isAccessDenied() = this is AppResult.Failure && error == DataError.AccessDenied

    class Subscription(val query: LibraryQuery) {
        val results = Channel<LibraryResult>(Channel.UNLIMITED)
        var cleanupGate: CompletableDeferred<Unit>? = null
        var lateResult: LibraryResult? = null
        var cleanupStarted = false
        var lateDeliveryAttempted = false
        var closed = false

        fun respond(result: LibraryResult) {
            check(results.trySend(result).isSuccess) { "Cannot answer a closed subscription." }
        }

        fun delayCleanupWith(result: LibraryResult) {
            cleanupGate = CompletableDeferred()
            lateResult = result
        }
    }
}
