package com.zaus.nullwave.feature.search.presentation

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults
import com.zaus.nullwave.feature.library.api.repository.LibrarySearchRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

internal typealias SearchResult = AppResult<LibrarySearchResults, DataError>

/** Test-only cold provider: records method calls, observations, updates and delayed cleanup. */
internal class FakeSearchRepository : LibrarySearchRepository {
    val calls = mutableListOf<LibrarySearchQuery>()
    val subscriptions = mutableListOf<Subscription>()
    var initialResult: SearchResult? = null
    var completeAfterInitial = false
    var failureOnCollect: Throwable? = null
    var activeCount = 0
        private set
    var maxActiveCount = 0
        private set

    override fun observeSearch(query: LibrarySearchQuery): Flow<SearchResult> {
        calls += query
        return flow {
            val subscription = Subscription(query).also(subscriptions::add)
            activeCount++
            maxActiveCount = maxOf(maxActiveCount, activeCount)
            try {
                failureOnCollect?.let { throw it }
                initialResult?.let {
                    emit(it)
                    if (completeAfterInitial || it.isAccessDenied()) return@flow
                }
                for (result in subscription.results) {
                    emit(result)
                    if (result.isAccessDenied()) return@flow
                }
            } finally {
                subscription.cleanupStarted = true
                try {
                    withContext(NonCancellable) { subscription.cleanupGate?.await() }
                } finally {
                    subscription.closed = true
                    subscription.results.close()
                    activeCount--
                }
            }
        }
    }

    fun releaseCleanup() = subscriptions.forEach { it.cleanupGate?.complete(Unit) }

    private fun SearchResult.isAccessDenied(): Boolean =
        this is AppResult.Failure && error == DataError.AccessDenied

    class Subscription(val query: LibrarySearchQuery) {
        val results = Channel<SearchResult>(Channel.UNLIMITED)
        var cleanupGate: CompletableDeferred<Unit>? = null
        var cleanupStarted = false
        var closed = false

        fun respond(result: SearchResult) {
            check(results.trySend(result).isSuccess) { "Cannot answer a closed search subscription." }
        }
    }
}
