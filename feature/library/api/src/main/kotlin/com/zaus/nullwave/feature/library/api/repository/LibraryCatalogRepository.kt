package com.zaus.nullwave.feature.library.api.repository

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import kotlinx.coroutines.flow.Flow

/**
 * Catalog browsing capability extending the track contract. A track-only adapter must not claim
 * this capability until it can observe both groupings; there are no empty/TODO default methods.
 *
 * All methods follow [LibraryRepository.observeTracks]: cold, main-safe snapshots; fresh access
 * checks; AccessDenied/Io failures with diagnostics; observer/query cleanup on cancellation.
 * Access denial completes collection. I/O may recover on a later change while observation lives;
 * recollection retries setup and reading. Cancellation/programming errors propagate.
 * Empty success means no eligible entities, never permission denial or an unsupported method.
 *
 * Albums and artists use the same published, non-trashed audio population as observeTracks.
 * Group by source identities (including volume), not labels or artwork keys. A missing entity ID
 * omits that membership, not the track from Songs; do not invent a named group from a display tag.
 * Normalize blank/provider-unknown names to null and keep valid identities with unknown names.
 * Counts refer to eligible members; emit no zero-track entities or duplicate IDs. Snapshots must
 * never be mutated after emission. Independent collections are not an atomic cross-tab snapshot.
 *
 * No filesystem scan, permission request, playback, search or detail lookup is implied here.
 */
interface LibraryCatalogRepository : LibraryRepository {
    /** Observe all represented albums in the order specified by [query]. */
    fun observeAlbums(query: LibraryAlbumQuery): Flow<AppResult<List<LibraryAlbum>, DataError>>

    /** Observe all represented artists in the order specified by [query]. */
    fun observeArtists(query: LibraryArtistQuery): Flow<AppResult<List<LibraryArtist>, DataError>>
}
