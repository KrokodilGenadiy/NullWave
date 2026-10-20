package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.feature.library.api.model.ArtworkRef
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Album thumbnails are shared. A change to any member invalidates every row's album cache key. */
internal suspend fun List<MediaStoreAudioRow>.artworkReferences(): Map<String, ArtworkRef> {
    val context = currentCoroutineContext()
    return groupBy { row ->
        context.ensureActive()
        row.artworkKey()
    }.mapNotNull { (key, rows) ->
        if (key == null) return@mapNotNull null
        key to rows.albumArtwork(key)
    }.toMap()
}

/** Reuses an already grouped album when building Albums; Songs groups only for its artwork map. */
internal suspend fun List<MediaStoreAudioRow>.albumArtwork(key: String): ArtworkRef {
    val context = currentCoroutineContext()
    val digest = MessageDigest.getInstance("SHA-256")
    // Provider order and UI sorting must not invalidate an unchanged album thumbnail.
    sortedWith { first, second ->
        context.ensureActive()
        first.id.compareTo(second.id)
    }.forEach { row ->
        context.ensureActive()
        digest.update("${row.id}:${row.dateModifiedSeconds}:${row.sizeBytes};".toByteArray(Charsets.UTF_8))
    }
    val revision = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    return ArtworkRef(key, revision)
}

internal fun MediaStoreAudioRow.artworkKey(): String? =
    if (!volume.isNullOrBlank() && albumId != null && albumId >= 0) "$volume:$albumId" else null
