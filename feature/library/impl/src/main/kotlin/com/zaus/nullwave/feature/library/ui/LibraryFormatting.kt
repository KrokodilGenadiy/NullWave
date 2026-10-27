package com.zaus.nullwave.feature.library.ui

import com.zaus.nullwave.core.media.model.track.MediaTrack

/** Unknown or unrepresentable totals stay unknown. A successful empty catalog is exactly zero. */
internal fun libraryTotalBytes(tracks: List<MediaTrack>): Long? {
    var total = 0L
    for (track in tracks) {
        val size = track.sizeBytes ?: return null
        if (Long.MAX_VALUE - total < size) return null
        total += size
    }
    return total
}
