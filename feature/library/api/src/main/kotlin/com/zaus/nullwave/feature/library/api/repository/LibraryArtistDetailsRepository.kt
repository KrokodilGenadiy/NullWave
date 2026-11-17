package com.zaus.nullwave.feature.library.api.repository

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistDetails
import kotlinx.coroutines.flow.Flow

interface LibraryArtistDetailsRepository {
    /**
     * Cold, main-safe observation of the complete catalog group identified by [artistId].
     * Use the same eligible membership and source identity as Artists and Search. Each success
     * describes the requested ID, both counts and both lists from a single catalog read.
     *
     * Include each artist track once, including tracks without an album identity. Include each
     * related album once with its full catalog summary, including compilations and grouped
     * Download albums. Album identities, metadata and artwork match Albums and Search.
     *
     * Order albums by title ascending using the screen's nonblank [localeTag], unknown titles
     * last, then by the ordinal AlbumId value. Order tracks as Songs with title sorting ascending
     * and the same locale, then by the ordinal TrackId value. Factory creation preserves that order.
     *
     * Check access before registration and each read. Register before the first read.
     * With confirmed access, an empty artist membership emits [DataError.NotFound] and observation
     * continues so the same ID can reappear. An artist with no albums can still have tracks.
     * Recheck access before publishing content or absence. Revocation emits [DataError.AccessDenied]
     * and completes collection, including when registration or reading also encounters an I/O error.
     * [DataError.Io] retains diagnostics and can recover on the next change while observation lives.
     * Recollection retries setup and reading. Cancellation releases resources and propagates.
     */
    fun observeArtistDetails(
        artistId: ArtistId,
        localeTag: String,
    ): Flow<AppResult<LibraryArtistDetails, DataError>>
}
