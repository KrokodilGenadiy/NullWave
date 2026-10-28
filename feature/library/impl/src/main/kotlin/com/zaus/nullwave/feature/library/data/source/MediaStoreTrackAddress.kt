package com.zaus.nullwave.feature.library.data.source

import com.zaus.nullwave.core.media.model.track.TrackId

/** Source-owned decoding of catalog IDs; core treats TrackId as opaque. */
internal data class MediaStoreTrackAddress(val volume: String, val id: Long) {
    companion object {
        private val volumePattern = Regex("[A-Za-z0-9_-]+")

        fun from(trackId: TrackId): MediaStoreTrackAddress? {
            val separator = trackId.value.indexOf(':')
            if (separator <= 0) return null
            val volume = trackId.value.substring(0, separator)
            if (!volumePattern.matches(volume) || volume == "external" || volume == "internal") return null
            val rawId = trackId.value.substring(separator + 1)
            val id = rawId.toLongOrNull()?.takeIf { it >= 0 } ?: return null
            if (rawId != id.toString()) return null
            return MediaStoreTrackAddress(volume, id)
        }
    }
}
