package com.zaus.nullwave.feature.library.api.model.album

import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery

/**
 * Complete input for album browsing, ordered by title using [localeTag].
 * Unknown titles sort last in either direction; ties use [AlbumId.value] ascending.
 * Track-specific sorting modes in [LibraryTrackQuery] do not apply to this query.
 */
data class LibraryAlbumQuery(
    val localeTag: String,
    val direction: SortDirection = SortDirection.Ascending,
) {
    init {
        require(localeTag.isNotBlank()) { "A locale tag is required for album sorting." }
    }
}
