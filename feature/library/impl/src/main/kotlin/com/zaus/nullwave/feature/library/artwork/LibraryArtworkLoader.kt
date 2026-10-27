package com.zaus.nullwave.feature.library.artwork

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.zaus.nullwave.core.media.model.ArtworkRef

/** App-scoped image service. Coil and Android URI types stay inside the Library implementation. */
class LibraryArtworkLoader internal constructor(internal val imageLoader: ImageLoader)

internal fun ArtworkRef.mediaStoreUri(): Uri? {
    val volume = key.substringBeforeLast(':', "")
    val albumId = key.substringAfterLast(':').toLongOrNull()
    if (volume.isBlank() || albumId == null || albumId < 0) return null
    return ContentUris.withAppendedId(MediaStore.Audio.Albums.getContentUri(volume), albumId)
}

internal fun artworkRequest(context: Context, artwork: ArtworkRef, sizePx: Int): ImageRequest? {
    val uri = artwork.mediaStoreUri() ?: return null
    return ImageRequest.Builder(context)
        .data(uri)
        .size(sizePx)
        .memoryCacheKey("library-artwork:${artwork.key}:${artwork.revision}")
        .build()
}
