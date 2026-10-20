package com.zaus.nullwave.feature.library.ui

import com.zaus.nullwave.feature.library.api.model.track.LibraryTrack
import java.util.Locale

internal fun librarySubtitle(artist: String?, album: String?, unknownArtist: String): String =
    listOfNotNull(artist, album).joinToString(" · ").ifEmpty { unknownArtist }

/** First letters of up to two words, or two code points from a single word (never split a surrogate). */
internal fun libraryMonogram(text: String): String {
    val words = text.trim().split(Regex("\\s+"), limit = 3).filter(String::isNotEmpty)
    val letters = if (words.size > 1) words.take(2).joinToString("") {
        it.substring(0, it.offsetByCodePoints(0, 1))
    } else words.firstOrNull()?.let { it.substring(0, it.offsetByCodePoints(0, minOf(2, it.codePointCount(0, it.length)))) }.orEmpty()
    return letters.uppercase(Locale.ROOT)
}

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

/** Formatting only: unknown is omitted, known zero is 0:00, hours never wrap at 24. */
internal fun libraryDuration(durationMs: Long?): String? = durationMs?.let {
    require(it >= 0)
    val seconds = it / 1_000
    val minutes = seconds / 60
    val tail = (seconds % 60).toString().padStart(2, '0')
    if (minutes < 60) "$minutes:$tail"
    else "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$tail"
}
