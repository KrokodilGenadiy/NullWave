package com.zaus.nullwave.feature.library.api.repository

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumDetails
import kotlinx.coroutines.flow.Flow

interface LibraryAlbumDetailsRepository {
    /**
     * Cold, main-safe observation of the complete catalog group identified by [albumId].
     * Use the same eligible membership and identity as Albums and Search, including Download
     * groups spanning provider albums. Each success describes the requested ID in a single read.
     *
     * Order by disc and track number ascending. A track number alone belongs to disc one.
     * Unnumbered tracks on a known disc follow its numbered tracks. Tracks with neither number
     * follow all discs. Break ties by display title using the screen's nonblank [localeTag],
     * then by the ordinal TrackId value.
     *
     * Check access before registration and each read. Register before the first read.
     * With confirmed access, an empty result emits [DataError.NotFound] and observation
     * continues so the same ID can reappear.
     * Recheck access before publishing content or absence. Revocation emits [DataError.AccessDenied]
     * and completes collection, including when registration or reading also encounters an I/O error.
     * [DataError.Io] retains diagnostics and can recover on the next change while observation lives.
     * Recollection retries setup and reading. Cancellation releases resources and propagates.
     */
    fun observeAlbumDetails(
        albumId: AlbumId,
        localeTag: String,
    ): Flow<AppResult<LibraryAlbumDetails, DataError>>
}
