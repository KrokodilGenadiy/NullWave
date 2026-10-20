package com.zaus.nullwave.feature.library.api.repository

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    /**
     * Cold, main-safe observation: each collection checks access, reads a fresh snapshot and observes
     * catalog changes. Published lists must never be mutated. Empty success is a valid catalog.
     *
     * Expected failures are [DataError.AccessDenied] and [DataError.Io], retaining diagnostics in
     * [AppResult.Failure]. Access denial completes collection; I/O failure can recover on the next
     * change if observation is still available. Recollection always retries setup and reading.
     * If access is revoked while an expected I/O failure occurs, report AccessDenied with the
     * original cause. This applies to both reading and observer registration.
     * Cancellation releases the query/observer and must not be converted into a failure value.
     * Programming errors propagate. This method does not scan the filesystem or request permission.
     */
    fun observeTracks(query: LibraryTrackQuery): Flow<AppResult<List<LibraryTrack>, DataError>>
}
