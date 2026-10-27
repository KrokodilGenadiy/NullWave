package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.api.repository.LibraryCatalogRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal typealias LibraryResult = AppResult<List<MediaTrack>, DataError>

/** Test-only provider: independent cold subscriptions, synchronous results and delayed cleanup. */
internal class FakeLibraryRepository : LibraryCatalogRepository {
    val tracks = CatalogFlow<LibraryTrackQuery, MediaTrack>()
    val albums = CatalogFlow<LibraryAlbumQuery, LibraryAlbum>()
    val artists = CatalogFlow<LibraryArtistQuery, LibraryArtist>()
    val requests = mutableListOf<Any>()
    var activeCount = 0
        private set
    var maxActiveCount = 0
        private set

    override fun observeTracks(query: LibraryTrackQuery) = tracks.observe(query)
    override fun observeAlbums(query: LibraryAlbumQuery) = albums.observe(query)
    override fun observeArtists(query: LibraryArtistQuery) = artists.observe(query)

    fun releaseCleanup() {
        tracks.releaseCleanup()
        albums.releaseCleanup()
        artists.releaseCleanup()
    }

    inner class CatalogFlow<Q : Any, T> {
        val subscriptions = mutableListOf<Subscription<Q, T>>()
        var initialResult: AppResult<List<T>, DataError>? = null
        var completeAfterInitial = false
        var failureOnCollect: Throwable? = null

        fun releaseCleanup() = subscriptions.forEach { it.cleanupGate?.complete(Unit) }

        fun observe(query: Q): Flow<AppResult<List<T>, DataError>> = object : Flow<AppResult<List<T>, DataError>> {
            override suspend fun collect(collector: FlowCollector<AppResult<List<T>, DataError>>) {
                val subscription = Subscription<Q, T>(query).also(subscriptions::add)
                requests += query
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
    }

    private fun AppResult<*, DataError>.isAccessDenied() = this is AppResult.Failure && error == DataError.AccessDenied

    class Subscription<Q, T>(val query: Q) {
        val results = Channel<AppResult<List<T>, DataError>>(Channel.UNLIMITED)
        var cleanupGate: CompletableDeferred<Unit>? = null
        var lateResult: AppResult<List<T>, DataError>? = null
        var cleanupStarted = false
        var lateDeliveryAttempted = false
        var closed = false

        fun respond(result: AppResult<List<T>, DataError>) {
            check(results.trySend(result).isSuccess) { "Cannot answer a closed subscription." }
        }

        fun delayCleanupWith(result: AppResult<List<T>, DataError>) {
            cleanupGate = CompletableDeferred()
            lateResult = result
        }
    }
}
