package com.zaus.nullwave.feature.library.api.model.search

import com.zaus.nullwave.feature.library.api.model.track.LibraryTrack

/**
 * Existing track metadata plus the original, cleaned file display name, if known. A filename
 * can explain a match even when [track] has a different title. It is not a path or playback URI.
 * Unlike search comparison text, its spelling, case and internal whitespace are preserved.
 */
data class LibraryTrackSearchHit(
    val track: LibraryTrack,
    val fileName: String?,
) {
    init {
        require(fileName == null || fileName.isKnownSearchText()) {
            "Unknown file name must be null; known file names must be nonblank and trimmed."
        }
        require(fileName == null || '/' !in fileName) { "File name must not contain a path." }
    }
}
