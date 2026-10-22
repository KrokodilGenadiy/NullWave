package com.zaus.nullwave.feature.library.ui

import com.zaus.nullwave.feature.library.api.model.track.LibraryTrack

/** Unknown or unrepresentable totals stay unknown. A successful empty catalog is exactly zero. */
internal fun libraryTotalBytes(tracks: List<LibraryTrack>): Long? {
    var total = 0L
    for (track in tracks) {
        val size = track.sizeBytes ?: return null
        if (Long.MAX_VALUE - total < size) return null
        total += size
    }
    return total
}
