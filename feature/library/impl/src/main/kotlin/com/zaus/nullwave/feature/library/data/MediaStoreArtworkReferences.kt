package com.zaus.nullwave.feature.library.data

import com.zaus.nullwave.feature.library.api.model.ArtworkRef
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
        val digest = MessageDigest.getInstance("SHA-256")
        // Provider order and UI sorting must not invalidate an unchanged album thumbnail.
        rows.sortedBy { it.id }.forEach { row ->
            context.ensureActive()
            digest.update("${row.id}:${row.dateModifiedSeconds}:${row.sizeBytes};".toByteArray(Charsets.UTF_8))
        }
        val revision = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        key to ArtworkRef(key, revision)
    }.toMap()
}

internal fun MediaStoreAudioRow.artworkKey(): String? =
    if (!volume.isNullOrBlank() && albumId != null && albumId >= 0) "$volume:$albumId" else null
