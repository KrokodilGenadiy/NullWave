package com.zaus.nullwave.feature.library.api.repository

import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults
import com.zaus.nullwave.feature.library.api.model.search.normalizeLibrarySearchText
import kotlinx.coroutines.flow.Flow

/**
 * Optional search capability of the Library source, independent of catalog browsing methods.
 * Searches the same eligible audio population as [LibraryRepository], regardless of the Library
 * tab or sort order. No filesystem scan, permission prompt, network, debounce or UI lifecycle.
 *
 * Normalize query/fields with [normalizeLibrarySearchText]. Every space-separated query token
 * must occur as a substring in an allowed field of one entity (AND tokens, OR fields). Repetition
 * does not require another occurrence; punctuation is literal, with no wildcards or operators.
 * Never join field boundaries or search missing/provider-unknown/localized fallback labels.
 * - Tracks: title, known artist/album and separate file display name, even with a populated title.
 * - Albums: canonical title plus one participant's known artist tag. Different participants
 *   cannot jointly cover the tokens. Group complete membership before applying the search.
 * - Artists: canonical known name only, not titles of their songs.
 *
 * Rank each group by primary name: exact full query, full-query prefix, all tokens in that name,
 * then matches requiring supporting fields. Within a rank, use query locale collation (unknown
 * names last), then ordinal ID. Return all matches, without hidden limits or partial success.
 */
interface LibrarySearchRepository {
    /**
     * Cold, main-safe observation. Calling this method performs no I/O. Each collection checks
     * fresh access, registers observation before its initial read and emits subsequent snapshots.
     * Each refresh builds all three groups from one read/observation pipeline, not a combination
     * of independent catalog flows. This does not promise an OS transaction across source volumes.
     *
     * Recheck access before protected registration/read and after reading. AccessDenied completes
     * the flow and releases resources, including when revocation accompanies an I/O error. Io
     * can recover on the next notification while observation lives; failed setup requires a new
     * collection. Failures preserve diagnostics/cause. Cancellation and programming errors
     * propagate; cancellation cleans up the query, matching work and observer.
     *
     * Only expected AccessDenied/Io failures are emitted. No matches is an empty success, never
     * NotFound. Recollecting the same query retries; there is no separate refresh/retry method.
     * Empty input has no [LibrarySearchQuery], so the caller must not collect a search for it.
     */
    fun observeSearch(query: LibrarySearchQuery): Flow<AppResult<LibrarySearchResults, DataError>>
}
