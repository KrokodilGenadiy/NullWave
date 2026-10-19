package com.zaus.nullwave.feature.library.data

import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.os.OperationCanceledException
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.LibraryQuery
import com.zaus.nullwave.feature.library.api.model.LibraryTrack
import com.zaus.nullwave.feature.library.api.repository.LibraryRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class MediaStoreLibraryRepository(
    private val source: MediaStoreAudioSource,
    private val io: CoroutineDispatcher,
) : LibraryRepository {
    override fun observeTracks(query: LibraryQuery): Flow<AppResult<List<LibraryTrack>, DataError>> = flow {
        val changes = Channel<Unit>(Channel.CONFLATED)
        try {
            val observation = readResult { source.observeChanges { changes.trySend(Unit) } }
            when (observation) {
                is AppResult.Failure -> emit(observation)
                is AppResult.Success -> observation.value.use {
                    changes.trySend(Unit)
                    for (ignored in changes) {
                        val result = readResult { source.readRows().toTracks(query) }
                        // Access may have been revoked while the provider was reading or sorting.
                        val checked = if (result is AppResult.Success && !source.hasReadAccess()) {
                            AppResult.Failure(DataError.AccessDenied)
                        } else result
                        emit(checked)
                        if (checked is AppResult.Failure && checked.error == DataError.AccessDenied) break
                    }
                }
            }
        } finally {
            changes.cancel()
        }
    }.flowOn(io).buffer(Channel.RENDEZVOUS)

    /** Catch only provider failures, never downstream collector/reducer exceptions. */
    private suspend fun <T> readResult(read: suspend () -> T): AppResult<T, DataError> = try {
        currentCoroutineContext().ensureActive()
        if (!source.hasReadAccess()) AppResult.Failure(DataError.AccessDenied)
        else AppResult.Success(read())
    } catch (failure: SecurityException) {
        currentCoroutineContext().ensureActive()
        if (source.hasReadAccess()) throw failure
        AppResult.Failure(DataError.AccessDenied, failure)
    } catch (failure: IOException) {
        ioFailure(failure)
    } catch (failure: SQLiteDiskIOException) {
        ioFailure(failure)
    } catch (failure: SQLiteCantOpenDatabaseException) {
        ioFailure(failure)
    } catch (failure: SQLiteDatabaseCorruptException) {
        ioFailure(failure)
    } catch (failure: OperationCanceledException) {
        // Provider-initiated cancellation is retryable; caller cancellation remains cancellation.
        ioFailure(failure)
    }

    private suspend fun ioFailure(failure: Exception): AppResult.Failure<DataError> {
        currentCoroutineContext().ensureActive()
        return AppResult.Failure(DataError.Io, failure)
    }
}
